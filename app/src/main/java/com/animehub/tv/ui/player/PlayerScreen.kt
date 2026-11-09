package com.animehub.tv.ui.player

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.domain.model.ServerHealth
import com.animehub.tv.domain.model.ServerOption
import com.animehub.tv.playback.PlaybackController
import com.animehub.tv.playback.PlayerCommand
import com.animehub.tv.playback.PlayerPhase
import com.animehub.tv.playback.PlayerState
import com.animehub.tv.ui.components.ActionsDialog
import com.animehub.tv.ui.components.DialogAction
import com.animehub.tv.ui.focus.InitialFocusState
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format
import com.animehub.tv.webview.PlayerWebView
import kotlinx.coroutines.delay

/**
 * Reproductor: busca los servidores, reproduce directo el preferido (o muestra los que
 * funcionan), y al terminar pasa solo al siguiente episodio.
 * Control: OK = pausa/reanudar · ←/→ = saltos · ↑ = opciones · ↓ = información · Atrás = salir.
 */
@Composable
fun PlayerScreen(onExit: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    BackHandler { session?.controller?.onExit(); onExit() }

    val current = session
    if (current == null) {
        CenterMessage(error ?: "Preparando ${viewModel.route.animeTitle}…", if (error != null) "Reintentar" to viewModel::load else null, onExit)
        return
    }
    PlayerContent(current, onExit)
}

@Composable
private fun PlayerContent(session: PlayerSession, onExit: () -> Unit) {
    val controller = session.controller
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val web = remember(controller) { PlayerWebView(context, session.adHosts, controller::onMessage) }
    // Reproductor nativo para los servidores con video directo (sin la página del servidor).
    val native = remember(controller) { session.nativeFactory.create(context, session.quality, controller::onMessage) }
    DisposableEffect(web, native) {
        onDispose {
            controller.onExit()
            web.destroy()
            native.release()
        }
    }
    LaunchedEffect(controller) {
        controller.commands.collect { cmd ->
            when (cmd) {
                is PlayerCommand.PlayNative -> {
                    web.stop()
                    native.play(cmd.stream)
                }
                is PlayerCommand.Load -> {
                    native.stop()
                    web.load(cmd.url, session.referer)
                }
                // Las órdenes del mando van al reproductor que se está usando.
                is PlayerCommand.Script -> if (controller.state.value.nativePlayback) native.command(cmd.json) else web.command(cmd.json)
                PlayerCommand.Stop -> {
                    native.stop()
                    web.stop()
                }
            }
        }
    }

    val playing = state.phase is PlayerPhase.Playing
    val rootFocus = remember { FocusRequester() }
    var infoTick by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    // Iconos de pausa/reproducir y de los saltos, como en cualquier reproductor.
    val osd = remember(controller) { OsdState() }
    // En pausa la barra queda a la vista; reproduciendo se oculta a los 4 s de la última tecla.
    LaunchedEffect(infoTick, playing, state.paused) {
        infoVisible = true
        delay(INFO_VISIBLE_MS)
        if (playing && !state.paused) infoVisible = false
    }
    // Los avisos ("Continuando en 32:00", "usando el reproductor web"…) se van solos.
    var noticeVisible by remember { mutableStateOf(false) }
    var lastNotice by remember { mutableStateOf("") }
    LaunchedEffect(state.notice) {
        val notice = state.notice
        if (notice == null) {
            noticeVisible = false
            return@LaunchedEffect
        }
        lastNotice = notice
        noticeVisible = true
        delay(NOTICE_VISIBLE_MS)
        noticeVisible = false
    }
    // Mientras reproduce, el foco lo tiene la pantalla (la web no lo toma) para recibir las teclas.
    LaunchedEffect(playing, menuOpen) {
        if (playing && !menuOpen) runCatching { rootFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!playing || menuOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                infoTick++
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar, Key.MediaPlay, Key.MediaPause -> {
                        osd.playPause(nowPlaying = state.paused)
                        controller.togglePlay()
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        osd.seek(-session.seekSeconds)
                        controller.seekBy(-session.seekSeconds)
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        osd.seek(session.seekSeconds)
                        controller.seekBy(session.seekSeconds)
                    }
                    Key.DirectionUp, Key.Menu -> menuOpen = true
                    Key.MediaNext -> controller.nextEpisode()
                    Key.MediaPrevious -> controller.previousEpisode()
                    Key.DirectionDown -> Unit
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        // Los dos reproductores quedan montados (no se recargan al mostrar paneles encima); se ve
        // el que se está usando. Se ocultan con la visibilidad de la vista y no con transparencia:
        // una capa transparente sobre el video obliga a componer cada cuadro dos veces (tirones).
        val showVideo = playing || state.phase is PlayerPhase.NextCountdown
        val webVisible = showVideo && !state.nativePlayback
        val nativeVisible = showVideo && state.nativePlayback
        AndroidView(
            factory = { web.container },
            modifier = Modifier.fillMaxSize(),
            update = { it.visibility = if (webVisible) View.VISIBLE else View.INVISIBLE },
        )
        native.view?.let { view ->
            AndroidView(
                factory = { view },
                modifier = Modifier.fillMaxSize(),
                update = { it.visibility = if (nativeVisible) View.VISIBLE else View.GONE },
            )
        }
        when (val phase = state.phase) {
            PlayerPhase.LoadingServers -> CenterMessage("Buscando servidores que funcionen…\n${episodeTitle(session, state)}", null, onExit)
            is PlayerPhase.ChoosingServer -> ServerList(session, state, phase.options, onChoose = controller::choose)
            is PlayerPhase.Playing -> {
                PlayerOsd(osd, paused = state.paused && state.durationMs > 0)
                AnimatedVisibility(
                    visible = infoVisible,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
                    exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 3 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    InfoBar(session, state, phase.option)
                }
                AnimatedVisibility(
                    visible = noticeVisible,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it },
                    exit = fadeOut(tween(400)),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Notice(lastNotice)
                }
            }
            is PlayerPhase.NextCountdown -> NextPanel(
                text = "Siguiente: Episodio ${Format.episode(phase.next.number)} en ${phase.secondsLeft} s",
                onNow = controller::nextEpisode,
                onCancel = controller::cancelNext,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
            PlayerPhase.Finished -> FinishedPanel(state, controller, onExit)
            is PlayerPhase.Failed -> CenterMessage(phase.message, "Reintentar" to controller::start, onExit)
        }
    }

    if (menuOpen) {
        ActionsDialog(
            title = episodeTitle(session, state),
            message = (state.phase as? PlayerPhase.Playing)?.option?.server?.name?.let { "Servidor: $it" },
            actions = listOf(
                DialogAction("Cambiar servidor") { menuOpen = false; controller.showServers() },
                // Para avisos o capas del servidor que tapan el video y no se pueden tocar con el control.
                DialogAction("Quitar recuadros") { menuOpen = false; controller.cleanupOverlays() },
                DialogAction("Siguiente episodio", enabled = state.hasNext) { menuOpen = false; controller.nextEpisode() },
                DialogAction("Episodio anterior", enabled = state.hasPrevious) { menuOpen = false; controller.previousEpisode() },
                DialogAction("Salir del reproductor") { menuOpen = false; controller.onExit(); onExit() },
            ),
            onDismiss = { menuOpen = false },
        )
    }
}

private fun episodeTitle(session: PlayerSession, state: PlayerState) =
    "${session.animeTitle} · Episodio ${state.episode?.number?.let(Format::episode) ?: ""}"

/** Lista de servidores: primero los que funcionan (el foco va al primero); los caídos, al final. */
@Composable
private fun ServerList(session: PlayerSession, state: PlayerState, options: List<ServerOption>, onChoose: (ServerOption) -> Unit) {
    val working = options.filter { it.health != ServerHealth.NO_DISPONIBLE }
    val broken = options.filter { it.health == ServerHealth.NO_DISPONIBLE }
    var showBroken by remember(options) { mutableStateOf(working.isEmpty()) }
    val focus = remember(options) { InitialFocusState() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppColors.Fondo),
        contentPadding = PaddingValues(horizontal = 160.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text(episodeTitle(session, state), style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (working.isEmpty()) "Ningún servidor respondió en la comprobación." else "Elige un servidor: se reproduce al instante y se recuerda como preferido.",
                    color = AppColors.TextoSecundario,
                )
                state.notice?.let { Text(it, color = AppColors.Error) }
            }
        }
        itemsIndexed(working, key = { _, o -> o.server.embedUrl }) { i, option ->
            ServerButton(option, if (i == 0) Modifier.initialFocus(focus) else Modifier) { onChoose(option) }
        }
        if (broken.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { showBroken = !showBroken },
                    modifier = if (working.isEmpty()) Modifier.initialFocus(focus) else Modifier,
                ) { Text(if (showBroken) "Ocultar no disponibles" else "Ver no disponibles (${broken.size})") }
            }
            if (showBroken) {
                itemsIndexed(broken, key = { _, o -> "x" + o.server.embedUrl }) { _, option -> ServerButton(option, Modifier) { onChoose(option) } }
            }
        }
    }
}

@Composable
private fun ServerButton(option: ServerOption, modifier: Modifier, onClick: () -> Unit) {
    val label = buildString {
        if (option.preferred) append("★ ")
        append(option.server.name)
        option.server.language?.let { append(" · ").append(it) }
    }
    val status = listOfNotNull(option.health.etiqueta, option.detail?.takeIf { option.health != ServerHealth.FUNCIONA }, option.kind.etiqueta).joinToString(" · ")
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▶  $label", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoBar(session: PlayerSession, state: PlayerState, option: ServerOption) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(horizontal = 56.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(episodeTitle(session, state), style = MaterialTheme.typography.titleLarge)
        Text(
            listOfNotNull(
                option.server.name,
                session.siteName,
                if (state.nativePlayback) "Reproductor nativo" else "Reproductor web",
                "En pausa".takeIf { state.paused },
            ).joinToString(" · "),
            color = AppColors.TextoSecundario,
        )
        if (state.durationMs > 0) {
            SeekBar(livePosition(state), state.durationMs, Modifier.padding(vertical = 4.dp))
        } else {
            Text("Cargando el video…", style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
        }
        Text(
            "OK pausa · ←/→ ${session.seekSeconds} s · ↑ opciones (cambiar servidor, siguiente) · Atrás salir",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextoSecundario,
        )
    }
}

/**
 * Posición que avanza sola entre los avisos del reproductor (llegan cada pocos segundos), para
 * que el tiempo y la barra corran de forma continua mientras se ven.
 */
@Composable
private fun livePosition(state: PlayerState): Long {
    var position by remember { mutableLongStateOf(state.positionMs) }
    LaunchedEffect(state.positionMs, state.paused, state.durationMs) {
        position = state.positionMs
        if (state.paused) return@LaunchedEffect
        val from = state.positionMs
        val start = withFrameMillis { it }
        var elapsed = 0L
        // Como mucho hasta el siguiente aviso (cada 5 s): no se adelanta más de lo que el video avanzó.
        while (position < state.durationMs && elapsed < LIVE_POSITION_MAX_MS) {
            delay(LIVE_POSITION_STEP_MS)
            elapsed = withFrameMillis { it } - start
            position = (from + elapsed).coerceAtMost(state.durationMs)
        }
    }
    return position
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(top = 28.dp)
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun NextPanel(text: String, onNow: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .padding(48.dp)
            .width(460.dp)
            .background(AppColors.Superficie.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onNow, modifier = Modifier.initialFocus()) { Text("Ver ahora") }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

@Composable
private fun FinishedPanel(state: PlayerState, controller: PlaybackController, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(AppColors.Fondo).padding(80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (state.hasNext) "Episodio terminado" else "Has visto el último episodio disponible", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.hasNext) Button(onClick = controller::nextEpisode, modifier = Modifier.initialFocus()) { Text("Siguiente episodio") }
            OutlinedButton(onClick = { controller.onExit(); onExit() }, modifier = if (!state.hasNext) Modifier.initialFocus() else Modifier) {
                Text("Volver a la ficha")
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, action: Pair<String, () -> Unit>?, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppColors.Fondo), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (action != null) Button(onClick = action.second, modifier = Modifier.initialFocus()) { Text(action.first) }
                OutlinedButton(onClick = onExit, modifier = if (action == null) Modifier.initialFocus() else Modifier) { Text("Volver") }
            }
        }
    }
}

private const val INFO_VISIBLE_MS = 4_000L
private const val NOTICE_VISIBLE_MS = 4_000L
private const val LIVE_POSITION_STEP_MS = 500L
private const val LIVE_POSITION_MAX_MS = 6_000L
