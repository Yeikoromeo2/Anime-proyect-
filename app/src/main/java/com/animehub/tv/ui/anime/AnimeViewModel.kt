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
) : ViewModel() {

    private val route: AnimeRoute = savedStateHandle.toRoute()
    private val load = MutableStateFlow(LoadState())

    private data class LoadState(val loading: Boolean = true, val detail: AnimeDetail? = null, val error: String? = null, val siteName: String? = null)

    val state: StateFlow<AnimeUiState> = combine(
        load,
        library.isFavorite(route.siteId, route.url),
        library.progressForAnime(route.siteId, route.url),
    ) { l, fav, progress ->
        AnimeUiState(route, l.loading, l.detail, l.error, fav, progress.associateBy { it.episodeUrl }, l.siteName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnimeUiState(route))

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
