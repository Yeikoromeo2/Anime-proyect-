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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.WatchProgress
import com.animehub.tv.ui.components.ConfirmDialog
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.ReviewCard
import com.animehub.tv.ui.components.WideCard
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.focus.rememberInitialFocusState
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format
import com.animehub.tv.voice.VoiceSearch

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { HomeHeader(onSearch, { onExplore(null) }, onVoiceSearch, onHistory, onSettings, onSearchFocus = { searchHasFocus = it }) }

        // Qué sitio se está viendo: todos mezclados o uno solo (se recuerda).
        if (state.siteChoices.size > 1) {
            item(key = "elegir-sitio") { SiteChooser(state, viewModel::selectSite) }
        }

        // 1) Lo que se acaba de publicar en todos los sitios, arriba y de frente (una sola fila:
        //    antes había además una fila por sitio que repetía los mismos capítulos).
        val combined = state.combinedLatest
        if (combined.isNotEmpty()) {
            item(key = "ultimos-capitulos") {
                ContentRow(
                    title = "Últimos capítulos",
                    subtitle = "Lo que se acaba de publicar en " + (state.currentSite?.name ?: "tus sitios"),
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
                            state.siteNames[ep.siteId],
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
                        badge = state.siteNames[p.siteId],
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
                        badge = state.siteNames[f.siteId],
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
                    subtitle = "Lo que destaca " + (state.currentSite?.name ?: "cada sitio") + ", con una pequeña reseña",
                    items = state.recommended,
                    key = { it.card.siteId + it.card.url },
                ) { item, cardModifier -> ShowcaseCard(item, state, cardModifier, viewModel::requestReview, onOpenAnime) }
            }
        }
        if (state.newSeries.isNotEmpty()) {
            item(key = "series-nuevas") {
                ContentRow(
                    title = "Series nuevas",
                    subtitle = "Estrenos recién agregados a " + (state.currentSite?.name ?: "tus sitios"),
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
                    subtitle = "Todo el catálogo de " + (state.currentSite?.name ?: "tus sitios") + ", por categoría",
                    items = entries,
                    key = { it.canonical ?: "todo" },
                ) { entry, cardModifier ->
                    OutlinedButton(onClick = { onExplore(entry.canonical) }, modifier = cardModifier) { Text(entry.label) }
                }
            }
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
private fun HomeHeader(
    onSearch: () -> Unit,
    onExplore: () -> Unit,
    onVoiceSearch: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onSearchFocus: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val voiceAvailable = remember { VoiceSearch.isAvailable(context) }
    // Foco inicial inmediato: "Buscar" (luego pasa al capítulo más reciente cuando carga).
    val focus = rememberInitialFocusState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("AnimeHub TV", style = MaterialTheme.typography.headlineMedium)
            Text("Tus sitios de anime en un solo lugar", style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSearch, modifier = Modifier.initialFocus(focus).onFocusChanged { onSearchFocus(it.isFocused) }) { Text("Buscar") }
            OutlinedButton(onClick = onExplore) { Text("Explorar") }
            // Atajo de voz: solo si el dispositivo tiene reconocimiento de voz.
            if (voiceAvailable) OutlinedButton(onClick = onVoiceSearch) { Text("🎤 Voz") }
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Viendo:", color = AppColors.TextoSecundario, modifier = Modifier.padding(end = 4.dp))
        FilterChip(selected = selected == null, onClick = { onSelect(null) }) { Text("Todos los sitios") }
        state.siteChoices.forEach { site ->
            FilterChip(selected = selected == site.id, onClick = { onSelect(site.id) }) { Text(site.name) }
        }
    }
}

/** Novedades que aún cargan o que fallaron (con "Reintentar"), debajo de "Últimos capítulos". */
@Composable
private fun LatestStatus(state: HomeUiState, onRetry: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.combinedLatest.isEmpty()) Text("Últimos capítulos", style = MaterialTheme.typography.titleLarge)
        when {
            state.activeSites.isEmpty() -> InfoText("No hay sitios activos. Actívalos en Ajustes.")
            state.combinedLatest.isEmpty() && state.loadingLatest -> InfoText("Cargando capítulos nuevos…")
            state.combinedLatest.isEmpty() && state.failedSites.isEmpty() -> InfoText("No hay capítulos nuevos.")
        }
        state.failedSites.forEach { (site, error) ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${site.name}: $error", color = AppColors.Error)
                OutlinedButton(onClick = { onRetry(site.id) }) { Text("Reintentar") }
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
            state.siteNames[item.card.siteId],
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
