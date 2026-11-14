package com.animehub.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.FilterGroup
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.domain.model.FilterTranslator
import com.animehub.tv.domain.model.QuickFilter
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.ui.AdminMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import com.animehub.tv.util.unifyCards
import java.util.Calendar
import com.animehub.tv.voice.VoiceEngine
import javax.inject.Inject

data class SiteChip(val id: String?, val name: String)

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val results: List<AnimeCard> = emptyList(),
    /** Avisos discretos de sitios que fallaron o no pudieron aplicar un filtro. */
    val notices: List<String> = emptyList(),
    val searched: Boolean = false,
    /** Cambia en cada búsqueda nueva (para mover el foco al primer resultado). */
    val version: Int = 0,
    val siteNames: Map<String, String> = emptyMap(),
    val noProviders: Boolean = false,
    /** Sitios para elegir (null = todos los sitios). */
    val sites: List<SiteChip> = emptyList(),
    val selectedSite: String? = null,
    /** Filtros mostrados: los reales del sitio elegido o los comunes en "Todos". */
    val filterGroups: List<FilterGroup> = emptyList(),
    val filtersLoading: Boolean = false,
    val filters: FilterSelection = FilterSelection.NONE,
    val quickFilters: List<QuickFilter> = emptyList(),
) {
    val canLoadMore: Boolean get() = !loading && !loadingMore
    val filterSummary: String get() = FilterTranslator.summary(filters, filterGroups)
}

/**
 * Búsqueda: todos los sitios activos a la vez (con tiempo límite por sitio) o uno solo,
 * con los filtros reales del sitio o los comunes traducidos a cada sitio.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val registry: ProviderRegistry,
    private val errorLog: ErrorLogRepository,
    private val settings: SettingsRepository,
    val admin: AdminMode,
    /** Reconocimiento de voz dentro de la app. */
    val voice: VoiceEngine,
) : ViewModel() {

    val recentSearches: StateFlow<List<String>> =
        library.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Página siguiente pendiente por sitio (solo los que tienen más resultados). */
    private val nextPages = mutableMapOf<String, Int>()
    private val lastSelections = mutableMapOf<String, FilterSelection>()
    private var providers: List<SiteProvider> = emptyList()
    private val filtersCache = mutableMapOf<String, List<FilterGroup>>()
    private var searchJob: Job? = null
    private var filtersJob: Job? = null
    private var exploreStarted = false
    /** Termina cuando se conocen los sitios y empezó la carga de sus filtros. */
    private val ready: Job

    init {
        ready = viewModelScope.launch {
            providers = registry.activeProviders.first()
            // Empieza en el sitio que se está viendo en Inicio (si se eligió uno).
            val homeSite = settings.current().homeSite?.takeIf { id -> admin.enabled.value && providers.any { it.id == id } }
            _state.update {
                it.copy(
                    sites = listOf(SiteChip(null, "Todos los sitios")) + providers.map { p -> SiteChip(p.id, p.name) },
                    siteNames = providers.associate { p -> p.id to p.name },
                    noProviders = providers.isEmpty(),
                    selectedSite = homeSite,
                )
            }
            loadFilters()
        }
    }

    fun selectSite(siteId: String?) {
        if (siteId == _state.value.selectedSite) return
        _state.update { it.copy(selectedSite = siteId, filters = FilterSelection.NONE, filterGroups = emptyList(), quickFilters = emptyList()) }
        loadFilters()
        if (_state.value.searched && _state.value.query.isNotEmpty()) search(_state.value.query)
    }

    fun toggleFilter(group: FilterGroup, value: String) = _state.update { it.copy(filters = it.filters.toggle(group, value)) }

    fun setFilters(selection: FilterSelection) = _state.update { it.copy(filters = selection) }

    fun clearFilters() = _state.update { it.copy(filters = FilterSelection.NONE) }

    /** Elige un valor de un grupo (o ninguno, con null) reemplazando el anterior. */
    fun setGroupValue(groupId: String, value: String?) = _state.update {
        val values = it.filters.values.toMutableMap()
        if (value == null) values.remove(groupId) else values[groupId] = setOf(value)
        it.copy(filters = FilterSelection(values))
    }

    /** Ejecuta la búsqueda con el texto actual y los filtros elegidos (texto vacío = solo filtros). */
    fun search(query: String) {
        val clean = query.trim()
        if (clean.isEmpty() && _state.value.filters.isEmpty) return
        run(clean)
    }

    /** Explorar: el catálogo con los filtros elegidos (sin filtros = todo el catálogo). */
    fun browse() = run("")

    /**
     * Abre Explorar una sola vez: espera los filtros del sitio, marca el género pedido desde Inicio
     * (valor común, p. ej. "accion") y muestra el catálogo.
     */
    fun startExplore(genre: String?) {
        if (exploreStarted) return
        exploreStarted = true
        viewModelScope.launch {
            ready.join()
            filtersJob?.join()
            if (genre != null) {
                val group = _state.value.filterGroups.firstOrNull { it.id == GENRE }
                val option = group?.options?.firstOrNull { FilterTranslator.canonicalOf(it) == genre }
                if (option != null) setGroupValue(GENRE, option.value)
            }
            browse()
        }
    }

    private fun run(clean: String) {
        val filters = _state.value.filters
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (clean.isNotEmpty()) library.addSearch(clean)
            if (providers.isEmpty()) providers = registry.activeProviders.first()
            val targets = providers.filter { _state.value.selectedSite == null || it.id == _state.value.selectedSite }
            nextPages.clear()
            lastSelections.clear()
            _state.update { it.copy(query = clean, loading = true, searched = true, version = it.version + 1, results = emptyList(), notices = emptyList()) }

            val notices = mutableListOf<String>()
            val requests = targets.mapNotNull { provider ->
                val selection = selectionFor(provider, filters, notices) ?: return@mapNotNull null
                lastSelections[provider.id] = selection
                provider to (1 to selection)
            }.toMap()
            val pages = fetch(requests, clean)
            // Unir títulos se hace fuera del hilo principal (no congela la pantalla).
            val unified = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { unifyCards(interleave(pages.values.map { r -> r.first })) }
            _state.update {
                it.copy(loading = false, results = unified, notices = notices + pages.values.flatMap { r -> r.second })
            }
        }
    }

    /** Pide la página siguiente a los sitios que tienen más resultados (desplazamiento infinito). */
    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore || nextPages.isEmpty()) return
        val pending = providers.filter { it.id in nextPages }
            .associateWith { nextPages.getValue(it.id) to (lastSelections[it.id] ?: FilterSelection.NONE) }
        nextPages.clear()
        _state.update { it.copy(loadingMore = true) }
        searchJob = viewModelScope.launch {
            val pages = fetch(pending, current.query)
            val before = _state.value.results
            val known = before.map { it.siteId + it.url }.toSet()
            val more = interleave(pages.values.map { it.first }).filter { (it.siteId + it.url) !in known }
            // Lo nuevo se une con lo que ya está (el mismo anime de otro sitio no se repite), fuera del hilo principal.
            val merged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { unifyCards(before + more) }
            _state.update { s ->
                s.copy(loadingMore = false, results = merged, notices = (s.notices + pages.values.flatMap { it.second }).distinct())
            }
        }
    }

    fun clearRecent() = viewModelScope.launch { library.clearSearches() }

    /** En "Todos" traduce los filtros comunes; si el sitio no puede aplicarlos se omite con un aviso. */
    private suspend fun selectionFor(provider: SiteProvider, filters: FilterSelection, notices: MutableList<String>): FilterSelection? {
        if (filters.isEmpty || _state.value.selectedSite != null) return filters
        val translated = FilterTranslator.toSite(filters, groupsOf(provider) ?: emptyList())
        if (translated.unsupported.isNotEmpty()) {
            // El filtro que un sitio no tiene se omite sin avisar.
            return null
        }
        return translated.selection
    }

    private fun loadFilters() {
        filtersJob?.cancel()
        filtersJob = viewModelScope.launch {
            _state.update { it.copy(filtersLoading = true) }
            val selected = _state.value.selectedSite
            val groups = if (selected != null) {
                providers.firstOrNull { it.id == selected }?.let { groupsOf(it) }.orEmpty()
            } else {
                val perSite = supervisorScope { providers.map { p -> async { groupsOf(p) } }.awaitAll() }.filterNotNull()
                FilterTranslator.commonGroups(perSite)
            }
            val year = Calendar.getInstance().get(Calendar.YEAR)
            _state.update { it.copy(filterGroups = groups, filtersLoading = false, quickFilters = FilterTranslator.quickFilters(groups, year)) }
        }
    }

    /** Filtros reales de un sitio (con caché); null si el sitio no responde. */
    private suspend fun groupsOf(provider: SiteProvider): List<FilterGroup>? =
        filtersCache[provider.id] ?: runCatching { withTimeout(SITE_TIMEOUT_MS) { provider.availableFilters() } }
            .onFailure { errorLog.logIssue(provider.name, "Filtros", it) }
            .getOrNull()?.also { filtersCache[provider.id] = it }

    /** Devuelve por sitio: resultados y avisos (error o filtro no aplicado). */
    private suspend fun fetch(
        requests: Map<SiteProvider, Pair<Int, FilterSelection>>,
        query: String,
    ): Map<SiteProvider, Pair<List<AnimeCard>, List<String>>> = supervisorScope {
        requests.map { (provider, req) ->
            val (page, selection) = req
            async {
                provider to runCatching {
                    withTimeout(SITE_TIMEOUT_MS) { provider.search(query, selection, page) }
                }.fold(
                    onSuccess = { result ->
                        if (result.hasNextPage) nextPages[provider.id] = page + 1
                        result.items to emptyList<String>()
                    },
                    // Un sitio que falla simplemente no aporta resultados: sin avisos (solo se
                    // guarda en el registro lo que hay que arreglar).
                    onFailure = { e ->
                        errorLog.logIssue(provider.name, "Búsqueda", e)
                        emptyList<AnimeCard>() to emptyList<String>()
                    },
                )
            }
        }.awaitAll().toMap()
    }

    companion object {
        const val SITE_TIMEOUT_MS = 15_000L
        const val GENRE = "genre"

        /** Mezcla los resultados de varios sitios alternándolos (1º de cada sitio, 2º de cada sitio…). */
        fun interleave(lists: List<List<AnimeCard>>): List<AnimeCard> = com.animehub.tv.util.interleave(lists)
    }
}
