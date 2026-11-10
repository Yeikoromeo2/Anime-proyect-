package com.animehub.tv.ui.search

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButtonDefaults
import com.animehub.tv.R
import com.animehub.tv.voice.VoiceSession
import com.animehub.tv.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.TvTextField
import com.animehub.tv.ui.focus.InitialFocusState
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.voice.VoiceSearch
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Búsqueda: micrófono (foco inicial si hay reconocedor de voz), texto, chips de sitio, accesos
 * rápidos y panel de filtros. Resultados en cuadrícula de portadas con la etiqueta del sitio.
 * @param startVoice abre el dictado al entrar (atajo de voz desde Inicio).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenAnime: (siteId: String, url: String, title: String, cover: String?) -> Unit,
    startVoice: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    // Voz dentro de la app (sin abrir el asistente de la TV); si no hay, la pantalla del sistema.
    val inAppVoice = remember { viewModel.voice.isAvailable(context) }
    val voiceAvailable = remember { inAppVoice || VoiceSearch.isAvailable(context) }
    val gridState = rememberLazyGridState()
    // Un estado de foco por búsqueda: cada búsqueda nueva lleva el foco al primer resultado.
    val resultsFocus = remember(state.version) { InitialFocusState() }

    // Lo dictado va al campo y se busca de inmediato.
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = if (result.resultCode == Activity.RESULT_OK) VoiceSearch.parseResult(result.data) else null
        if (spoken != null) {
            text = spoken
            viewModel.search(spoken)
        }
    }
    val systemVoice: () -> Unit = {
        try {
            voiceLauncher.launch(VoiceSearch.intent())
        } catch (e: ActivityNotFoundException) {
            voiceError = "No se pudo abrir el reconocimiento de voz"
        }
    }
    var session by remember { mutableStateOf<VoiceSession?>(null) }
    DisposableEffect(Unit) { onDispose { session?.destroy() } }
    var voiceOpen by remember { mutableStateOf(false) }
    val listen: () -> Unit = {
        val current = session ?: viewModel.voice.create(context).also { session = it }
        voiceOpen = true
        current.start()
    }
    // El micrófono necesita permiso la primera vez; si se niega, queda la pantalla de voz del sistema.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else systemVoice()
    }
    val startVoiceSearch: () -> Unit = {
        voiceError = null
        when {
            !inAppVoice -> systemVoice()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val idle = remember { MutableStateFlow<VoiceState>(VoiceState.Idle) }
    val voiceState by (session?.state ?: idle).collectAsStateWithLifecycle()
    // Lo entendido va al campo y se busca de inmediato.
    LaunchedEffect(voiceState) {
        val result = voiceState as? VoiceState.Result ?: return@LaunchedEffect
        voiceOpen = false
        text = result.text
        viewModel.search(result.text)
    }
    var voiceStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startVoice && voiceAvailable && !voiceStarted) {
            voiceStarted = true
            startVoiceSearch()
        }
    }

    // Desplazamiento infinito: al acercarse al final se piden más resultados.
    LaunchedEffect(gridState, state.version) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (state.results.isNotEmpty() && last >= gridState.layoutInfo.totalItemsCount - 6) viewModel.loadMore() }
    }

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "cabecera") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Column {
                    Text("Buscar", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (voiceAvailable) "Escribe o di el nombre de un anime" else "Escribe el nombre de un anime",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextoSecundario,
                    )
                }
                // Barra de búsqueda: micrófono de color, campo redondeado, Buscar y Filtros.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (voiceAvailable) {
                        Button(
                            onClick = startVoiceSearch,
                            modifier = Modifier.initialFocus(),
                            shape = ButtonDefaults.shape(RoundedCornerShape(28.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = AppColors.Acento,
                                contentColor = Color.White,
                                focusedContainerColor = AppColors.Sakura,
                                focusedContentColor = Color.White,
                            ),
                        ) { Text("🎤 Hablar") }
                    }
                    TvTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = "Nombre del anime",
                        onDone = { viewModel.search(text) },
                        pill = true,
                        modifier = Modifier.width(460.dp).let { if (voiceAvailable) it else it.initialFocus() },
                    )
                    Button(onClick = { viewModel.search(text) }, shape = ButtonDefaults.shape(RoundedCornerShape(28.dp))) { Text("Buscar") }
                    OutlinedButton(onClick = { panelOpen = true }, shape = OutlinedButtonDefaults.shape(RoundedCornerShape(28.dp))) {
                        Text(if (state.filters.activeCount > 0) "Filtros (${state.filters.activeCount})" else "Filtros")
                    }
                }
                voiceError?.let { Text(it, color = AppColors.Error) }
                if (state.sites.size > 2) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(state.sites, key = { it.id ?: "todos" }) { site ->
                            FilterChip(selected = site.id == state.selectedSite, onClick = { viewModel.selectSite(site.id) }) { Text(site.name) }
                        }
                    }
                }
                if (state.quickFilters.isNotEmpty() || state.filters.activeCount > 0) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        items(state.quickFilters, key = { it.label }) { quick ->
                            FilterChip(selected = state.filters == quick.selection, onClick = {
                                viewModel.setFilters(if (state.filters == quick.selection) FilterSelection.NONE else quick.selection)
                                viewModel.search(text)
                            }) { Text(quick.label) }
                        }
                        if (state.filters.activeCount > 0) {
                            item(key = "resumen") {
                                Text("Filtros: ${state.filterSummary}", color = AppColors.Acento2, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                            item(key = "limpiar") {
                                OutlinedButton(onClick = { viewModel.clearFilters(); if (text.isNotBlank()) viewModel.search(text) }) { Text("Limpiar") }
                            }
                        }
                    }
                }
                if (!state.searched && !state.loading) WelcomeHint(voiceAvailable)
                if (!state.searched && recent.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Búsquedas recientes", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                            items(recent, key = { it }) { query -> OutlinedButton(onClick = { text = query; viewModel.search(query) }) { Text(query) } }
                            item { OutlinedButton(onClick = { viewModel.clearRecent() }) { Text("Borrar recientes") } }
                        }
                    }
                }
                StatusLine(state)
            }
        }
        itemsIndexed(state.results, key = { _, card -> card.siteId + card.url }) { index, card ->
            // Los resultados aparecen con un fundido suave.
            Box(Modifier.animateItem(fadeInSpec = tween(300), placementSpec = null, fadeOutSpec = null)) {
                ResultCard(
                    card = card,
                    siteName = state.siteNames[card.siteId],
                    cardModifier = if (index == 0) Modifier.initialFocus(resultsFocus) else Modifier,
                    onClick = { onOpenAnime(card.siteId, card.url, card.title, card.coverUrl) },
                )
            }
        }
        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "cargando-mas") {
                Text("Cargando más resultados…", color = AppColors.TextoSecundario)
            }
        }
    }

    if (voiceOpen && voiceState !is VoiceState.Result) {
        VoicePanel(
            state = voiceState,
            onCancel = {
                session?.stop()
                voiceOpen = false
            },
            onRetry = listen,
        )
    }
    }

    if (panelOpen) {
        val siteName = state.sites.firstOrNull { it.id == state.selectedSite }?.name ?: "Todos los sitios"
        FilterPanel(
            title = "Filtros · $siteName",
            groups = state.filterGroups,
            loading = state.filtersLoading,
            selection = state.filters,
            onToggle = viewModel::toggleFilter,
            onClear = viewModel::clearFilters,
            onClose = {
                panelOpen = false
                viewModel.search(text)
            },
        )
    }
}

/** Antes de buscar: la mascota y una pista de qué hacer. */
@Composable
private fun WelcomeHint(voice: Boolean) {
    Row(
        Modifier
            .background(Brush.horizontalGradient(listOf(AppColors.SuperficieFoco.copy(alpha = 0.6f), Color.Transparent)), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(painterResource(R.drawable.mascota), contentDescription = null, modifier = Modifier.size(64.dp))
        Column {
            Text("¿Qué quieres ver hoy?", style = MaterialTheme.typography.titleMedium)
            Text(
                if (voice) "Pulsa 🎤 Hablar y di el nombre, o escríbelo. También puedes buscar solo con filtros."
                else "Escribe el nombre del anime. También puedes buscar solo con filtros.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextoSecundario,
            )
        }
    }
}

@Composable
private fun StatusLine(state: SearchUiState) {
    val what = state.query.ifEmpty { "los filtros elegidos" }.let { if (state.query.isEmpty()) it else "\"$it\"" }
    when {
        state.loading -> Text("Buscando $what…", color = AppColors.TextoSecundario)
        state.noProviders -> Text("No hay sitios activos con proveedor. Revisa Ajustes → Sitios.", color = AppColors.TextoSecundario)
        state.searched && state.results.isEmpty() && state.notices.isEmpty() -> Text("No se encontraron resultados para $what.", color = AppColors.TextoSecundario)
        state.searched && state.results.isNotEmpty() -> Text("${state.results.size} resultados para $what", color = AppColors.TextoSecundario)
    }
    state.notices.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = AppColors.Error) }
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
