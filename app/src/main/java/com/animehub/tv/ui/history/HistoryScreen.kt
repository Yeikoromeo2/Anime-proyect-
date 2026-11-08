package com.animehub.tv.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.ui.components.ActionsDialog
import com.animehub.tv.ui.components.ConfirmDialog
import com.animehub.tv.ui.components.DialogAction
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format

@Composable
fun HistoryScreen(
    onOpenAnime: (siteId: String, url: String, title: String, cover: String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<WatchProgress?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val headerFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }

    // Foco inicial: primer elemento del historial o, si está vacío, el botón del encabezado.
    LaunchedEffect(state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        runCatching { if (state.items.isNotEmpty()) firstItemFocus.requestFocus() else headerFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Historial", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { if (state.items.isNotEmpty()) confirmClear = true },
                    modifier = Modifier.focusRequester(headerFocus),
                ) { Text("Borrar todo") }
            }
        }
        if (state.loaded && state.items.isEmpty()) {
            item { Text("Todavía no has visto ningún episodio.", color = AppColors.TextoSecundario) }
        }
        itemsIndexed(state.items, key = { _, it -> it.siteId + it.episodeUrl }) { index, item ->
            ListItem(
                selected = false,
                onClick = { selected = item },
                modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                scale = ListItemDefaults.scale(focusedScale = 1.02f),
                leadingContent = {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.animeTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 48.dp, height = 68.dp).clip(RoundedCornerShape(6.dp)),
                    )
                },
                headlineContent = { Text(item.animeTitle) },
                supportingContent = {
                    val status = if (item.completed) "visto" else "${Format.time(item.positionMs)} de ${Format.time(item.durationMs)}"
                    Text(
                        "Episodio ${Format.episode(item.episodeNumber)} · $status · ${state.siteNames[item.siteId] ?: item.siteId} · ${Format.date(item.updatedAt)}",
                        color = AppColors.TextoSecundario,
                    )
                },
            )
        }
    }

    selected?.let { item ->
        ActionsDialog(
            title = item.animeTitle,
            message = "Episodio ${Format.episode(item.episodeNumber)} · ${Format.time(item.positionMs)}",
            actions = listOf(
                DialogAction("Ver ficha del anime") {
                    selected = null
                    onOpenAnime(item.siteId, item.animeUrl, item.animeTitle, item.coverUrl)
                },
                DialogAction("Borrar del historial") { viewModel.delete(item); selected = null },
            ),
            onDismiss = { selected = null },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Borrar todo el historial",
            message = "Se perderá el minuto guardado de todos los episodios.",
            confirmText = "Borrar todo",
            onConfirm = { viewModel.clearAll(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}
