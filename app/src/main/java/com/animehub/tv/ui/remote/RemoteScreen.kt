package com.animehub.tv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.remote.PlayTarget
import com.animehub.tv.remote.RemoteStatus
import com.animehub.tv.ui.components.Button
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.player.SeekBar
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format

/**
 * "Enviar a mi TV" desde la tablet: el episodio se reproduce en la TV y aquí queda el control:
 * pausa, saltos, anterior/siguiente y detener, con el minuto que va la TV.
 */
@Composable
fun RemoteScreen(onBack: () -> Unit, viewModel: RemoteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val route = viewModel.route
    val status = state.status
    Box(Modifier.fillMaxSize().background(AppColors.Fondo)) {
        AsyncImage(
            model = route.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.35f),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.Fondo.copy(alpha = 0.6f), AppColors.Fondo))))
        Row(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = route.coverUrl,
                contentDescription = route.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 200.dp, height = 290.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.Superficie),
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 720.dp)) {
                Text("${iconOf(route.kind)}  En ${route.tvName}", style = MaterialTheme.typography.titleMedium, color = AppColors.Acento2)
                Text(status?.title ?: route.animeTitle, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "Episodio ${Format.episode(status?.episode ?: route.episodeNumber)}",
                        status?.server?.let { "Servidor $it" },
                    ).joinToString(" · "),
                    color = AppColors.TextoSecundario,
                )
                Text(phaseText(state, route.tvName, route.kind), color = if (state.phase is SendPhase.Failed || state.phase == SendPhase.Rejected) AppColors.Error else AppColors.Foco)
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Error) }
                if (status != null && status.durationMs > 0) SeekBar(status.positionMs, status.durationMs)
                when (state.phase) {
                    SendPhase.Connected -> Controls(status, viewModel.seekSeconds, viewModel::command, onBack)
                    SendPhase.Rejected, is SendPhase.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::send, modifier = Modifier.initialFocus()) { Text("Reintentar") }
                        OutlinedButton(onClick = onBack) { Text("Volver") }
                    }
                    else -> OutlinedButton(onClick = onBack, modifier = Modifier.initialFocus()) { Text("Cancelar") }
                }
            }
        }
    }
}

@Composable
private fun Controls(status: RemoteStatus?, seek: Int, command: (String, Int) -> Unit, onBack: () -> Unit) {
    val paused = status?.state == "paused"
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { command("previous", 0) }, enabled = status?.hasPrevious != false) { Text("⏮ Anterior") }
            OutlinedButton(onClick = { command("seekBy", -seek) }) { Text("−$seek s") }
            Button(
                onClick = { command("toggle", 0) },
                modifier = Modifier.initialFocus(),
                shape = ButtonDefaults.shape(RoundedCornerShape(28.dp)),
                colors = ButtonDefaults.colors(containerColor = AppColors.Acento, focusedContainerColor = AppColors.Sakura),
            ) { Text(if (paused) "▶  Reanudar" else "⏸  Pausa", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(onClick = { command("seekBy", seek) }) { Text("+$seek s") }
            OutlinedButton(onClick = { command("next", 0) }, enabled = status?.hasNext != false) { Text("Siguiente ⏭") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { command("stop", 0); onBack() }) { Text("⏹ Detener en la TV") }
            OutlinedButton(onClick = onBack) { Text("Volver (sigue en la TV)") }
        }
    }
}

private fun iconOf(kind: String) = when (kind) {
    PlayTarget.KIND_CAST -> "📡"
    PlayTarget.KIND_DLNA -> "🖥"
    else -> "📺"
}

private fun phaseText(state: RemoteUiState, tvName: String, kind: String): String = when (val phase = state.phase) {
    SendPhase.Sending -> "Enviando a $tvName…"
    SendPhase.WaitingApproval -> "Si es la primera vez, acepta este dispositivo en la TV (con el control)."
    SendPhase.Rejected -> "La TV no permitió este dispositivo. Ábrela y vuelve a intentarlo."
    is SendPhase.Failed -> if (kind == PlayTarget.KIND_ANIMEHUB) "${phase.message}. ¿Está AnimeHub abierta en la TV?" else "${phase.message}."
    SendPhase.Connected -> when (state.status?.state) {
        null, "loading" -> if (kind == PlayTarget.KIND_ANIMEHUB) "La TV está buscando un servidor que funcione…" else "Cargando el video en $tvName…"
        "playing" -> "Reproduciendo en la TV"
        "paused" -> "En pausa"
        "finished" -> "Terminó el episodio"
        else -> "Esperando a la TV…"
    }
}
