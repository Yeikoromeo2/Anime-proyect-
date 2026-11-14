package com.animehub.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import coil3.compose.AsyncImage
import com.animehub.tv.R
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.ui.components.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animehub.tv.ui.components.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.animehub.tv.ui.components.FilterChip
import androidx.tv.material3.MaterialTheme
import com.animehub.tv.ui.components.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.data.update.UpdateState
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.WatchProgress
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.animehub.tv.ui.LocalAdmin
import com.animehub.tv.ui.components.ConfirmDialog
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.ReviewCard
import com.animehub.tv.ui.update.UpdateDialog
import com.animehub.tv.ui.update.headerLabel
import com.animehub.tv.ui.update.showsInHeader
import com.animehub.tv.ui.components.WideCard
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.focus.rememberInitialFocusState
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format
import com.animehub.tv.voice.VoiceSearch

@Composable
fun HomeScreen(
    onExplore: (genre: String?) -> Unit,
    onVoiceSearch: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onOpenAnime: (siteId: String, url: String, title: String, cover: String?) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onOpenEpisode: (siteId: String, animeUrl: String, episodeUrl: String, title: String, image: String?) -> Unit,
    onExit: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val admin = LocalAdmin.current
    // Sin modo administrador no se ve de qué sitio viene nada.
    val names = if (admin) state.siteNames else emptyMap()
    val update by viewModel.updates.state.collectAsStateWithLifecycle()
    var updateDialog by remember { mutableStateOf(false) }
    val onRetryLatest: (String) -> Unit = { viewModel.reloadLatest(it) }
    var confirmExit by remember { mutableStateOf(false) }
    // En Inicio, "Atrás" pide confirmación antes de salir.
    BackHandler { confirmExit = true }

    // Foco: primero "Buscar" (al instante); cuando llegan los capítulos nuevos, el foco pasa al
    // más reciente si el usuario aún no se movió. Solo la primera vez (al volver se queda en Buscar).
    var searchHasFocus by remember { mutableStateOf(false) }
    var movedToLatest by rememberSaveable { mutableStateOf(false) }
    val latestFocus = rememberInitialFocusState()
    val openEpisode: (EpisodeCard) -> Unit = { ep ->
        onOpenEpisode(ep.siteId, ep.animeUrl ?: ep.episodeUrl, ep.episodeUrl, ep.animeTitle, ep.imageUrl)
    }

    // Animación de entrada (una vez por apertura): después el perfil aparece subiendo con un fundido.
    var showIntro by remember { mutableStateOf(Intro.enabled && !Intro.shown) }
    LaunchedEffect(showIntro) { if (showIntro) Intro.shown = true }
    val reveal by animateFloatAsState(if (showIntro) 0f else 1f, tween(650), label = "entrada")
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // Cualquier tecla salta la animación (y no actúa sobre lo que hay debajo).
                if (showIntro && event.type == KeyEventType.KeyDown) { showIntro = false; true } else false
            },
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = if (reveal >= 1f) 1f else reveal
            translationY = (1f - reveal) * 48f
        },
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            HomeHeader({ onExplore(null) }, onVoiceSearch, onHistory, onSettings, onSearchFocus = { searchHasFocus = it }) {
                // Versión nueva en Releases: botón al final de la cabecera.
                if (update.showsInHeader()) Button(onClick = { updateDialog = true }) { Text(update.headerLabel(), maxLines = 1, softWrap = false) }
            }
        }

        // Qué sitio se está viendo: todos mezclados o uno solo (se recuerda).
        if (admin && state.siteChoices.size > 1) {
            item(key = "elegir-sitio") { SiteChooser(state, viewModel::selectSite) }
        }

        // 1) Lo que se acaba de publicar en todos los sitios, arriba y de frente (una sola fila:
        //    antes había además una fila por sitio que repetía los mismos capítulos).
        val combined = state.combinedLatest
        if (combined.isNotEmpty()) {
            item(key = "ultimos-capitulos") {
                ContentRow(
                    title = "Últimos capítulos",
                    subtitle = "Lo que se acaba de publicar" + state.currentSite?.name?.let { " en $it" }.orEmpty(),
                    items = combined,
                    key = { it.siteId + it.episodeUrl },
                    firstItemModifier = if (!movedToLatest && searchHasFocus) Modifier.initialFocus(latestFocus) else Modifier,
                    onFirstItemFocused = { movedToLatest = true },
                ) { ep, cardModifier ->
                    WideCard(
                        title = ep.animeTitle,
                        imageUrl = ep.imageUrl,
                        cardModifier = cardModifier,
                        width = 300.dp,
                        subtitle = listOfNotNull(
                            names[ep.siteId],
                            ep.episodeNumber?.let { "Episodio ${Format.episode(it)}" },
                            ep.dateLabel,
                        ).joinToString(" · "),
                        badge = ep.language,
                        onClick = { openEpisode(ep) },
                    )
                }
            }
        }
        if (state.hasLatestStatus) {
            item(key = "estado-novedades") { LatestStatus(state, onRetryLatest) }
        }

        // 2) Lo tuyo: lo que estás viendo (sigue con un clic) y tus favoritos.
        if (state.continueWatching.isNotEmpty()) {
            item(key = "continuar") {
                ContentRow("Continuar viendo", state.continueWatching, key = { it.siteId + it.episodeUrl }) { p, cardModifier ->
                    PosterCard(
                        title = p.animeTitle,
                        imageUrl = p.coverUrl,
                        cardModifier = cardModifier,
                        subtitle = "Ep. ${Format.episode(p.episodeNumber)} – ${Format.time(p.positionMs)}",
                        badge = names[p.siteId],
                        progress = p.fraction,
                        // Continuar viendo: reproduce directo desde el minuto guardado.
                        onClick = { onResume(p) },
                    )
                }
            }
        }

        if (state.favorites.isNotEmpty()) {
            item(key = "favoritos") {
                ContentRow("Favoritos", state.favorites, key = { it.siteId + it.animeUrl }) { f, cardModifier ->
                    PosterCard(
                        title = f.title,
                        imageUrl = f.coverUrl,
                        cardModifier = cardModifier,
                        badge = names[f.siteId],
                        onClick = { onOpenAnime(f.siteId, f.animeUrl, f.title, f.coverUrl) },
                    )
                }
            }
        }

        if (state.continueWatching.isEmpty() && state.favorites.isEmpty()) {
            item { InfoText("Aquí verás lo que estés viendo y tus favoritos.") }
        }

        // 3) Lo que recomiendan los sitios y las series recién agregadas, con una reseña corta.
        if (state.recommended.isNotEmpty()) {
            item(key = "recomendados") {
                ContentRow(
                    title = "Recomendados",
                    subtitle = "Lo que destaca hoy, con una pequeña reseña",
                    items = state.recommended,
                    key = { it.card.siteId + it.card.url },
                ) { item, cardModifier -> ShowcaseCard(item, state, cardModifier, viewModel::requestReview, onOpenAnime) }
            }
        }
        if (state.newSeries.isNotEmpty()) {
            item(key = "series-nuevas") {
                ContentRow(
                    title = "Series nuevas",
                    subtitle = "Estrenos recién agregados",
                    items = state.newSeries,
                    key = { it.card.siteId + it.card.url },
                ) { item, cardModifier -> ShowcaseCard(item, state, cardModifier, viewModel::requestReview, onOpenAnime) }
            }
        }

        // 4) Todo el catálogo por categoría.
        if (state.genres.isNotEmpty()) {
            item(key = "explorar-generos") {
                val entries = listOf(GenreEntry("Todo el catálogo", null)) + state.genres.map { GenreEntry(it.label, it.value) }
                ContentRow(
                    title = "Explorar por género",
                    subtitle = "Todo el catálogo, por categoría",
                    items = entries,
                    key = { it.canonical ?: "todo" },
                ) { entry, cardModifier ->
                    OutlinedButton(onClick = { onExplore(entry.canonical) }, modifier = cardModifier) { Text(entry.label) }
                }
            }
        }
    }
    if (showIntro) IntroSplash { showIntro = false }
    }

    if (updateDialog && update != UpdateState.Idle) {
        UpdateDialog(update, viewModel.updates.installedVersion, viewModel.updates) { updateDialog = false }
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
private fun HomeHeader(
    onExplore: () -> Unit,
    onVoiceSearch: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onSearchFocus: (Boolean) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val voiceAvailable = remember { VoiceSearch.anyAvailable(context) }
    // Foco inicial inmediato: "Buscar" (luego pasa al capítulo más reciente cuando carga).
    val focus = rememberInitialFocusState()
    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Kitsu, la mascota de la app (el mismo dibujo del icono).
            Image(
                painter = painterResource(R.drawable.mascota),
                contentDescription = null,
                modifier = Modifier.padding(end = 14.dp).size(48.dp),
            )
            Column {
                Text("AnimeHub TV", style = MaterialTheme.typography.headlineMedium)
                Text("Tus sitios de anime en un solo lugar", style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
            }
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Explorar incluye la búsqueda; el micrófono (si hay voz) abre Explorar escuchando.
            Button(onClick = onExplore, modifier = Modifier.initialFocus(focus).onFocusChanged { onSearchFocus(it.isFocused) }) { Text("Explorar") }
            if (voiceAvailable) OutlinedButton(onClick = onVoiceSearch) { Text("🎤") }
            OutlinedButton(onClick = onHistory) { Text("Historial") }
            OutlinedButton(onClick = onSettings) { Text("Ajustes") }
            extra()
        }
    }
    // TV y tablet en horizontal: todo en una fila. Pantalla angosta (tablet en vertical): los
    // botones pasan a una segunda fila que se puede desplazar.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                title()
                Spacer(Modifier.weight(1f))
                buttons()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                title()
                Box(Modifier.horizontalScroll(rememberScrollState())) { buttons() }
            }
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
    subtitle: String? = null,
    firstItemModifier: Modifier = Modifier,
    onFirstItemFocused: () -> Unit = {},
    itemContent: @Composable (item: T, cardModifier: Modifier) -> Unit,
) {
    val firstCard = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
        }
        LazyRow(
            modifier = Modifier.focusRestorer(firstCard),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            // Margen para que la tarjeta enfocada (crece un 6-8 %) no se corte en los bordes.
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 20.dp),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                val modifier = if (index == 0) {
                    Modifier.focusRequester(firstCard).then(firstItemModifier).onFocusChanged { if (it.isFocused) onFirstItemFocused() }
                } else Modifier
                itemContent(item, modifier)
            }
        }
    }
}

/** Chips para ver todos los sitios mezclados o solo uno. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SiteChooser(state: HomeUiState, onSelect: (String?) -> Unit) {
    val selected = state.currentSite?.id
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Viendo:", color = AppColors.TextoSecundario, modifier = Modifier.padding(end = 2.dp))
        SiteBubble("Todos los sitios", selected == null, onClick = { onSelect(null) }) {
            Image(painterResource(R.drawable.mascota), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        state.siteChoices.forEach { site ->
            SiteBubble(site.name, selected == site.id, onClick = { onSelect(site.id) }) { SiteLogo(site) }
        }
    }
}

/** Sitio para elegir: su logo redondo y el nombre. El elegido lleva un aro de color. */
@Composable
private fun SiteBubble(name: String, selected: Boolean, onClick: () -> Unit, logo: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(RoundedCornerShape(28.dp)),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Foco), shape = RoundedCornerShape(28.dp))),
        colors = CardDefaults.colors(
            containerColor = if (selected) AppColors.SuperficieFoco.copy(alpha = 0.6f) else AppColors.Superficie,
            contentColor = Color.White,
            focusedContainerColor = AppColors.SuperficieFoco,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 14.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Acento2 else Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(if (selected) 2.dp else 1.dp)
                    .clip(CircleShape),
            ) { logo() }
            Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) AppColors.Acento2 else Color.White)
        }
    }
}

/** Logo del sitio (de su web); mientras carga o si falla, sus iniciales sobre su color. */
@Composable
private fun SiteLogo(site: EffectiveSite) {
    val color = remember(site.config.color) {
        runCatching { Color(android.graphics.Color.parseColor(site.config.color)) }.getOrDefault(AppColors.Acento)
    }
    Box(Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
        Text(initials(site.name), style = MaterialTheme.typography.labelLarge, color = Color.White)
        AsyncImage(
            model = site.config.logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(site.config.logoAlign, 0f),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun initials(name: String) =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .let { if (it.length == 1) name.take(2) else it }

/** Novedades que aún cargan o que fallaron (con "Reintentar"), debajo de "Últimos capítulos". */
@Composable
private fun LatestStatus(state: HomeUiState, onRetry: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.combinedLatest.isEmpty()) Text("Últimos capítulos", style = MaterialTheme.typography.titleLarge)
        val admin = LocalAdmin.current
        when {
            state.activeSites.isEmpty() -> if (admin) InfoText("No hay sitios activos. Actívalos en Ajustes.")
            state.combinedLatest.isEmpty() && state.loadingLatest -> InfoText("Cargando capítulos nuevos…")
            state.combinedLatest.isEmpty() && state.failedSites.isEmpty() -> InfoText("No hay capítulos nuevos.")
        }
        if (admin) {
            state.failedSites.forEach { (site, error) ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${site.name}: $error", color = AppColors.Error)
                    OutlinedButton(onClick = { onRetry(site.id) }) { Text("Reintentar") }
                }
            }
        } else if (state.combinedLatest.isEmpty() && state.failedSites.isNotEmpty()) {
            // Sin detalles técnicos: algo tarda y ya se está reintentando solo.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Estamos preparando las novedades…", color = AppColors.TextoSecundario)
                OutlinedButton(onClick = { state.failedSites.forEach { onRetry(it.first.id) } }) { Text("Reintentar") }
            }
        }
    }
}

/** Destacado con su reseña; si la portada del sitio no la trae, se pide a la ficha al mostrarse. */
@Composable
private fun ShowcaseCard(
    item: ShowcaseItem,
    state: HomeUiState,
    cardModifier: Modifier,
    onNeedReview: (ShowcaseItem) -> Unit,
    onOpenAnime: (siteId: String, url: String, title: String, cover: String?) -> Unit,
) {
    val review = state.reviewOf(item)
    LaunchedEffect(item.card.siteId + item.card.url) { if (review == null) onNeedReview(item) }
    ReviewCard(
        title = item.card.title,
        imageUrl = item.card.coverUrl,
        synopsis = review?.let { it.synopsis.orEmpty() },
        cardModifier = cardModifier,
        meta = listOfNotNull(
            if (LocalAdmin.current) state.siteNames[item.card.siteId] else null,
            item.dateLabel?.let { "Agregado el ${Format.siteDate(it)}" } ?: item.card.year?.toString(),
            item.rating?.let { "★ $it" },
        ).joinToString(" · "),
        genres = review?.genres?.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        onClick = { onOpenAnime(item.card.siteId, item.card.url, item.card.title, item.card.coverUrl) },
    )
}

/** Acceso directo a Explorar: un género (valor común entre sitios) o todo el catálogo (null). */
private data class GenreEntry(val label: String, val canonical: String?)

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextoSecundario, modifier = Modifier.width(720.dp))
}
