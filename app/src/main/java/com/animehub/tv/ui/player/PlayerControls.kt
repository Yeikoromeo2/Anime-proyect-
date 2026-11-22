package com.animehub.tv.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format

/**
 * Barra de avance que se maneja con el dedo (tocar para ir a ese punto o arrastrar) y con las
 * flechas del control (las mueve el reproductor con [onPending]/[onCommit]).
 * [pendingMs] es el punto elegido que aún no se aplicó; mientras exista, la barra lo muestra.
 */
@Composable
fun ScrubBar(
    positionMs: Long,
    durationMs: Long,
    pendingMs: Long?,
    onPending: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = pendingMs ?: positionMs
    val fraction = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var width by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val pending by rememberUpdatedState(pendingMs)
    val duration by rememberUpdatedState(durationMs)
    val pendingCb by rememberUpdatedState(onPending)
    val commitCb by rememberUpdatedState(onCommit)
    fun msAt(x: Float) = (x / width.width.coerceAtLeast(1) * duration).toLong().coerceIn(0, duration)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onSizeChanged { width = it }
                .semantics { contentDescription = "Barra de avance" }
                .pointerInput(Unit) { detectTapGestures(onTap = { o -> if (duration > 0) commitCb(msAt(o.x)) }) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> if (duration > 0) pendingCb(msAt(o.x)) },
                        onHorizontalDrag = { change, _ -> if (duration > 0) pendingCb(msAt(change.position.x)) },
                        onDragEnd = { pending?.let(commitCb) },
                        onDragCancel = { pending?.let(commitCb) },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(AppColors.Acento2, RoundedCornerShape(3.dp)))
            val knob = with(density) { (width.width * fraction).toDp() - 9.dp }
            Box(Modifier.offset(x = knob.coerceAtLeast(0.dp)).size(if (pendingMs != null) 22.dp else 18.dp).background(Color.White, CircleShape))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Format.time(shown), style = MaterialTheme.typography.bodySmall, color = if (pendingMs != null) AppColors.Acento2 else Color.White)
            Box(Modifier.weight(1f))
            Text("−" + Format.time((durationMs - shown).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
        }
    }
}

/**
 * Botón redondo con icono: se toca en la tablet y se alcanza con las flechas en la TV (el foco
 * lo resalta con un aro). Sin texto: la descripción es solo para accesibilidad.
 */
@Composable
fun IconControl(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    enabled: Boolean = true,
    draw: DrawScope.() -> Unit,
) {
    val click by rememberUpdatedState(onClick)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .background(Color.Black.copy(alpha = if (focused) 0.85f else 0.55f), CircleShape)
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onClick { click(); true }
            }
            .focusable(enabled, interaction)
            .onKeyEvent { e ->
                if (enabled && e.type == KeyEventType.KeyDown && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    click(); true
                } else false
            }
            .pointerInput(enabled) { if (enabled) detectTapGestures(onTap = { click() }) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.46f)) {
            draw()
            if (!enabled) drawRect(Color.Black.copy(alpha = 0.55f))
        }
    }
}

/** Fila de controles: anterior, retroceder, pausa, avanzar, siguiente (y los extras a la derecha). */
@Composable
fun ControlRow(
    paused: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    seekSeconds: Int,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onVersions: () -> Unit,
    onClean: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconControl("Episodio anterior", onPrevious, enabled = hasPrevious, modifier = firstFocus?.let { Modifier.focusRequester(it) } ?: Modifier) { drawSkip(forward = false) }
        IconControl("Retroceder $seekSeconds segundos", { onSeek(-seekSeconds) }) { drawSeek(forward = false) }
        IconControl(if (paused) "Reproducir" else "Pausa", onToggle, size = 64.dp) { if (paused) drawPlay() else drawPause() }
        IconControl("Avanzar $seekSeconds segundos", { onSeek(seekSeconds) }) { drawSeek(forward = true) }
        IconControl("Episodio siguiente", onNext, enabled = hasNext) { drawSkip(forward = true) }
        Box(Modifier.size(24.dp))
        IconControl("Idioma y versión", onVersions) { drawVersions() }
        IconControl("Quitar recuadros", onClean) { drawClear() }
    }
}

/** Triángulo con barra: saltar al episodio siguiente (o al anterior, reflejado). */
internal fun DrawScope.drawSkip(forward: Boolean) {
    val w = size.width
    val h = size.height
    val tri = Path().apply {
        if (forward) { moveTo(w * 0.08f, h * 0.15f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.08f, h * 0.85f) }
        else { moveTo(w * 0.92f, h * 0.15f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.92f, h * 0.85f) }
        close()
    }
    drawPath(tri, Color.White)
    val barX = if (forward) w * 0.78f else w * 0.06f
    drawRoundRect(Color.White, Offset(barX, h * 0.15f), Size(w * 0.16f, h * 0.7f), CornerRadius(w * 0.04f))
}

/** Tres rayas con un punto: elegir idioma/versión. */
internal fun DrawScope.drawVersions() {
    val h = size.height
    val w = size.width
    for (i in 0..2) {
        val y = h * (0.2f + i * 0.3f)
        drawRoundRect(Color.White, Offset(w * 0.3f, y), Size(w * 0.68f, h * 0.12f), CornerRadius(h * 0.06f))
        drawCircle(Color.White, h * 0.07f, Offset(w * 0.08f, y + h * 0.06f))
    }
}

/** Cuadro con una cruz: quitar recuadros que tapan el video. */
internal fun DrawScope.drawClear() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color.White, Offset(w * 0.04f, h * 0.12f), Size(w * 0.92f, h * 0.76f), CornerRadius(w * 0.1f), style = Stroke(w * 0.1f))
    drawLine(Color.White, Offset(w * 0.34f, h * 0.36f), Offset(w * 0.66f, h * 0.64f), w * 0.1f)
    drawLine(Color.White, Offset(w * 0.66f, h * 0.36f), Offset(w * 0.34f, h * 0.64f), w * 0.1f)
}

/** Icono de "enviar a la pantalla" (Chromecast): cuadro con ondas en la esquina. */
fun DrawScope.drawCast(color: Color = Color.White) {
    val w = size.width
    val h = size.height
    val sw = w * 0.09f
    val frame = Path().apply {
        moveTo(w * 0.34f, h * 0.2f); lineTo(w * 0.92f, h * 0.2f); lineTo(w * 0.92f, h * 0.8f); lineTo(w * 0.62f, h * 0.8f)
    }
    drawPath(frame, color, style = Stroke(sw))
    val cx = w * 0.08f
    val cy = h * 0.9f
    for (r in listOf(w * 0.26f, w * 0.46f)) {
        drawArc(color, 270f, 90f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(sw))
    }
    drawCircle(color, sw * 0.9f, Offset(w * 0.08f, h * 0.9f))
}
