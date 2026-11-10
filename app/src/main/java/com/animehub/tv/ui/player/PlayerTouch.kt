package com.animehub.tv.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Capa táctil sobre el video (tablet): un toque muestra/oculta los controles; doble toque en la
 * mitad izquierda retrocede y en la derecha avanza. También impide tocar los anuncios de la página.
 */
@Composable
fun TouchLayer(onTap: () -> Unit, onDoubleTap: (forward: Boolean) -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { offset -> doubleTap(offset.x > size.width / 2) },
                )
            },
    )
}

/** Controles en pantalla para tablet: retroceder, pausa/reproducir, avanzar, servidores y opciones. */
@Composable
fun TouchControls(
    paused: Boolean,
    seekSeconds: Int,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onServers: () -> Unit,
    onOptions: () -> Unit,
    onBack: () -> Unit,
) = Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
    Row(
        Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton("← Volver", onBack)
        Box(Modifier.weight(1f))
        PillButton("Servidores", onServers)
        PillButton("Opciones", onOptions)
    }
    Row(
        Modifier.align(Alignment.Center),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeekButton("−$seekSeconds s", forward = false) { onSeek(-seekSeconds) }
        TouchButton(if (paused) "Reproducir" else "Pausa", size = 112.dp, onClick = onToggle) {
            if (paused) drawPlay() else drawPause()
        }
        SeekButton("+$seekSeconds s", forward = true) { onSeek(seekSeconds) }
    }
}

@Composable
private fun SeekButton(label: String, forward: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TouchButton(label, size = 80.dp, onClick = onClick) { drawSeek(forward) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Botón redondo solo táctil (no recibe el foco: en la TV no aparece). */
@Composable
private fun TouchButton(description: String, size: Dp, onClick: () -> Unit, draw: DrawScope.() -> Unit) {
    val click by rememberUpdatedState(onClick)
    Box(
        Modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { click(); true }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { click() }) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.42f)) { draw() }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        modifier = Modifier
            .semantics {
                role = Role.Button
                onClick { click(); true }
            }
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { click() }) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
