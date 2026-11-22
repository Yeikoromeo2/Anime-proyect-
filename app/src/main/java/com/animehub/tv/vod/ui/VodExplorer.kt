package com.animehub.tv.vod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.ui.components.Button
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.TvTextField
import com.animehub.tv.ui.home.LocalSidebarLeft
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.vod.VodRepository
import com.animehub.tv.vod.models.MediaItem
import com.animehub.tv.vod.models.MediaType
import com.animehub.tv.vod.models.ProviderId
import com.animehub.tv.vod.models.VodCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodExplorerState(
    /** Fuentes que tienen lo necesario para funcionar. */
    val providers: List<ProviderId> = emptyList(),
    /** null = todas las fuentes juntas. */
    val provider: ProviderId? = null,
    val categories: List<VodCategory> = emptyList(),
    val category: String? = null,
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
    val canLoadMore: Boolean = false,
)

/** Explorador de Películas o Series: filtro por fuente y categoría, búsqueda y paginación. */
@HiltViewModel
class VodExplorerViewModel @Inject constructor(private val repository: VodRepository) : ViewModel() {
    private val _state = MutableStateFlow(VodExplorerState())
    val state: StateFlow<VodExplorerState> = _state.asStateFlow()
    private var type = MediaType.MOVIE
    private var started = false
    private var job: Job? = null

    fun start(type: MediaType) {
        if (started) return
        started = true
        this.type = type
        viewModelScope.launch {
            val providers = repository.available()
            _state.update { it.copy(providers = providers) }
            load(append = false)
        }
    }

    fun selectProvider(provider: ProviderId?) {
        _state.update { it.copy(provider = provider, category = null, categories = emptyList()) }
        if (provider != null) viewModelScope.launch { _state.update { s -> if (s.provider == provider) s.copy(categories = repository.categories(provider, type)) else s } }
        load(append = false)
    }

    fun selectCategory(id: String?) {
        _state.update { it.copy(category = id) }
        load(append = false)
    }

    fun search(query: String) {
        _state.update { it.copy(query = query.trim()) }
        load(append = false)
    }

    fun loadMore() = load(append = true)

    /** Actualización manual: borra la caché y vuelve a pedir todo. */
    fun refresh() {
        repository.refresh()
        _state.update { it.copy(categories = emptyList()) }
        _state.value.provider?.let { selectProvider(it) } ?: load(append = false)
    }

    private fun load(append: Boolean) {
        job?.cancel()
        _state.update { it.copy(loading = true, items = if (append) it.items else emptyList()) }
        job = viewModelScope.launch {
            val s = _state.value
            val targets = if (s.provider != null) listOf(s.provider) else s.providers
            val results = targets.map { p ->
                if (s.query.isNotBlank()) repository.search(p, type, s.query)
                else repository.catalog(p, type, if (targets.size == 1) s.category else null, if (append) s.items.count { it.provider == p } else 0)
            }
            // Con varias fuentes se mezclan de una en una para que se vean todas.
            val merged = buildList {
                val max = results.maxOfOrNull { it.size } ?: 0
                for (i in 0 until max) results.forEach { r -> r.getOrNull(i)?.let(::add) }
            }
            _state.update { cur ->
                val items = (if (append) cur.items + merged else merged).distinctBy { it.provider to it.id }
                cur.copy(items = items, loading = false, canLoadMore = targets.size == 1 && cur.query.isBlank() && merged.size >= MIN_PAGE)
            }
        }
    }

    private companion object {
        const val MIN_PAGE = 20
    }
}

private fun MediaItem.meta(): String? =
    listOfNotNull(year, rating?.let { "★ " + "%.1f".format(java.util.Locale.US, it) }).joinToString(" · ").ifEmpty { null }

/**
 * Pestaña de Películas o Series: fuente (Todo / Comunidad / Servidor), búsqueda, categorías y la cuadrícula de títulos.
 * Abrir un título lleva a su detalle.
 */
@Composable
fun VodExplorer(type: MediaType, onOpen: (provider: String, type: String, id: String) -> Unit, modifier: Modifier = Modifier, viewModel: VodExplorerViewModel = hiltViewModel(key = type.key)) {
    LaunchedEffect(type) { viewModel.start(type) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leftToSidebar = LocalSidebarLeft.current
    var text by remember { mutableStateOf("") }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 8.dp, top = 6.dp, end = 24.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "filtros") {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(type.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 8.dp))
                    if (state.providers.size > 1) {
                        Chip("Todo", state.provider == null, leftToSidebar) { viewModel.selectProvider(null) }
                        state.providers.forEach { p -> Chip(p.label, state.provider == p) { viewModel.selectProvider(p) } }
                    }
                    OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.semantics { contentDescription = "Actualizar catálogo" }) { Text("↻") }
                }
                TvTextField(
                    value = text, onValueChange = { text = it }, placeholder = "Buscar ${type.title.lowercase()}…", pill = true,
                    onDone = { viewModel.search(text) }, modifier = Modifier.fillMaxWidth(0.6f),
                )
                if (state.categories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        item { Chip("Todas", state.category == null) { viewModel.selectCategory(null) } }
                        items(state.categories, key = { it.id }) { c -> Chip(c.name, state.category == c.id) { viewModel.selectCategory(c.id) } }
                    }
                }
                Text(
                    when {
                        state.loading -> "Cargando…"
                        state.providers.isEmpty() -> "Sin fuentes configuradas por ahora"
                        state.items.isEmpty() -> "Sin resultados"
                        else -> "${state.items.size} títulos"
                    },
                    style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario,
                )
            }
        }
        items(state.items, key = { "${it.provider.key}:${it.id}" }) { item ->
            PosterCard(title = item.title, imageUrl = item.poster, subtitle = item.meta(), width = null, onClick = { onOpen(item.provider.key, item.type.key, item.id) })
        }
        if (state.canLoadMore && !state.loading) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "mas") { OutlinedButton(onClick = viewModel::loadMore) { Text("Cargar más") } }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label, maxLines = 1) } else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, maxLines = 1) }
}
