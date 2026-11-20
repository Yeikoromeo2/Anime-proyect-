package com.animehub.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.interleave
import com.animehub.tv.util.unifyCards
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class SectionPageState(val loading: Boolean = true, val items: List<AnimeCard> = emptyList())

/** Lo primero del catálogo de una sección (Retro, Pelis, Series) para armar su página del Inicio. */
@HiltViewModel
class SectionPageViewModel @Inject constructor(
    private val registry: ProviderRegistry,
    private val errorLog: ErrorLogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SectionPageState())
    val state: StateFlow<SectionPageState> = _state.asStateFlow()
    private var loaded: String? = null

    fun load(section: String) {
        if (loaded == section) return
        loaded = section
        viewModelScope.launch {
            val providers = registry.activeProvidersOf(section).first()
            val pages = supervisorScope {
                providers.map { p ->
                    async {
                        runCatching { withTimeout(TIMEOUT_MS) { p.search("", FilterSelection.NONE, 1).items } }
                            .onFailure { errorLog.logIssue(p.name, "Inicio de sección", it) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
            }
            val items = withContext(Dispatchers.Default) { unifyCards(interleave(pages)) }
            _state.value = SectionPageState(loading = false, items = items)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}

fun sectionTitle(section: String) = when (section) {
    "retro" -> "Retro"
    "pelis" -> "Pelis"
    "series" -> "Series"
    else -> "Anime"
}

/**
 * Página de una sección en el Inicio: un mosaico con lo primero del catálogo y, debajo, más títulos
 * y la entrada a todo el catálogo (buscador y filtros).
 */
@Composable
fun SectionPage(
    section: String,
    onOpen: (AnimeCard) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    leftFocus: Modifier = Modifier,
    viewModel: SectionPageViewModel = hiltViewModel(key = "seccion-$section"),
) {
    LaunchedEffect(section) { viewModel.load(section) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "titulo") {
            Column {
                Text(sectionTitle(section), style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (state.loading) "Cargando…" else if (state.items.isEmpty()) "Por ahora no hay títulos para mostrar" else "Lo primero del catálogo",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario,
                )
            }
        }
        if (state.items.isNotEmpty()) {
            item(key = "mosaico") { MosaicGrid(state.items, onOpen, Modifier.height(330.dp), firstTile = leftFocus) }
        }
        item(key = "mas") {
            val more = state.items.drop(7)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                item(key = "ver-todo") {
                    Box(Modifier.width(150.dp).height(120.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = onViewAll, modifier = leftFocus) { Text("Ver todo y buscar") }
                    }
                }
                itemsIndexed(more, key = { _, c -> c.siteId + c.url }) { _, c ->
                    Box(Modifier.width(130.dp).height(190.dp)) { MosaicTile(c.title, c.coverUrl, { onOpen(c) }, subtitle = c.mosaicSubtitle()) }
                }
            }
        }
    }
}
