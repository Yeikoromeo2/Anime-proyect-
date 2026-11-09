package com.animehub.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.db.FavoriteEntity
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.util.UserMessages
import com.animehub.tv.util.interleave
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Estado de la fila de novedades de un sitio. */
data class LatestRow(
    val loading: Boolean = true,
    val items: List<EpisodeCard> = emptyList(),
    val error: String? = null,
    /** El sitio está activo pero aún no tiene proveedor. */
    val noProvider: Boolean = false,
)

data class HomeUiState(
    val continueWatching: List<WatchProgress> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val activeSites: List<EffectiveSite> = emptyList(),
    val siteNames: Map<String, String> = emptyMap(),
    val latest: Map<String, LatestRow> = emptyMap(),
) {
    /** "Últimos capítulos": lo publicado por todos los sitios, alternando para que ninguno tape a los demás. */
    val combinedLatest: List<EpisodeCard>
        get() = interleave(activeSites.mapNotNull { latest[it.id]?.items?.takeIf { items -> items.isNotEmpty() } })
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    private val config: ConfigRepository,
    private val registry: ProviderRegistry,
    private val errorLog: ErrorLogRepository,
) : ViewModel() {

    private val latest = MutableStateFlow<Map<String, LatestRow>>(emptyMap())

    val state: StateFlow<HomeUiState> = combine(library.continueWatching, library.favorites, config.sites, latest) { cw, fav, sites, latest ->
        HomeUiState(
            continueWatching = cw,
            favorites = fav,
            activeSites = sites.filter { it.enabled },
            siteNames = sites.associate { it.id to it.name },
            latest = latest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Recarga las novedades cuando cambian los sitios activos o sus dominios.
        viewModelScope.launch {
            config.sites
                .map { sites -> sites.filter { it.enabled }.map { it.id to it.baseUrl } }
                .distinctUntilChanged()
                .collect { reloadLatest() }
        }
    }

    fun reloadLatest(siteId: String? = null) {
        viewModelScope.launch {
            val sites = config.sites.first().filter { it.enabled }
            sites.filter { siteId == null || it.id == siteId }.forEach { site ->
                val provider = registry.create(site)
                if (provider == null) {
                    latest.update { it + (site.id to LatestRow(loading = false, noProvider = true)) }
                    return@forEach
                }
                latest.update { it + (site.id to LatestRow(loading = true)) }
                launch {
                    val row = runCatching { withTimeout(20_000) { provider.latestEpisodes() } }.fold(
                        onSuccess = { LatestRow(loading = false, items = it) },
                        onFailure = { e ->
                            val message = UserMessages.forError(site.name, e)
                            errorLog.log(site.name, "Novedades: $message")
                            LatestRow(loading = false, error = message)
                        },
                    )
                    latest.update { it + (site.id to row) }
                }
            }
        }
    }
}
