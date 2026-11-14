package com.animehub.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animehub.tv.ui.components.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.domain.model.FilterGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.OutlinedButtonDefaults
import com.animehub.tv.ui.LocalAdmin
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.components.TvTextField
import com.animehub.tv.ui.components.ChoiceDialog
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.SecondaryText
import com.animehub.tv.ui.components.listItemBorder
import com.animehub.tv.ui.components.listItemColors
import com.animehub.tv.ui.components.listItemScale
import com.animehub.tv.ui.focus.InitialFocusState
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Explorar: el catálogo completo de los sitios, como en su web, con un menú de categorías a la
 * izquierda (sitio, género, tipo, estado, año, letra). Cada categoría abre la lista de sus
 * opciones; al elegir una se muestra la cuadrícula de portadas al instante.
 * @param initialGenre género elegido en Inicio (valor común entre sitios); null = todo el catálogo.
 */
@Composable
fun ExploreScreen(
    onOpenAnime: (siteId: String, url: String, title: String, cover: String?) -> Unit,
    initialGenre: String? = null,
    startVoice: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val admin = LocalAdmin.current
    LaunchedEffect(Unit) { viewModel.startExplore(initialGenre) }

    // Búsqueda integrada: lo escrito o dicho se busca de inmediato con los filtros elegidos.
    var text by rememberSaveable { mutableStateOf("") }
    val search: (String) -> Unit = { q -> if (q.isBlank()) viewModel.browse() else viewModel.search(q) }
    val voice = rememberVoiceUi(viewModel.voice) { spoken -> text = spoken; viewModel.search(spoken) }
    var voiceStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startVoice && voice.available && !voiceStarted) {
            voiceStarted = true
            voice.start()
        }
    }

    /** Menú abierto: "sitio" o el id de un grupo de filtros. */
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    // Desde un género de Inicio el foco va directo a las portadas; si no, al menú.
    val resultsFocus = remember(state.version) { InitialFocusState() }
    val focusResults = initialGenre != null && state.version <= 1 || (state.query.isNotEmpty() && state.version > 1)

    // Desplazamiento infinito: al acercarse al final se pide la página siguiente.
    LaunchedEffect(gridState, state.version) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (state.results.isNotEmpty() && last >= gridState.layoutInfo.totalItemsCount - 8) viewModel.loadMore() }
    }

    val groups = state.filterGroups.sortedBy { MENU_ORDER.indexOf(it.id).let { i -> if (i < 0) 99 else i } }
    val siteName = if (admin) state.sites.firstOrNull { it.id == state.selectedSite }?.name ?: "Todos los sitios" else null

    Row(
        modifier = Modifier.fillMaxSize().padding(start = 40.dp, end = 24.dp, top = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        LazyColumn(
            modifier = Modifier.width(260.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 28.dp),
        ) {
            item(key = "titulo") {
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text("Explorar", style = MaterialTheme.typography.headlineMedium)
                    Text("Todo el catálogo, por categoría", style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
                }
            }
            if (admin && state.sites.size > 2) {
                item(key = "sitio") {
                    MenuEntry(
                        title = "Sitio",
                        value = "$siteName  ▾",
                        onClick = { picking = SITE },
                        modifier = if (!focusResults) Modifier.initialFocus() else Modifier,
                    )
                }
            }
            item(key = "todo") {
                MenuEntry(
                    title = "Todo el catálogo",
                    value = if (state.filters.isEmpty) "✓ Sin filtros" else "Quitar los filtros",
                    selected = state.filters.isEmpty,
                    onClick = {
                        viewModel.clearFilters()
                        viewModel.browse()
                    },
                    modifier = if (!focusResults && !(admin && state.sites.size > 2)) Modifier.initialFocus() else Modifier,
                )
            }
            items(groups, key = { it.id }) { group ->
                val chosen = state.filters.valuesOf(group.id).firstOrNull()
                MenuEntry(
                    title = group.label,
                    value = (group.options.firstOrNull { it.value == chosen }?.label ?: "Todos") + "  ▾",
                    selected = chosen != null,
                    onClick = { picking = group.id },
                )
            }
            if (state.filtersLoading) {
                item(key = "cargando-filtros") { Text("Leyendo las categorías del sitio…", color = AppColors.TextoSecundario) }
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "cabecera") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                    // Barra de búsqueda: escribir, o el micrófono (pequeño) que escucha al pulsarlo.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = "Buscar un anime",
                            onDone = { search(text) },
                            pill = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (voice.available) {
                            OutlinedButton(onClick = voice.start, shape = OutlinedButtonDefaults.shape(RoundedCornerShape(28.dp))) { Text("🎤") }
                        }
                    }
                    Text(
                        (if (state.query.isNotEmpty()) "“${state.query}”" else state.filterSummary.ifEmpty { "Todo el catálogo" }) +
                            (siteName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    ExploreStatus(state)
                }
            }
            itemsIndexed(state.results, key = { _, card -> card.siteId + card.url }) { index, card ->
                PosterCard(
                    title = card.title,
                    imageUrl = card.coverUrl,
                    onClick = { onOpenAnime(card.siteId, card.url, card.title, card.coverUrl) },
                    cardModifier = if (index == 0 && focusResults) Modifier.initialFocus(resultsFocus) else Modifier,
                    subtitle = listOfNotNull(card.type?.etiqueta, (card.year ?: com.animehub.tv.util.TitleMatcher.yearTag(card.title))?.toString(), card.language).joinToString(" · ").ifEmpty { null },
                    badge = if (admin && state.selectedSite == null) state.siteNames[card.siteId] else null,
                    width = null,
                )
            }
            if (state.loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "cargando-mas") {
                    Text("Cargando más títulos…", color = AppColors.TextoSecundario)
                }
            }
        }
    }

    when (val menu = picking) {
        null -> Unit
        SITE -> ChoiceDialog(
            title = "Sitio",
            options = state.sites.map { it.name },
            selectedIndex = state.sites.indexOfFirst { it.id == state.selectedSite }.coerceAtLeast(0),
            onChoose = { i ->
                picking = null
                viewModel.selectSite(state.sites[i].id)
                viewModel.browse()
            },
            onDismiss = { picking = null },
        )
        else -> groups.firstOrNull { it.id == menu }?.let { group ->
            GroupDialog(
                group = group,
                chosen = state.filters.valuesOf(group.id).firstOrNull(),
                onChoose = { value ->
                    picking = null
                    viewModel.setGroupValue(group.id, value)
                    viewModel.browse()
                },
                onDismiss = { picking = null },
            )
        }
    }
    voice.panel()
    // Si se cambió de sitio y esa categoría ya no existe, se cierra el menú pendiente.
    LaunchedEffect(groups) {
        if (picking != null && picking != SITE && groups.none { it.id == picking } && !state.filtersLoading) picking = null
    }
}

/** Opciones de una categoría: "Todos" (sin filtro) y las del sitio. */
@Composable
private fun GroupDialog(group: FilterGroup, chosen: String?, onChoose: (String?) -> Unit, onDismiss: () -> Unit) {
    val index = group.options.indexOfFirst { it.value == chosen }
    ChoiceDialog(
        title = group.label,
        options = listOf("Todos") + group.options.map { it.label },
        selectedIndex = index + 1,
        onChoose = { i -> onChoose(if (i == 0) null else group.options[i - 1].value) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun MenuEntry(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(title) },
        supportingContent = { SecondaryText(value) },
        scale = listItemScale(),
        colors = listItemColors(),
        border = listItemBorder(),
    )
}

@Composable
private fun ExploreStatus(state: SearchUiState) {
    when {
        state.noProviders -> Text("No hay sitios activos con proveedor. Revisa Ajustes → Sitios.", color = AppColors.TextoSecundario)
        state.loading || !state.searched -> Text("Cargando el catálogo…", color = AppColors.TextoSecundario)
        state.results.isEmpty() && state.notices.isEmpty() -> Text("No hay títulos con estas categorías.", color = AppColors.TextoSecundario)
        state.results.isNotEmpty() -> Text("${state.results.size} títulos cargados · baja para ver más", color = AppColors.TextoSecundario)
    }
    state.notices.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = AppColors.Error) }
}

private const val SITE = "__sitio"

/** Orden del menú: primero lo que más se usa (género), luego tipo, estado, año y letra. */
private val MENU_ORDER = listOf("genre", "type", "status", "year", "letter")
