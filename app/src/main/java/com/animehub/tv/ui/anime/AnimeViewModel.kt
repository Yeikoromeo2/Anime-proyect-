package com.animehub.tv.ui.anime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.ui.navigation.AnimeRoute
import com.animehub.tv.util.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.animehub.tv.remote.CastTarget
import com.animehub.tv.remote.PlayTarget
import com.animehub.tv.remote.TargetDirectory
import javax.inject.Inject

data class AnimeUiState(
    val route: AnimeRoute,
    val loading: Boolean = true,
    val detail: AnimeDetail? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
    /** Progreso por URL de episodio. */
    val progress: Map<String, WatchProgress> = emptyMap(),
    val siteName: String? = null,
    /** Miniaturas que el sitio da aparte (JKAnime), por número de episodio. */
    val thumbnails: Map<Float, String> = emptyMap(),
) {
    /** Último episodio visto (para "Continuar Ep. X – mm:ss"). */
    val lastWatched: WatchProgress? get() = progress.values.maxByOrNull { it.updatedAt }
}

@HiltViewModel
class AnimeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val registry: ProviderRegistry,
    private val library: LibraryRepository,
    private val errorLog: ErrorLogRepository,
    directory: TargetDirectory,
    private val castTarget: CastTarget,
) : ViewModel() {

    /** Ver en otra pantalla: TVs con AnimeHub, Chromecast y TVs DLNA de la red, y dónde se reproduce ahora. */
    val tvs: StateFlow<List<PlayTarget>> = directory.targets
    val target: StateFlow<PlayTarget?> = castTarget.selected

    fun selectTarget(tv: PlayTarget?) = castTarget.select(tv)

    private val route: AnimeRoute = savedStateHandle.toRoute()
    private val load = MutableStateFlow(LoadState())

    private data class LoadState(val loading: Boolean = true, val detail: AnimeDetail? = null, val error: String? = null, val siteName: String? = null)

    private val thumbnails = MutableStateFlow<Map<Float, String>>(emptyMap())
    private val requestedThumbs = mutableSetOf<Float>()

    val state: StateFlow<AnimeUiState> = combine(
        load,
        library.isFavorite(route.siteId, route.url),
        library.progressForAnime(route.siteId, route.url),
        thumbnails,
    ) { l, fav, progress, thumbs ->
        AnimeUiState(route, l.loading, l.detail, l.error, fav, progress.associateBy { it.episodeUrl }, l.siteName, thumbs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnimeUiState(route))

    /** Pide las miniaturas de los episodios que se ven (si el sitio las da aparte). */
    fun loadThumbnails(episodes: List<com.animehub.tv.domain.model.Episode>) {
        val missing = episodes.filter { it.imageUrl == null && requestedThumbs.add(it.number) }.map { it.number }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val provider = registry.provider(route.siteId) ?: return@launch
            val found = runCatching { provider.episodeThumbnails(route.url, missing) }.getOrDefault(emptyMap())
            if (found.isNotEmpty()) thumbnails.value = thumbnails.value + found
        }
    }

    init {
        reload()
    }

    fun reload() {
        load.value = load.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val provider = registry.provider(route.siteId)
            if (provider == null) {
                load.value = LoadState(loading = false, error = "Este sitio todavía no tiene proveedor.")
                return@launch
            }
            load.value = runCatching { provider.animeDetail(route.url) }.fold(
                onSuccess = { LoadState(loading = false, detail = it, siteName = provider.name) },
                onFailure = { e ->
                    val message = UserMessages.forError(provider.name, e)
                    errorLog.log(provider.name, "Ficha ${route.url}: $message")
                    LoadState(loading = false, error = message, siteName = provider.name)
                },
            )
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val s = state.value
        val title = s.detail?.title?.takeIf { it.isNotBlank() } ?: route.title
        val cover = s.detail?.coverUrl ?: route.coverUrl
        library.setFavorite(route.siteId, route.url, title, cover, !s.isFavorite)
    }
}
