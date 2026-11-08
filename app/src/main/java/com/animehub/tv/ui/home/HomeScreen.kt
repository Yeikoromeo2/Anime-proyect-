package com.animehub.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.ui.components.ConfirmDialog
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.WideCard
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.focus.rememberInitialFocusState
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onOpenAnime: (siteId: String, url: String, title: String, cover: String?) -> Unit,
    onExit: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onRetryLatest: (String) -> Unit = { viewModel.reloadLatest(it) }
    var confirmExit by remember { mutableStateOf(false) }
    // En Inicio, "Atrás" pide confirmación antes de salir.
    BackHandler { confirmExit = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { HomeHeader(onSearch, onHistory, onSettings) }

        if (state.continueWatching.isNotEmpty()) {
            item {
                ContentRow("Continuar viendo", state.continueWatching, key = { it.siteId + it.episodeUrl }) { p, cardModifier ->
                    PosterCard(
                        title = p.animeTitle,
                        imageUrl = p.coverUrl,
                        cardModifier = cardModifier,
                        subtitle = "Ep. ${Format.episode(p.episodeNumber)} – ${Format.time(p.positionMs)}",
                        badge = state.siteNames[p.siteId],
                        progress = p.fraction,
                        onClick = { onOpenAnime(p.siteId, p.animeUrl, p.animeTitle, p.coverUrl) },
                    )
                }
            }
        }

        if (state.favorites.isNotEmpty()) {
            item {
                ContentRow("Favoritos", state.favorites, key = { it.siteId + it.animeUrl }) { f, cardModifier ->
                    PosterCard(
                        title = f.title,
                        imageUrl = f.coverUrl,
                        cardModifier = cardModifier,
                        badge = state.siteNames[f.siteId],
                        onClick = { onOpenAnime(f.siteId, f.animeUrl, f.title, f.coverUrl) },
                    )
                }
            }
        }

        if (state.activeSites.isEmpty()) {
            item { InfoText("No hay sitios activos. Actívalos en Ajustes.") }
        }
        state.activeSites.forEach { site ->
            item(key = "novedades-${site.id}") {
                val row = state.latest[site.id] ?: LatestRow()
                when {
                    row.items.isNotEmpty() -> ContentRow("Novedades · ${site.name}", row.items, key = { it.episodeUrl }) { ep, cardModifier ->
                        WideCard(
                            title = ep.animeTitle,
                            imageUrl = ep.imageUrl,
                            cardModifier = cardModifier,
                            subtitle = listOfNotNull(ep.episodeNumber?.let { "Episodio ${Format.episode(it)}" }, ep.dateLabel).joinToString(" · "),
                            badge = ep.language,
                            onClick = { onOpenAnime(site.id, ep.animeUrl ?: ep.episodeUrl, ep.animeTitle, ep.imageUrl) },
                        )
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Novedades · ${site.name}", style = MaterialTheme.typography.titleLarge)
                        when {
                            row.noProvider -> InfoText("Este sitio todavía no tiene proveedor; llegará en una próxima fase.")
                            row.loading -> InfoText("Cargando novedades…")
                            row.error != null -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(row.error, color = AppColors.Error)
                                OutlinedButton(onClick = { onRetryLatest(site.id) }) { Text("Reintentar") }
                            }
                            else -> InfoText("No hay novedades.")
                        }
                    }
                }
            }
        }

        if (state.continueWatching.isEmpty() && state.favorites.isEmpty()) {
            item { InfoText("Aquí verás lo que estés viendo y tus favoritos.") }
        }
    }

    if (confirmExit) {
        ConfirmDialog(
            title = "¿Salir de AnimeHub TV?",
            message = "Puedes volver cuando quieras; tu progreso queda guardado.",
            confirmText = "Salir",
            onConfirm = onExit,
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun HomeHeader(onSearch: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    // Foco inicial de la pantalla: botón "Buscar".
    val focus = rememberInitialFocusState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("AnimeHub TV", style = MaterialTheme.typography.headlineMedium)
            Text("Tus sitios de anime en un solo lugar", style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSearch, modifier = Modifier.initialFocus(focus)) { Text("Buscar") }
            OutlinedButton(onClick = onHistory) { Text("Historial") }
            OutlinedButton(onClick = onSettings) { Text("Ajustes") }
        }
    }
}

/**
 * Fila horizontal de tarjetas. Al entrar con el D-pad el foco va a la última tarjeta usada
 * o, la primera vez, a la primera de la fila.
 */
@Composable
private fun <T> ContentRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    itemContent: @Composable (item: T, cardModifier: Modifier) -> Unit,
) {
    val firstCard = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyRow(
            modifier = Modifier.focusRestorer(firstCard),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                itemContent(item, if (index == 0) Modifier.focusRequester(firstCard) else Modifier)
            }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextoSecundario, modifier = Modifier.width(720.dp))
}
