package com.animehub.tv.ui.search

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animehub.tv.voice.VoiceEngine
import com.animehub.tv.voice.VoiceSearch
import com.animehub.tv.voice.VoiceSession
import com.animehub.tv.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow

/** Voz lista para usar: [start] empieza a escuchar al instante y [Panel] es lo que se ve mientras tanto. */
class VoiceUi(val available: Boolean, val start: () -> Unit, val panel: @Composable () -> Unit)

/**
 * Búsqueda por voz dentro de la app: al pulsar el micrófono escucha de inmediato (con el panel de
 * la mascota) y entrega lo entendido en [onResult]. La primera vez pide el permiso del micrófono;
 * si no hay servicio de voz o se niega, usa la pantalla de voz del sistema.
 */
@Composable
fun rememberVoiceUi(engine: VoiceEngine, onResult: (String) -> Unit): VoiceUi {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val inApp = remember { engine.isAvailable(context) }
    val available = remember { inApp || VoiceSearch.isAvailable(context) }

    val systemVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) VoiceSearch.parseResult(r.data)?.let { result(it) }
    }
    val openSystemVoice = { try { systemVoice.launch(VoiceSearch.intent()) } catch (_: ActivityNotFoundException) { } }

    var session by remember { mutableStateOf<VoiceSession?>(null) }
    DisposableEffect(Unit) { onDispose { session?.destroy() } }
    var open by remember { mutableStateOf(false) }
    val listen = {
        val current = session ?: engine.create(context).also { session = it }
        open = true
        current.start()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else openSystemVoice()
    }
    val start: () -> Unit = {
        when {
            !inApp -> openSystemVoice()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val idle = remember { MutableStateFlow<VoiceState>(VoiceState.Idle) }
    val state by (session?.state ?: idle).collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        val done = state as? VoiceState.Result ?: return@LaunchedEffect
        open = false
        result(done.text)
    }
    return VoiceUi(available, start) {
        if (open && state !is VoiceState.Result) {
            VoicePanel(state, onCancel = { session?.stop(); open = false }, onRetry = listen)
        }
    }
}
