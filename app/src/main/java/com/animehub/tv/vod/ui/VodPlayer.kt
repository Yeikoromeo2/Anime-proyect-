package com.animehub.tv.vod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.data.settings.PreferredQuality
import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.nativeplayer.NativePlaybackFactory
import com.animehub.tv.ui.components.ListItem
import com.animehub.tv.ui.components.SecondaryText
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.navigation.VodPlayerRoute
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format
import com.animehub.tv.vod.VodRepository
import com.animehub.tv.vod.models.MediaType
import com.animehub.tv.vod.models.ProviderId
import com.animehub.tv.vod.models.StreamSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

enum class VodSignal { BUSCANDO, CARGANDO, REPRODUCIENDO, SIN_FUENTES }

data class VodPlayerState(
    val title: String = "",
    val sources: List<StreamSource> = emptyList(),
    val index: Int = 0,
    val signal: VodSignal = VodSignal.BUSCANDO,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val paused: Boolean = false,
    /** Aviso breve ("Fuente 2…") que la pantalla muestra un momento. */
    val notice: String? = null,
    val tick: Int = 0,
)

/**
 * Reproducción de una película o un episodio: las fuentes se piden en el momento de reproducir, se empieza por la
 * primera y, si falla (error o 25 s sin imagen), se pasa sola a la siguiente. También se puede elegir a mano.
 */
@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VodRepository,
    val nativeFactory: NativePlaybackFactory,
) : ViewModel() {
    val route: VodPlayerRoute = savedStateHandle.toRoute()
    private val _state = MutableStateFlow(VodPlayerState(title = route.title))
    val state: StateFlow<VodPlayerState> = _state.asStateFlow()
    private val _streams = MutableSharedFlow<DirectStream?>(replay = 1, extraBufferCapacity = 4)
    val streams: SharedFlow<DirectStream?> = _streams.asSharedFlow()
    private var watchdog: Job? = null

    init {
        viewModelScope.launch {
            val sources = repository.streams(ProviderId.of(route.provider), MediaType.of(route.type), route.id, route.season, route.episode)
            if (sources.isEmpty()) _state.update { it.copy(signal = VodSignal.SIN_FUENTES) }
            else { _state.update { it.copy(sources = sources) }; play(0, auto = true) }
        }
    }

    /** El usuario eligió una fuente de la lista. */
    fun choose(index: Int) = play(index, auto = false)

    private fun play(index: Int, auto: Boolean) {
        val source = _state.value.sources.getOrNull(index) ?: return
        _state.update { it.copy(index = index, signal = VodSignal.CARGANDO, positionMs = 0, durationMs = 0, paused = false, notice = "Fuente ${index + 1}" + source.quality.takeIf { q -> q != "Auto" }?.let { q -> " · $q" }.orEmpty(), tick = it.tick + 1) }
        _streams.tryEmit(DirectStream(source.url, typeOf(source.url), source.headers))
        watchdog?.cancel()
        watchdog = viewModelScope.launch { delay(START_WAIT_MS); if (_state.value.signal == VodSignal.CARGANDO) failed() }
    }

    /** Esta fuente falló: se pasa a la siguiente; si no hay más, se avisa. */
    private fun failed() {
        val s = _state.value
        if (s.index + 1 < s.sources.size) play(s.index + 1, auto = true)
        else { watchdog?.cancel(); _streams.tryEmit(null); _state.update { it.copy(signal = VodSignal.SIN_FUENTES, notice = null) } }
    }

    fun onPlayerMessage(raw: String) {
        val o = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (o["type"]?.jsonPrimitive?.contentOrNull) {
            "playing", "progress" -> {
                watchdog?.cancel()
                val pos = o["pos"]?.jsonPrimitive?.doubleOrNull
                val dur = o["dur"]?.jsonPrimitive?.doubleOrNull
                _state.update { it.copy(signal = VodSignal.REPRODUCIENDO, positionMs = pos?.let { p -> (p * 1000).toLong() } ?: it.positionMs, durationMs = dur?.let { d -> (d * 1000).toLong() } ?: it.durationMs, paused = o["paused"]?.jsonPrimitive?.contentOrNull == "true") }
            }
            "error" -> if (_state.value.signal != VodSignal.SIN_FUENTES) failed()
        }
    }

    private fun typeOf(url: String) = if (Regex("""\.m3u8(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)) StreamType.HLS else StreamType.MP4

    private companion object {
        const val START_WAIT_MS = 25_000L
    }
}

/** Reproductor a pantalla completa: OK pausa, ←/→ saltan 10 s, ↑ elige la fuente, Atrás oculta la barra y luego sale. */
@Composable
fun VodPlayerScreen(onExit: () -> Unit, viewModel: VodPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val native = remember { viewModel.nativeFactory.create(context, PreferredQuality.AUTO, viewModel::onPlayerMessage) }
    DisposableEffect(native) { onDispose { native.release() } }
    LaunchedEffect(native) { viewModel.streams.collect { s -> if (s == null) native.stop() else native.play(s) } }

    var overlay by remember { mutableStateOf(true) }
    var picker by remember { mutableStateOf(false) }
    var touch by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    LaunchedEffect(overlay, touch, state.paused) { if (overlay && !state.paused && state.signal == VodSignal.REPRODUCIENDO) { delay(4_000); overlay = false } }
    LaunchedEffect(state.tick) { overlay = true; touch++ }
    LaunchedEffect(state.signal) { if (state.signal == VodSignal.SIN_FUENTES && state.sources.isNotEmpty()) picker = true }
    LaunchedEffect(picker) { if (!picker) runCatching { root.requestFocus() } }

    fun back() {
        when {
            picker -> picker = false
            overlay && state.signal == VodSignal.REPRODUCIENDO -> overlay = false
            else -> onExit()
        }
    }
    BackHandler { back() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(root).focusable(enabled = !picker)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (e.key == Key.Back) { back(); return@onPreviewKeyEvent true }
                if (picker) return@onPreviewKeyEvent false
                touch++
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> { native.command("""{"type":"toggle"}"""); overlay = true }
                    Key.DirectionLeft, Key.MediaRewind -> { native.command("""{"type":"seekBy","seconds":-10}"""); overlay = true }
                    Key.DirectionRight, Key.MediaFastForward -> { native.command("""{"type":"seekBy","seconds":10}"""); overlay = true }
                    Key.DirectionUp, Key.Menu -> picker = state.sources.isNotEmpty()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        native.view?.let { v -> AndroidView(factory = { v }, modifier = Modifier.fillMaxSize()) }
        // Tocar la pantalla (tablet) muestra la barra.
        Box(Modifier.fillMaxSize().pointerInput(picker) { detectTapGestures(onTap = { if (picker) picker = false else { overlay = !overlay; touch++ } }) })

        when (state.signal) {
            VodSignal.BUSCANDO -> Text("Buscando fuentes…", modifier = Modifier.align(Alignment.Center), color = AppColors.TextoSecundario)
            VodSignal.CARGANDO -> Text("Cargando…", modifier = Modifier.align(Alignment.Center), color = AppColors.TextoSecundario)
            VodSignal.SIN_FUENTES -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No hay fuentes disponibles por ahora", style = MaterialTheme.typography.titleMedium)
                Text("Atrás para volver", color = AppColors.TextoSecundario)
            }
            VodSignal.REPRODUCIENDO -> Unit
        }
        AnimatedVisibility(visible = overlay && !picker, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))).padding(horizontal = 56.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                Text(
                    listOfNotNull(state.notice, if (state.durationMs > 0) "${Format.time(state.positionMs)} / ${Format.time(state.durationMs)}" else null, if (state.paused) "En pausa" else null).joinToString("  ·  "),
                    color = AppColors.TextoSecundario,
                )
                if (state.sources.size > 1) Text("↑ cambiar de fuente", color = AppColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
            }
        }
        AnimatedVisibility(visible = picker, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(
                Modifier.width(420.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.78f)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Fuente", style = MaterialTheme.typography.headlineSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(state.sources) { i, s ->
                        ListItem(
                            selected = i == state.index,
                            onClick = { viewModel.choose(i); picker = false },
                            modifier = if (i == state.index || (i == 0 && state.signal == VodSignal.SIN_FUENTES)) Modifier.initialFocus() else Modifier,
                            headlineContent = { Text("Fuente ${i + 1}" + if (i == state.index && state.signal != VodSignal.SIN_FUENTES) "  ▶ En uso" else "", maxLines = 1) },
                            supportingContent = { SecondaryText(listOf(s.name, s.quality.takeIf { it != "Auto" }).filterNotNull().joinToString(" · ")) },
                        )
                    }
                }
            }
        }
    }
}
