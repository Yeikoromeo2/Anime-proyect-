package com.animehub.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format
import kotlinx.coroutines.delay

/** Respuesta visual a una tecla del control (como en cualquier reproductor). */
sealed interface OsdFeedback {
    data object Play : OsdFeedback
    data object Pause : OsdFeedback
    /** Saltos seguidos en la misma dirección se suman: "+10 s", "+20 s"… */
    data class Seek(val seconds: Int) : OsdFeedback
}

/** Estado de la respuesta visual: cada evento nuevo reinicia la animación. */
class OsdState {
    var feedback by mutableStateOf<OsdFeedback?>(null)
        private set
    var tick by mutableStateOf(0)
        private set
    private var lastSeekAt = 0L

    fun playPause(nowPlaying: Boolean) {
        feedback = if (nowPlaying) OsdFeedback.Play else OsdFeedback.Pause
        tick++
    }

    fun seek(seconds: Int, now: Long = System.currentTimeMillis()) {
        val previous = feedback as? OsdFeedback.Seek
        val sameDirection = previous != null && (previous.seconds > 0) == (seconds > 0)
        feedback = OsdFeedback.Seek(if (sameDirection && now - lastSeekAt < SEEK_STACK_MS) previous!!.seconds + seconds else seconds)
        lastSeekAt = now
        tick++
    }

    private companion object {
        const val SEEK_STACK_MS = 1_500L
    }
}

/**
 * Animaciones del reproductor: icono grande de play/pausa en el centro y, al avanzar o retroceder,
 * el salto acumulado a cada lado. En pausa queda el icono de pausa tenue en el centro.
 */
@Composable
fun BoxScope.PlayerOsd(osd: OsdState, paused: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(osd.tick) {
        if (osd.tick == 0) return@LaunchedEffect
        visible = true
        delay(if (osd.feedback is OsdFeedback.Seek) SEEK_VISIBLE_MS else TOGGLE_VISIBLE_MS)
        visible = false
    }
    val feedback = osd.feedback

    // En pausa: icono de pausa tenue, fijo, mientras no haya otra animación.
    AnimatedVisibility(
        visible = paused && !visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        RoundIcon(Modifier.semantics { contentDescription = "En pausa" }, alpha = 0.55f) { drawPause() }
    }

    AnimatedVisibility(
        visible = visible && (feedback == OsdFeedback.Play || feedback == OsdFeedback.Pause),
        enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.7f),
        exit = fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.3f),
        modifier = Modifier.align(Alignment.Center),
    ) {
        val playing = feedback == OsdFeedback.Play
        RoundIcon(Modifier.semantics { contentDescription = if (playing) "Reproducir" else "Pausa" }) {
            if (playing) drawPlay() else drawPause()
        }
    }

    val seek = feedback as? OsdFeedback.Seek
    AnimatedVisibility(
        visible = visible && seek != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(400)),
        modifier = Modifier.align(if ((seek?.seconds ?: 0) >= 0) Alignment.CenterEnd else Alignment.CenterStart),
    ) {
        val forward = (seek?.seconds ?: 0) >= 0
        val text = (if (forward) "+" else "−") + "${kotlin.math.abs(seek?.seconds ?: 0)} s"
        Column(
            Modifier
                .padding(horizontal = 96.dp)
                .semantics { contentDescription = text },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIcon(Modifier, size = 104) { drawSeek(forward) }
            Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}

/** Barra de avance grande, con punto en la posición y tiempo transcurrido / restante. */
@Composable
fun SeekBar(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var width by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .onSizeChanged { width = it },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(AppColors.Acento2, RoundedCornerShape(3.dp)))
            val knob = with(density) { (width.width * fraction).toDp() - 8.dp }
            Box(Modifier.offset(x = knob.coerceAtLeast(0.dp)).size(16.dp).background(Color.White, CircleShape))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Format.time(positionMs), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f))
            Text("−" + Format.time((durationMs - positionMs).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
        }
    }
}

@Composable
private fun RoundIcon(modifier: Modifier, alpha: Float = 0.8f, size: Int = 128, draw: DrawScope.() -> Unit) {
    Box(
        modifier
            .size(size.dp)
            .background(Color.Black.copy(alpha = alpha * 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size((size * 0.45f).dp)) { draw() }
    }
}

private fun DrawScope.drawPlay() {
    val path = Path().apply {
        moveTo(size.width * 0.22f, size.height * 0.1f)
        lineTo(size.width * 0.92f, size.height * 0.5f)
        lineTo(size.width * 0.22f, size.height * 0.9f)
        close()
    }
    drawPath(path, Color.White)
}

private fun DrawScope.drawPause() {
    val barWidth = size.width * 0.28f
    val radius = CornerRadius(barWidth * 0.25f)
    drawRoundRect(Color.White, Offset(size.width * 0.14f, size.height * 0.1f), Size(barWidth, size.height * 0.8f), radius)
    drawRoundRect(Color.White, Offset(size.width * 0.58f, size.height * 0.1f), Size(barWidth, size.height * 0.8f), radius)
}

/** Dos triángulos (avanzar) o reflejados (retroceder). */
private fun DrawScope.drawSeek(forward: Boolean) {
    fun triangle(start: Float) = Path().apply {
        val w = size.width * 0.48f
        if (forward) {
            moveTo(start, size.height * 0.18f); lineTo(start + w, size.height * 0.5f); lineTo(start, size.height * 0.82f)
        } else {
            moveTo(start + w, size.height * 0.18f); lineTo(start, size.height * 0.5f); lineTo(start + w, size.height * 0.82f)
        }
        close()
    }
    drawPath(triangle(size.width * 0.04f), Color.White)
    drawPath(triangle(size.width * 0.48f), Color.White)
}

private const val TOGGLE_VISIBLE_MS = 650L
private const val SEEK_VISIBLE_MS = 900L
