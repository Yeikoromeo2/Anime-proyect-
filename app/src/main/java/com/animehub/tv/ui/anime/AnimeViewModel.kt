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
import com.animehub.tv.util.SameAnimeIndex
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    /** Las otras versiones del mismo anime (otro sitio u otro idioma). */
    private val alternates = SameAnimeIndex.alternatesOf(route.siteId, route.url)

    val state: StateFlow<AnimeUiState> = combine(
        load,
        library.isFavorite(route.siteId, route.url),
        // El progreso de todas las versiones: un episodio visto en otro sitio también cuenta.
        combine((listOf(route.siteId to route.url) + alternates).map { (site, url) -> library.progressForAnime(site, url) }) { lists -> lists.flatMap { it } },
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
            val main = fetch(route.siteId, route.url)
            val others = alternates.map { (site, url) -> async { fetch(site, url) } }.awaitAll().filterNotNull()
            // Si la principal no cargó, se usa otra versión sin avisar de nada.
            val base = main ?: others.firstOrNull()
            if (base == null) {
                load.value = LoadState(loading = false, error = "No pudimos cargar esta ficha por ahora.")
                return@launch
            }
            val rest = if (main != null) others else others.drop(1)
            load.value = LoadState(loading = false, detail = mergeVersions(base, rest))
        }
    }

    /** null si el sitio no responde o no se pudo leer (lo accionable queda en el registro de errores). */
    private suspend fun fetch(siteId: String, url: String): AnimeDetail? {
        val provider = registry.provider(siteId) ?: return null
        return runCatching { provider.animeDetail(url) }
            .onFailure { errorLog.logIssue(provider.name, "Ficha", it) }
            .getOrNull()
    }

    /**
     * Junta los episodios: los de la versión principal y, de las otras, los números que a la
     * principal le faltan (cada uno recuerda de qué sitio se reproduce).
     */
    private fun mergeVersions(base: AnimeDetail, others: List<AnimeDetail>): AnimeDetail {
        if (others.isEmpty()) return base
        // Con varias temporadas en la página principal no se mezcla nada: los números se repiten
        // por temporada y las temporadas de esa página deben quedar tal cual.
        if (base.episodes.mapNotNull { it.season }.distinct().size > 1) return base
        val have = base.episodes.map { it.number }.toMutableSet()
        val bySeason = base.episodes.sortedBy { it.number }
        val extra = others.flatMap { other ->
            other.episodes.filter { have.add(it.number) }.map { e ->
                val season = e.season ?: bySeason.lastOrNull { it.number <= e.number }?.season ?: bySeason.firstOrNull()?.season
                e.copy(season = season, siteId = other.siteId, animeUrl = other.url)
            }
        }
        if (extra.isEmpty()) return base
        return base.copy(
            episodes = (base.episodes + extra).sortedWith(compareBy({ it.season ?: 0 }, { it.number })).let { merged ->
                // El orden original de la principal se conserva si ya venía por temporada y número.
                if (base.episodes.map { it.number } == base.episodes.map { it.number }.sorted()) merged else base.episodes + extra
            },
            synopsis = base.synopsis ?: others.firstNotNullOfOrNull { it.synopsis },
            coverUrl = base.coverUrl ?: others.firstNotNullOfOrNull { it.coverUrl },
            genres = base.genres.ifEmpty { others.firstOrNull { it.genres.isNotEmpty() }?.genres.orEmpty() },
        )
    }

    fun toggleFavorite() = viewModelScope.launch {
        val s = state.value
        val title = s.detail?.title?.takeIf { it.isNotBlank() } ?: route.title
        val cover = s.detail?.coverUrl ?: route.coverUrl
        library.setFavorite(route.siteId, route.url, title, cover, !s.isFavorite)
    }
}
