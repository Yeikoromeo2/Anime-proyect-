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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import com.animehub.tv.domain.model.EpisodePager
import com.animehub.tv.ui.components.ChoiceDialog
import com.animehub.tv.ui.components.NumberPadDialog
import com.animehub.tv.ui.focus.InitialFocusState
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
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeScreen(onBack: () -> Unit, viewModel: AnimeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Episode?>(null) }
    val focus = rememberInitialFocusState()
    val detail = state.detail
    val episodes = detail?.episodes.orEmpty()
    // Capítulo nuevo elegido en Inicio: se destaca y recibe el foco.
    val highlighted = state.route.episodeUrl?.let { target -> episodes.firstOrNull { sameUrl(it.url, target) } }

    // Series largas: temporada elegida en un desplegable y tramos de 50 episodios.
    val seasons = remember(episodes) { EpisodePager.seasons(episodes) }
    var seasonIndex by rememberSaveable { mutableIntStateOf(-1) }
    var chunkIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<EpisodePicker?>(null) }
    var goToError by remember { mutableStateOf<String?>(null) }
    // Al cargar: abrir la temporada/tramo del capítulo nuevo o del último visto.
    LaunchedEffect(episodes.size, highlighted, state.lastWatched?.episodeUrl) {
        if (episodes.isEmpty() || seasonIndex >= 0) return@LaunchedEffect
        val section = EpisodePager.locate(episodes, highlighted?.url ?: state.lastWatched?.episodeUrl)
        seasonIndex = section?.let { seasons.indexOf(it.season) }?.takeIf { it >= 0 } ?: 0
        chunkIndex = section?.chunk ?: 0
    }
    val currentSeason = seasons.getOrNull(seasonIndex.coerceAtLeast(0))
    val seasonEpisodes = remember(episodes, currentSeason) { EpisodePager.episodesOf(episodes, currentSeason) }
    val chunks = remember(seasonEpisodes) { EpisodePager.chunks(seasonEpisodes) }
    val visible = chunks.getOrElse(chunkIndex) { chunks.first() }
    val needsBrowser = seasons.size > 1 || seasonEpisodes.size > EpisodePager.CHUNK
    // Ir a un episodio concreto: cambia de temporada/tramo y le da el foco.
    val goTo: (Episode) -> Unit = { ep ->
        val section = EpisodePager.sectionOf(episodes, ep)
        seasonIndex = seasons.indexOf(section.season).coerceAtLeast(0)
        chunkIndex = section.chunk
        focusTarget = ep.url
    }

    // Al elegir temporada, tramo o "Ir al episodio", se desplaza la lista hasta ese episodio.
    val gridState = rememberLazyGridState()
    LaunchedEffect(focusTarget, visible) {
        val target = focusTarget ?: return@LaunchedEffect
        val index = visible.indexOfFirst { sameUrl(it.url, target) }
        if (index < 0) return@LaunchedEffect
        val headerItems = if (needsBrowser || currentSeason != null) 2 else 1
        // Cerca del principio del tramo: que se vean el selector de temporada y los tramos.
        // Más abajo: deja visible la fila anterior para no perder el contexto.
        gridState.scrollToItem(if (index < 12) headerItems - 1 else headerItems + index - 6)
    }

    LazyVerticalGrid(
        state = gridState,
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
        if (needsBrowser) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "navegador") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (seasons.size > 1) {
                            Button(onClick = { picker = EpisodePicker.Season }) {
                                Text("${seasonLabel(currentSeason)} · ${seasonEpisodes.size} episodios  ▾")
                            }
                        } else {
                            Text(seasonLabel(currentSeason), style = MaterialTheme.typography.titleLarge)
                        }
                        OutlinedButton(onClick = { goToError = null; picker = EpisodePicker.GoTo }) { Text("Ir al episodio…") }
                    }
                    if (chunks.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            itemsIndexed(chunks) { i, chunk ->
                                FilterChip(selected = i == chunkIndex, onClick = { chunkIndex = i; focusTarget = chunk.firstOrNull()?.url }) {
                                    Text(EpisodePager.chunkLabel(chunk))
                                }
                            }
                        }
                    }
                }
            }
        } else if (currentSeason != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "temporada") {
                Text(seasonLabel(currentSeason), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
        }
        items(visible, key = { it.url }) { ep ->
            val targetFocus = remember(focusTarget) { InitialFocusState() }
            EpisodeButton(
                ep, state.progress[ep.url], isNew = ep == highlighted,
                modifier = if (focusTarget != null && sameUrl(ep.url, focusTarget!!)) Modifier.initialFocus(targetFocus) else Modifier,
            ) { selected = ep }
        }
    }

    when (picker) {
        EpisodePicker.Season -> ChoiceDialog(
            title = "Elegir temporada",
            options = seasons.map { season -> "${seasonLabel(season)} · ${EpisodePager.episodesOf(episodes, season).size} episodios" },
            selectedIndex = seasonIndex.coerceAtLeast(0),
            onChoose = { i ->
                seasonIndex = i
                chunkIndex = 0
                focusTarget = EpisodePager.episodesOf(episodes, seasons[i]).firstOrNull()?.url
                picker = null
            },
            onDismiss = { picker = null },
        )
        EpisodePicker.GoTo -> NumberPadDialog(
            title = "Ir al episodio",
            message = "Elige el número con el control" + (currentSeason?.let { " (se busca primero en la ${seasonLabel(it)})" } ?: ""),
            error = goToError,
            onSubmit = { value ->
                val ep = value.toFloatOrNull()?.let { EpisodePager.findByNumber(episodes, it, currentSeason) }
                if (ep == null) {
                    goToError = "No hay un episodio $value en esta serie"
                } else {
                    goTo(ep)
                    picker = null
                }
            },
            onDismiss = { picker = null },
        )
        null -> Unit
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

private enum class EpisodePicker { Season, GoTo }

private fun seasonLabel(season: Int?) = season?.let { "Temporada $it" } ?: "Episodios"

/** Botón de episodio: número, marca de visto y barra si está a medias. */
@Composable
private fun EpisodeButton(
    episode: Episode,
    progress: WatchProgress?,
    isNew: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val watched = progress?.completed == true
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.dp),
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
private fun sameUrl(a: String, b: String) = EpisodePager.sameUrl(a, b)
