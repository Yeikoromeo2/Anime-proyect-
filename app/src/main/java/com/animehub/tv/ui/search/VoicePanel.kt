package com.animehub.tv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.R
import com.animehub.tv.ui.components.Button
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.voice.VoiceState

/**
 * Panel de voz de la app (en lugar de la pantalla del asistente de la TV): la mascota late con el
 * volumen de la voz y lo que se va entendiendo aparece debajo. Atrás o "Cancelar" lo cierra.
 */
@Composable
fun VoicePanel(state: VoiceState, onCancel: () -> Unit, onRetry: () -> Unit) {
    BackHandler(onBack = onCancel)
    val level = (state as? VoiceState.Listening)?.level ?: 0f
    val pulse by animateFloatAsState(targetValue = 1f + level * 0.35f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f), label = "voz")
    val ring by animateFloatAsState(targetValue = 1f + level * 0.7f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 120f), label = "aro")
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Fondo.copy(alpha = 0.92f))
            .semantics { contentDescription = "Búsqueda por voz" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                // Aros que crecen con la voz.
                Box(
                    Modifier
                        .size(150.dp)
                        .scale(ring)
                        .background(Brush.radialGradient(listOf(AppColors.Sakura.copy(alpha = 0.35f), AppColors.Acento.copy(alpha = 0f))), CircleShape),
                )
                Box(
                    Modifier
                        .size(140.dp)
                        .scale(pulse)
                        .background(Brush.linearGradient(listOf(AppColors.Acento, AppColors.Sakura)), CircleShape),
                )
                Image(
                    painterResource(R.drawable.mascota),
                    contentDescription = null,
                    modifier = Modifier.size(118.dp).scale(pulse).clip(CircleShape),
                )
            }
            val (title, detail) = when (state) {
                is VoiceState.Listening -> "Te escucho…" to state.partial.ifBlank { "Di el nombre del anime" }
                is VoiceState.Thinking -> "Buscando…" to state.partial
                is VoiceState.Error -> "Uy…" to state.message
                is VoiceState.Result -> "Listo" to state.text
                VoiceState.Idle -> "Preparando el micrófono…" to ""
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (detail.isNotBlank()) {
                Text(
                    if (state is VoiceState.Listening && state.partial.isNotBlank() || state is VoiceState.Thinking) "“$detail”" else detail,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state is VoiceState.Error) AppColors.Error else AppColors.TextoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (state is VoiceState.Error) {
                    Button(onClick = onRetry, modifier = Modifier.initialFocus()) { Text("Reintentar") }
                    OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                } else {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.initialFocus()) { Text("Cancelar") }
                }
            }
        }
    }
}
