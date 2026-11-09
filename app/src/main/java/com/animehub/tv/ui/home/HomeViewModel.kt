package com.animehub.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.db.FavoriteEntity
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterOption
import com.animehub.tv.domain.model.FilterTranslator
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.ShowcaseKind
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.util.UserMessages
import com.animehub.tv.util.interleave
import com.animehub.tv.util.oneVersionPerTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Estado de las novedades de un sitio. */
data class LatestRow(
    val loading: Boolean = true,
    val items: List<EpisodeCard> = emptyList(),
    val error: String? = null,
    /** El sitio está activo pero aún no tiene proveedor. */
    val noProvider: Boolean = false,
)

/** Reseña corta de un anime destacado (sale de la portada del sitio o de su ficha). */
data class Review(val synopsis: String?, val genres: List<String> = emptyList())

/** Lo que muestran las filas de Inicio (agrupado para no recomponer todo en cada cambio). */
private data class HomeContent(
    val latest: Map<String, LatestRow> = emptyMap(),
    val showcase: Map<String, List<ShowcaseItem>> = emptyMap(),
    val reviews: Map<String, Review> = emptyMap(),
    val genres: List<FilterOption> = emptyList(),
)

data class HomeUiState(
    val continueWatching: List<WatchProgress> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val activeSites: List<EffectiveSite> = emptyList(),
    val siteNames: Map<String, String> = emptyMap(),
    val latest: Map<String, LatestRow> = emptyMap(),
    /** Destacados de la portada de cada sitio (recomendados y series nuevas), por sitio. */
    val showcase: Map<String, List<ShowcaseItem>> = emptyMap(),
    /** Reseñas cargadas desde la ficha, por sitio + URL. */
    val reviews: Map<String, Review> = emptyMap(),
    /** Géneros comunes de los sitios activos (accesos directos a Explorar). */
    val genres: List<FilterOption> = emptyList(),
) {
    /** "Últimos capítulos": lo publicado por todos los sitios, alternando para que ninguno tape a los demás. */
    val combinedLatest: List<EpisodeCard>
        get() = interleave(activeSites.mapNotNull { latest[it.id]?.items?.takeIf { items -> items.isNotEmpty() } })

    /** Lo que recomiendan o destacan los sitios (una versión por título). */
    val recommended: List<ShowcaseItem> get() = showcaseOf(ShowcaseKind.RECOMENDADO)

    /** Series recién agregadas que no estén ya en "Recomendados". */
    val newSeries: List<ShowcaseItem>
        get() {
            val shown = recommended.map { it.card.siteId + it.card.url }.toSet()
            return showcaseOf(ShowcaseKind.NUEVO).filter { (it.card.siteId + it.card.url) !in shown }
        }

    /** Sitios cuyas novedades fallaron (para el aviso con "Reintentar"). */
    val failedSites: List<Pair<EffectiveSite, String>>
        get() = activeSites.mapNotNull { site -> latest[site.id]?.error?.let { site to it } }

    val loadingLatest: Boolean get() = activeSites.any { latest[it.id]?.loading != false }

    /** Hay algo que avisar sobre las novedades (cargando, sin sitios o algún sitio falló). */
    val hasLatestStatus: Boolean get() = activeSites.isEmpty() || combinedLatest.isEmpty() || failedSites.isNotEmpty()

    /** Reseña de un destacado: la de la portada o la cargada de la ficha. */
    fun reviewOf(item: ShowcaseItem): Review? =
        item.synopsis?.let { Review(it) } ?: reviews[reviewKey(item)]

    private fun showcaseOf(kind: ShowcaseKind): List<ShowcaseItem> = interleave(
        activeSites.map { site -> oneVersionPerTitle(showcase[site.id].orEmpty().filter { it.kind == kind }) { it.card.title } },
    )

    companion object {
        fun reviewKey(item: ShowcaseItem) = item.card.siteId + item.card.url
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    private val config: ConfigRepository,
    private val registry: ProviderRegistry,
    private val errorLog: ErrorLogRepository,
) : ViewModel() {

    private val content = MutableStateFlow(HomeContent())
    private val providers = mutableMapOf<String, SiteProvider>()
    private val requestedReviews = mutableSetOf<String>()
    /** Pocas fichas a la vez: las reseñas se piden solo de las tarjetas que se ven. */
    private val reviewLimiter = Semaphore(REVIEWS_IN_PARALLEL)

    val state: StateFlow<HomeUiState> = combine(library.continueWatching, library.favorites, config.sites, content) { cw, fav, sites, content ->
        HomeUiState(
            continueWatching = cw,
            favorites = fav,
            activeSites = sites.filter { it.enabled },
            siteNames = sites.associate { it.id to it.name },
            latest = content.latest,
            showcase = content.showcase,
            reviews = content.reviews,
            genres = content.genres,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Recarga cuando cambian los sitios activos o sus dominios.
        viewModelScope.launch {
            config.sites
                .map { sites -> sites.filter { it.enabled }.map { it.id to it.baseUrl } }
                .distinctUntilChanged()
                .collect {
                    reloadLatest()
                    loadGenres()
                }
        }
    }

    /** Recarga las novedades y los destacados de un sitio (o de todos). */
    fun reloadLatest(siteId: String? = null) {
        viewModelScope.launch {
            val sites = config.sites.first().filter { it.enabled }
            sites.filter { siteId == null || it.id == siteId }.forEach { site ->
                val provider = registry.create(site)
                if (provider == null) {
                    setLatest(site.id, LatestRow(loading = false, noProvider = true))
                    return@forEach
                }
                providers[site.id] = provider
                setLatest(site.id, LatestRow(loading = true))
                launch {
                    val row = runCatching { withTimeout(SITE_TIMEOUT_MS) { provider.latestEpisodes() } }.fold(
                        onSuccess = { LatestRow(loading = false, items = it) },
                        onFailure = { e ->
                            val message = UserMessages.forError(site.name, e)
                            errorLog.log(site.name, "Novedades: $message")
                            LatestRow(loading = false, error = message)
                        },
                    )
                    setLatest(site.id, row)
                }
                launch {
                    // Usa la misma portada que las novedades (queda en caché): no cuesta otra descarga.
                    val items = runCatching { withTimeout(SITE_TIMEOUT_MS) { provider.showcase() } }
                        .onFailure { errorLog.log(site.name, "Destacados: ${UserMessages.forError(site.name, it)}") }
                        .getOrDefault(emptyList())
                    content.update { it.copy(showcase = it.showcase + (site.id to items)) }
                }
            }
        }
    }

    /** Pide la reseña (sinopsis y géneros) de un destacado cuya portada no la trae. */
    fun requestReview(item: ShowcaseItem) {
        if (item.synopsis != null) return
        val key = HomeUiState.reviewKey(item)
        if (!requestedReviews.add(key)) return
        val provider = providers[item.card.siteId] ?: return
        viewModelScope.launch {
            val detail = runCatching {
                reviewLimiter.withPermit { withTimeout(SITE_TIMEOUT_MS) { provider.animeDetail(item.card.url) } }
            }.getOrNull()
            val review = Review(synopsis = detail?.synopsis?.takeIf { it.isNotBlank() }, genres = detail?.genres.orEmpty().take(3))
            content.update { it.copy(reviews = it.reviews + (key to review)) }
        }
    }

    /** Géneros de los sitios activos para los accesos directos a Explorar (en caché una hora). */
    private fun loadGenres() {
        viewModelScope.launch {
            val active = registry.activeProviders.first()
            val perSite = supervisorScope {
                active.map { p -> async { runCatching { withTimeout(SITE_TIMEOUT_MS) { p.availableFilters() } }.getOrNull() } }.awaitAll()
            }.filterNotNull()
            val genres = FilterTranslator.commonGroups(perSite).firstOrNull { it.id == "genre" }?.options.orEmpty()
            content.update { it.copy(genres = genres) }
        }
    }

    private fun setLatest(siteId: String, row: LatestRow) = content.update { it.copy(latest = it.latest + (siteId to row)) }

    private companion object {
        const val SITE_TIMEOUT_MS = 20_000L
        const val REVIEWS_IN_PARALLEL = 3
    }
}
