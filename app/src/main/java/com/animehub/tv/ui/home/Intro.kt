package com.animehub.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.R
import com.animehub.tv.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** La animación de entrada se muestra una vez por apertura de la app (las pruebas la desactivan). */
object Intro {
    @Volatile var enabled = true
    @Volatile var shown = false
}

/**
 * Entrada de la app: líneas de velocidad de manga que giran, la mascota que aparece con un rebote,
 * el nombre que sube con un fundido y un destello. Dura menos de 2 s y se salta con cualquier tecla.
 */
@Composable
fun IntroSplash(onDone: () -> Unit) {
    val mascot = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { spin.animateTo(1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart)) }
        launch { mascot.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        kotlinx.coroutines.delay(450)
        title.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(750)
        out.animateTo(0f, tween(450))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = out.value }
            .background(Brush.radialGradient(listOf(Color(0xFF3A2D73), AppColors.Fondo))),
        contentAlignment = Alignment.Center,
    ) {
        // Líneas de velocidad alrededor del centro.
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val reach = size.maxDimension
            val n = 28
            for (i in 0 until n) {
                val a = (i.toFloat() / n + spin.value * 0.1f) * 2f * Math.PI.toFloat()
                val inner = size.minDimension * (0.2f + 0.05f * (i % 3))
                val alpha = (0.05f + 0.1f * (i % 2)) * mascot.value
                drawLine(
                    Color.White.copy(alpha = alpha),
                    c.copy(x = c.x + cos(a) * inner, y = c.y + sin(a) * inner),
                    c.copy(x = c.x + cos(a) * reach, y = c.y + sin(a) * reach),
                    strokeWidth = if (i % 2 == 0) 3f else 1.5f,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.mascota),
                contentDescription = null,
                modifier = Modifier
                    .size(180.dp)
                    .scale(0.4f + 0.6f * mascot.value)
                    .graphicsLayer { alpha = mascot.value.coerceIn(0f, 1f) },
            )
            Text(
                "AnimeHub TV",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 40f
                },
            )
        }
    }
}
