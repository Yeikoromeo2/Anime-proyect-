package com.animehub.tv.ui.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.domain.model.AnimeStatus
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.ui.components.ActionsDialog
import com.animehub.tv.ui.components.DialogAction
import com.animehub.tv.ui.components.ProgressBar
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.focus.rememberInitialFocusState
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format

/**
 * Ficha del anime: portada grande, sinopsis, géneros, estado, Favorito, "Continuar" y la lista
 * de episodios con los vistos marcados. Toda la pantalla es una sola cuadrícula para que el
 * D-pad baje de los botones a los episodios sin saltos.
 */
@Composable
fun AnimeScreen(onBack: () -> Unit, viewModel: AnimeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Episode?>(null) }
    val focus = rememberInitialFocusState()
    val detail = state.detail
    val episodes = detail?.episodes.orEmpty()
    val seasons = episodes.groupBy { it.season }
    val multiSeason = seasons.size > 1
    // Capítulo nuevo elegido en Inicio: se destaca y recibe el foco.
    val highlighted = state.route.episodeUrl?.let { target -> episodes.firstOrNull { sameUrl(it.url, target) } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "cabecera") {
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                AsyncImage(
                    model = detail?.coverUrl ?: state.route.coverUrl,
                    contentDescription = state.route.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 220.dp, height = 320.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.Superficie),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Text(detail?.title?.ifBlank { null } ?: state.route.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                    val meta = listOfNotNull(
                        detail?.type?.etiqueta, detail?.year?.toString(),
                        detail?.status?.takeIf { it != AnimeStatus.DESCONOCIDO }?.etiqueta,
                        state.siteName, detail?.episodes?.size?.takeIf { it > 1 }?.let { "$it episodios" },
                    )
                    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = AppColors.Acento2)
                    if (!detail?.genres.isNullOrEmpty()) Text(detail!!.genres.joinToString(" · "), color = AppColors.TextoSecundario)
                    detail?.synopsis?.let { Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
                    when {
                        state.loading -> Text("Cargando ficha…", color = AppColors.TextoSecundario)
                        state.error != null -> Text(state.error!!, color = AppColors.Error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val last = state.lastWatched
                        val firstEpisode = episodes.firstOrNull()
                        when {
                            state.error != null -> Button(onClick = viewModel::reload, modifier = Modifier.initialFocus(focus)) { Text("Reintentar") }
                            highlighted != null -> Button(onClick = { selected = highlighted }, modifier = Modifier.initialFocus(focus)) {
                                Text("Ver episodio ${Format.episode(highlighted.number)} · nuevo")
                            }
                            last != null -> Button(
                                onClick = { selected = episodes.firstOrNull { it.url == last.episodeUrl } ?: firstEpisode },
                                modifier = Modifier.initialFocus(focus),
                            ) { Text("Continuar Ep. ${Format.episode(last.episodeNumber)} – ${Format.time(last.positionMs)}") }
                            firstEpisode != null -> Button(onClick = { selected = firstEpisode }, modifier = Modifier.initialFocus(focus)) {
                                Text(if (episodes.size == 1) "Ver" else "Ver episodio ${Format.episode(firstEpisode.number)}")
                            }
                        }
                        OutlinedButton(
                            onClick = viewModel::toggleFavorite,
                            modifier = if (state.error == null && !state.loading && episodes.isEmpty()) Modifier.initialFocus(focus) else Modifier,
                        ) { Text(if (state.isFavorite) "★ Quitar de favoritos" else "☆ Añadir a favoritos") }
                    }
                }
            }
        }
        seasons.forEach { (season, list) ->
            if (multiSeason || season != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "temporada-$season") {
                    Text(season?.let { "Temporada $it" } ?: "Episodios", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(list, key = { it.url }) { ep -> EpisodeButton(ep, state.progress[ep.url], isNew = ep == highlighted) { selected = ep } }
        }
    }

    selected?.let { ep ->
        val p = state.progress[ep.url]
        ActionsDialog(
            title = "${detail?.title ?: state.route.title} · Episodio ${Format.episode(ep.number)}",
            message = buildString {
                if (p != null) append("Visto hasta ${Format.time(p.positionMs)}. ")
                append("La elección de servidor y la reproducción llegan en la Fase 4.")
            },
            actions = listOf(DialogAction("Entendido") { selected = null }),
            onDismiss = { selected = null },
        )
    }
}

/** Botón de episodio: número, marca de visto y barra si está a medias. */
@Composable
private fun EpisodeButton(episode: Episode, progress: WatchProgress?, isNew: Boolean = false, onClick: () -> Unit) {
    val watched = progress?.completed == true
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = when {
                isNew -> AppColors.SuperficieFoco
                watched -> AppColors.Superficie.copy(alpha = 0.5f)
                else -> AppColors.Superficie
            },
            contentColor = if (watched) AppColors.TextoSecundario else androidx.compose.ui.graphics.Color.White,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                (if (watched) "✓ " else "") + "Ep. ${Format.episode(episode.number)}",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleSmall,
            )
            if (isNew) {
                Text(
                    "NUEVO",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Acento2,
                    modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (progress != null && !watched) ProgressBar(progress.fraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

/** Compara URLs de episodio sin tener en cuenta la barra final. */
private fun sameUrl(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')
