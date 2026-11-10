package com.animehub.tv.ui.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.CardDefaults
import coil3.request.ImageRequest
import com.animehub.tv.ui.components.Badge
import com.animehub.tv.ui.components.Card
import com.animehub.tv.ui.components.focusBorder
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
import com.animehub.tv.ui.components.FilterChip
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
import com.animehub.tv.ui.components.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import com.animehub.tv.ui.components.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.domain.model.AnimeStatus
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.ui.components.ProgressBar
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.focus.rememberInitialFocusState
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.ui.navigation.PlayerRoute
import com.animehub.tv.ui.navigation.RemoteRoute
import com.animehub.tv.remote.DlnaDevice
import com.animehub.tv.remote.PlayTarget
import com.animehub.tv.remote.RemoteTv
import com.animehub.tv.ui.rememberIsTv
import com.animehub.tv.util.Format

/**
 * Ficha del anime: portada grande, sinopsis, géneros, estado, Favorito, "Continuar" y la lista
 * de episodios con los vistos marcados. Toda la pantalla es una sola cuadrícula para que el
 * D-pad baje de los botones a los episodios sin saltos.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeScreen(
    onBack: () -> Unit,
    onPlay: (PlayerRoute) -> Unit,
    onPlayOnTv: (RemoteRoute) -> Unit = {},
    viewModel: AnimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // "Enviar a mi TV" (en la tablet): TVs con AnimeHub en la red y dónde reproducir.
    val tvs by viewModel.tvs.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val isTv = rememberIsTv()
    // Elegir un episodio lo reproduce aquí o, si se eligió una TV, lo envía a la TV.
    val play: (Episode) -> Unit = { ep ->
        val title = state.detail?.title?.ifBlank { null } ?: state.route.title
        val cover = state.detail?.coverUrl ?: state.route.coverUrl
        val tv = target?.takeIf { !isTv && it in tvs }
        if (tv != null) {
            val progress = state.progress[ep.url]?.takeIf { !it.completed }
            onPlayOnTv(
                RemoteRoute(
                    kind = tv.kind,
                    controlUrl = (tv as? DlnaDevice)?.controlUrl,
                    tvId = tv.id, tvName = tv.name,
                    tvHost = (tv as? RemoteTv)?.host.orEmpty(), tvPort = (tv as? RemoteTv)?.port ?: 0,
                    siteId = state.route.siteId, animeUrl = state.route.url, animeTitle = title, coverUrl = cover,
                    episodeUrl = ep.url, episodeNumber = ep.number, startMs = progress?.positionMs ?: 0,
                ),
            )
        } else {
            onPlay(
                PlayerRoute(
                    siteId = state.route.siteId, animeUrl = state.route.url, animeTitle = title, coverUrl = cover,
                    episodeUrl = ep.url, episodeNumber = ep.number,
                ),
            )
        }
    }
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

    // Fondo de la ficha: la portada difuminada; al pasar por un episodio con miniatura, su imagen.
    val cover = detail?.coverUrl ?: state.route.coverUrl
    var backdrop by remember { mutableStateOf<String?>(null) }
    // Todos los sitios con tarjetas de imagen: la miniatura del episodio (Anime Ninja, JKAnime) o,
    // si el sitio no la tiene (Latanime, AnimeFLV), la imagen del anime.
    LaunchedEffect(visible) { viewModel.loadThumbnails(visible) }
    val thumbOf: (Episode) -> String? = { ep -> ep.imageUrl ?: state.thumbnails[ep.number] }
    // Entrada animada de la cabecera (portada desde la izquierda, texto con fundido).
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(Modifier.fillMaxSize().background(AppColors.Fondo)) {
    Backdrop(backdrop ?: cover)
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "cabecera") {
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                AnimatedVisibility(
                    visibleState = entered,
                    enter = fadeIn(tween(450)) + slideInHorizontally(tween(550)) { -it / 3 } + scaleIn(tween(550), initialScale = 0.92f),
                ) {
                    AsyncImage(
                        model = cover,
                        contentDescription = state.route.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 220.dp, height = 320.dp)
                            .shadow(24.dp, RoundedCornerShape(12.dp), ambientColor = AppColors.Acento, spotColor = AppColors.Acento)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Superficie),
                    )
                }
                AnimatedVisibility(
                    visibleState = entered,
                    enter = fadeIn(tween(600, delayMillis = 150)) + slideInVertically(tween(600, delayMillis = 150)) { it / 8 },
                    modifier = Modifier.weight(1f),
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    if (!isTv && tvs.isNotEmpty()) PlayTarget(tvs, target, viewModel::selectTarget)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val last = state.lastWatched
                        val firstEpisode = episodes.firstOrNull()
                        when {
                            state.error != null -> Button(onClick = viewModel::reload, modifier = Modifier.initialFocus(focus)) { Text("Reintentar") }
                            highlighted != null -> Button(onClick = { play(highlighted) }, modifier = Modifier.initialFocus(focus)) {
                                Text("Ver episodio ${Format.episode(highlighted.number)} · nuevo")
                            }
                            last != null -> Button(
                                onClick = { (episodes.firstOrNull { sameUrl(it.url, last.episodeUrl) } ?: firstEpisode)?.let(play) },
                                modifier = Modifier.initialFocus(focus),
                            ) { Text("Continuar Ep. ${Format.episode(last.episodeNumber)} – ${Format.time(last.positionMs)}") }
                            firstEpisode != null -> Button(onClick = { play(firstEpisode) }, modifier = Modifier.initialFocus(focus)) {
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
            val focusModifier = (if (focusTarget != null && sameUrl(ep.url, focusTarget!!)) Modifier.initialFocus(targetFocus) else Modifier)
                // El fondo sigue al episodio enfocado (su miniatura, si el sitio la tiene).
                .onFocusChanged { if (it.isFocused) backdrop = thumbOf(ep) }
            Box(Modifier.animateItem(fadeInSpec = tween(350), placementSpec = null, fadeOutSpec = null)) {
                EpisodeThumbnail(ep, thumbOf(ep) ?: cover, state.progress[ep.url], isNew = ep == highlighted, modifier = focusModifier) { play(ep) }
            }
        }
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

}

private enum class EpisodePicker { Season, GoTo }

/** "Ver en: Este dispositivo · TV de la sala": con una TV elegida, los episodios se envían a la TV. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayTarget(tvs: List<PlayTarget>, target: PlayTarget?, onSelect: (PlayTarget?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Ver en:", color = AppColors.TextoSecundario)
        FilterChip(selected = target == null || target !in tvs, onClick = { onSelect(null) }) { Text("📱 Este dispositivo") }
        tvs.forEach { tv ->
            FilterChip(selected = target == tv, onClick = { onSelect(tv) }) { Text(tv.label) }
        }
    }
}

private fun seasonLabel(season: Int?) = season?.let { "Temporada $it" } ?: "Episodios"

/**
 * Fondo de la ficha: la imagen muy reducida y ampliada (queda difuminada en cualquier Android) y
 * oscurecida para que el texto se lea. Cambia con un fundido al pasar por los episodios.
 */
@Composable
private fun Backdrop(url: String?) {
    val context = LocalContext.current
    Crossfade(targetState = url, animationSpec = tween(700), label = "fondo") { image ->
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                AsyncImage(
                    model = remember(image) { ImageRequest.Builder(context).data(image).size(160).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(18.dp).alpha(0.55f),
                )
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(listOf(AppColors.Fondo.copy(alpha = 0.92f), AppColors.Fondo.copy(alpha = 0.55f), AppColors.Fondo.copy(alpha = 0.75f))),
        ),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, AppColors.Fondo.copy(alpha = 0.9f)), startY = 300f)))
}

/**
 * Episodio como tarjeta de imagen 16:9 (miniatura del episodio o, si el sitio no la tiene, la
 * imagen del anime).
 * - Visto: la tarjeta se oscurece con una capa semitransparente y un sello grande "VISTO".
 * - A medias: sin sello; una barra donde quedó y "Ibas en 12:34".
 */
@Composable
private fun EpisodeThumbnail(
    episode: Episode,
    imageUrl: String?,
    progress: WatchProgress?,
    isNew: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val watched = progress?.completed == true
    val partial = progress?.takeIf { !watched && it.positionMs > 0 }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.07f),
        border = focusBorder(),
        colors = CardDefaults.colors(containerColor = AppColors.Superficie),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 60f)))
            if (watched) {
                // Visto: lámina negra semitransparente sobre toda la tarjeta y un sello grande.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                Text(
                    "✓  VISTO",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            Text(
                "Ep. ${Format.episode(episode.number)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (watched) Color.White.copy(alpha = 0.75f) else Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = if (partial != null) 14.dp else 8.dp),
            )
            if (isNew) Badge("NUEVO", Modifier.align(Alignment.TopEnd))
            if (partial != null) {
                Text(
                    "Ibas en ${Format.time(partial.positionMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Acento2,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 14.dp),
                )
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(partial.fraction.coerceIn(0.02f, 1f)).height(6.dp).background(AppColors.Acento2))
                }
            }
        }
    }
}

/** Compara URLs de episodio sin tener en cuenta la barra final. */
private fun sameUrl(a: String, b: String) = EpisodePager.sameUrl(a, b)
