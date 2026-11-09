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
import com.animehub.tv.playback.PlaybackController
import com.animehub.tv.playback.ServerRepository
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.ui.navigation.PlayerRoute
import com.animehub.tv.util.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
            // La lista de episodios permite seguir con el siguiente; si la ficha falla, solo este.
            val detail = runCatching { provider.animeDetail(route.animeUrl) }
                .onFailure { errorLog.log(provider.name, "Reproductor: ficha ${route.animeUrl}: ${UserMessages.forError(provider.name, it)}") }
                .getOrNull()
            val single = Episode(route.episodeNumber, "Episodio", route.episodeUrl)
            val episodes = detail?.episodes?.takeIf { it.isNotEmpty() } ?: listOf(single)
            val index = episodes.indexOfFirst { EpisodePager.sameUrl(it.url, route.episodeUrl) }.takeIf { it >= 0 }
            val list = if (index == null) listOf(single) else episodes
            val title = detail?.title?.ifBlank { null } ?: route.animeTitle
            val cover = detail?.coverUrl ?: route.coverUrl
            val config = configRepository.loadLocal().config
            val controller = PlaybackController(
                scope = viewModelScope,
                episodes = list,
                startIndex = index ?: 0,
                loadServers = { ep -> servers.serversFor(provider, ep.url) },
                savedProgress = { ep -> library.progressFor(route.siteId, ep.url) },
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
            controller.start()
        }
    }
}
