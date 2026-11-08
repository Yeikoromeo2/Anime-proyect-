package com.animehub.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.TvTextField
import com.animehub.tv.ui.focus.InitialFocusState
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Búsqueda en todos los sitios activos. Resultados en cuadrícula de portadas con la etiqueta
 * del sitio; al terminar una búsqueda el foco pasa al primer resultado.
 */
@Composable
fun SearchScreen(
    onOpenAnime: (siteId: String, url: String, title: String, cover: String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    // Un estado de foco por búsqueda: cada búsqueda nueva lleva el foco al primer resultado.
    val resultsFocus = remember(state.version) { InitialFocusState() }

    // Desplazamiento infinito: al acercarse al final se piden más resultados.
    LaunchedEffect(gridState, state.version) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (state.results.isNotEmpty() && last >= gridState.layoutInfo.totalItemsCount - 6) viewModel.loadMore() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "cabecera") {
            SearchHeader(
                text = text,
                onTextChange = { text = it },
                onSearch = { viewModel.search(text) },
                recent = if (state.searched) emptyList() else recent,
                onRecent = { text = it; viewModel.search(it) },
                onClearRecent = { viewModel.clearRecent() },
                state = state,
            )
        }
        itemsIndexed(state.results, key = { _, card -> card.siteId + card.url }) { index, card ->
            ResultCard(
                card = card,
                siteName = state.siteNames[card.siteId],
                cardModifier = if (index == 0) Modifier.initialFocus(resultsFocus) else Modifier,
                onClick = { onOpenAnime(card.siteId, card.url, card.title, card.coverUrl) },
            )
        }
        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "cargando-mas") {
                Text("Cargando más resultados…", color = AppColors.TextoSecundario)
            }
        }
    }
}

@Composable
private fun SearchHeader(
    text: String,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    recent: List<String>,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    state: SearchUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Text("Buscar", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = "Nombre del anime",
                onDone = onSearch,
                modifier = Modifier.width(520.dp).initialFocus(),
            )
            Button(onClick = onSearch) { Text("Buscar") }
        }
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Búsquedas recientes", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(recent, key = { it }) { query -> OutlinedButton(onClick = { onRecent(query) }) { Text(query) } }
                    item { OutlinedButton(onClick = onClearRecent) { Text("Borrar recientes") } }
                }
            }
        }
        when {
            state.loading -> Text("Buscando \"${state.query}\"…", color = AppColors.TextoSecundario)
            state.noProviders -> Text("No hay sitios activos con proveedor. Revisa Ajustes → Sitios.", color = AppColors.TextoSecundario)
            state.searched && state.results.isEmpty() && state.notices.isEmpty() ->
                Text("No se encontraron resultados para \"${state.query}\".", color = AppColors.TextoSecundario)
            state.searched && state.results.isNotEmpty() ->
                Text("${state.results.size} resultados para \"${state.query}\"", color = AppColors.TextoSecundario)
        }
        state.notices.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = AppColors.Error) }
    }
}

@Composable
private fun ResultCard(card: AnimeCard, siteName: String?, cardModifier: Modifier, onClick: () -> Unit) {
    val subtitle = listOfNotNull(card.type?.etiqueta, card.year?.toString(), card.language).joinToString(" · ").ifEmpty { null }
    PosterCard(
        title = card.title,
        imageUrl = card.coverUrl,
        onClick = onClick,
        cardModifier = cardModifier,
        subtitle = subtitle,
        badge = siteName,
        width = null,
    )
}
