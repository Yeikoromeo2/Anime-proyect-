package com.animehub.tv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.data.settings.PreferredQuality
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.EpisodePager
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.nativeplayer.NativePlaybackFactory
import com.animehub.tv.playback.NativeFailures
import com.animehub.tv.remote.ActivePlayback
import com.animehub.tv.playback.PlaybackController
import com.animehub.tv.playback.ServerRepository
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.ui.navigation.PlayerRoute
import com.animehub.tv.util.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Datos fijos de la sesión de reproducción. */
data class PlayerSession(
    val controller: PlaybackController,
    val animeTitle: String,
    val siteName: String,
    val referer: String,
    val adHosts: Set<String>,
    val seekSeconds: Int,
    /** Crea el reproductor nativo para los servidores con video directo. */
    val nativeFactory: NativePlaybackFactory,
    val quality: PreferredQuality,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val registry: ProviderRegistry,
    private val servers: ServerRepository,
    private val library: LibraryRepository,
    private val configRepository: ConfigRepository,
    private val settingsRepository: SettingsRepository,
    private val errorLog: ErrorLogRepository,
    private val nativeFactory: NativePlaybackFactory,
    private val nativeFailures: NativeFailures,
    /** "Enviar a mi TV": la tablet maneja el reproductor abierto en la TV. */
    private val active: ActivePlayback,
) : ViewModel() {

    val route: PlayerRoute = savedStateHandle.toRoute()
    private val _session = MutableStateFlow<PlayerSession?>(null)
    val session: StateFlow<PlayerSession?> = _session.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        _error.value = null
        viewModelScope.launch {
            val provider = registry.provider(route.siteId)
            if (provider == null) {
                _error.value = "Este sitio no tiene proveedor"
                return@launch
            }
            // Se empieza ya con este episodio; la lista completa (para anterior/siguiente) llega
            // en paralelo y no retrasa el arranque.
            val single = Episode(route.episodeNumber, "Episodio", route.episodeUrl)
            var title = route.animeTitle
            var cover = route.coverUrl
            val config = configRepository.loadLocal().config
            val controller = PlaybackController(
                scope = viewModelScope,
                episodes = listOf(single),
                startIndex = 0,
                loadServers = { ep ->
                    servers.serversFlow(provider, ep.url)
                        .catch { e -> errorLog.logIssue(provider.name, "Servidores", e); throw e }
                },
                // Enviado desde la tablet con su minuto: se empieza ahí (solo el primer episodio).
                savedProgress = { ep ->
                    if (route.startMs > 0 && EpisodePager.sameUrl(ep.url, route.episodeUrl)) {
                        WatchProgress(
                            siteId = route.siteId, animeUrl = route.animeUrl, animeTitle = title, coverUrl = cover,
                            episodeNumber = ep.number, episodeUrl = ep.url, serverName = null,
                            positionMs = route.startMs, durationMs = 0, updatedAt = System.currentTimeMillis(), completed = false,
                        )
                    } else {
                        library.progressFor(route.siteId, ep.url)
                    }
                },
                saveProgress = { ep, option, pos, dur, completed ->
                    library.saveProgress(
                        WatchProgress(
                            siteId = route.siteId, animeUrl = route.animeUrl, animeTitle = title, coverUrl = cover,
                            episodeNumber = ep.number, episodeUrl = ep.url, serverName = option?.server?.name,
                            positionMs = pos, durationMs = dur, updatedAt = System.currentTimeMillis(), completed = completed,
                        ),
                    )
                },
                rememberPreferred = { name -> servers.rememberPreferred(route.siteId, name) },
                nativeFailures = nativeFailures,
            )
            _session.value = PlayerSession(
                controller = controller,
                animeTitle = title,
                siteName = provider.name,
                referer = provider.baseUrl + "/",
                adHosts = config.adBlockHosts.toSet(),
                seekSeconds = settingsRepository.current().seekSeconds,
                nativeFactory = nativeFactory,
                quality = settingsRepository.current().preferredQuality,
            )
            active.controller = controller
            active.animeTitle = title
            controller.start()
            // La ficha completa: anterior/siguiente y el título definitivo.
            val detail = runCatching { provider.animeDetail(route.animeUrl) }
                .onFailure { errorLog.logIssue(provider.name, "Reproductor: ficha", it) }
                .getOrNull()
            if (detail != null) {
                detail.title.ifBlank { null }?.let { title = it }
                detail.coverUrl?.let { cover = it }
                detail.episodes.takeIf { it.isNotEmpty() }?.let { controller.setEpisodes(it) }
            }
        }
    }

    override fun onCleared() {
        if (active.controller === _session.value?.controller) {
            active.controller = null
            active.animeTitle = null
        }
    }
}
