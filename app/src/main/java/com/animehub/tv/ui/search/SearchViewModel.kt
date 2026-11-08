package com.animehub.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.util.UserMessages
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
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val results: List<AnimeCard> = emptyList(),
    /** Avisos discretos de sitios que fallaron (los demás siguen mostrando resultados). */
    val notices: List<String> = emptyList(),
    val searched: Boolean = false,
    /** Cambia en cada búsqueda nueva (para mover el foco al primer resultado). */
    val version: Int = 0,
    val siteNames: Map<String, String> = emptyMap(),
    val noProviders: Boolean = false,
) {
    val canLoadMore: Boolean get() = !loading && !loadingMore
}

/** Busca en todos los sitios activos a la vez, con un tiempo límite por sitio. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val registry: ProviderRegistry,
    private val errorLog: ErrorLogRepository,
) : ViewModel() {

    val recentSearches: StateFlow<List<String>> =
        library.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Página siguiente pendiente por sitio (solo los que tienen más resultados). */
    private val nextPages = mutableMapOf<String, Int>()
    private var providers: List<SiteProvider> = emptyList()
    private var searchJob: Job? = null

    fun search(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            library.addSearch(clean)
            providers = registry.activeProviders.first()
            nextPages.clear()
            _state.update {
                SearchUiState(
                    query = clean, loading = true, searched = true, version = it.version + 1,
                    siteNames = providers.associate { p -> p.id to p.name }, noProviders = providers.isEmpty(),
                )
            }
            val pages = fetch(providers.associateWith { 1 }, clean)
            _state.update { it.copy(loading = false, results = interleave(pages.values.map { r -> r.first }), notices = pages.notices()) }
        }
    }

    /** Pide la página siguiente a los sitios que tienen más resultados (desplazamiento infinito). */
    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore || nextPages.isEmpty()) return
        val pending = providers.filter { it.id in nextPages }.associateWith { nextPages.getValue(it.id) }
        nextPages.clear()
        _state.update { it.copy(loadingMore = true) }
        searchJob = viewModelScope.launch {
            val pages = fetch(pending, current.query)
            _state.update { s ->
                val known = s.results.map { it.siteId + it.url }.toSet()
                val more = interleave(pages.values.map { it.first }).filter { (it.siteId + it.url) !in known }
                s.copy(loadingMore = false, results = s.results + more, notices = (s.notices + pages.notices()).distinct())
            }
        }
    }

    fun clearRecent() = viewModelScope.launch { library.clearSearches() }

    private suspend fun fetch(requests: Map<SiteProvider, Int>, query: String): Map<SiteProvider, Pair<List<AnimeCard>, String?>> =
        supervisorScope {
            requests.map { (provider, page) ->
                async {
                    provider to runCatching {
                        withTimeout(SITE_TIMEOUT_MS) { provider.search(query, FilterSelection.NONE, page) }
                    }.fold(
                        onSuccess = { result ->
                            if (result.hasNextPage) nextPages[provider.id] = page + 1
                            result.items to null
                        },
                        onFailure = { e ->
                            val message = UserMessages.forError(provider.name, e)
                            errorLog.log(provider.name, "Búsqueda \"$query\": $message")
                            emptyList<AnimeCard>() to message
                        },
                    )
                }
            }.awaitAll().toMap()
        }

    private fun Map<SiteProvider, Pair<List<AnimeCard>, String?>>.notices() = values.mapNotNull { it.second }

    companion object {
        const val SITE_TIMEOUT_MS = 15_000L

        /** Mezcla los resultados de varios sitios alternándolos (1º de cada sitio, 2º de cada sitio…). */
        fun interleave(lists: List<List<AnimeCard>>): List<AnimeCard> {
            val max = lists.maxOfOrNull { it.size } ?: 0
            return (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
        }
    }
}
