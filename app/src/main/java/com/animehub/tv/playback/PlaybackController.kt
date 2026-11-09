package com.animehub.tv.playback

import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.ServerHealth
import com.animehub.tv.domain.model.ServerOption
import com.animehub.tv.domain.model.WatchProgress
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Qué está haciendo el reproductor. */
sealed interface PlayerPhase {
    data object LoadingServers : PlayerPhase
    data class ChoosingServer(val options: List<ServerOption>) : PlayerPhase
    data class Playing(val option: ServerOption) : PlayerPhase
    /** Terminó el episodio: cuenta atrás para el siguiente. */
    data class NextCountdown(val next: Episode, val secondsLeft: Int) : PlayerPhase
    /** Último episodio visto (o se canceló el siguiente). */
    data object Finished : PlayerPhase
    data class Failed(val message: String) : PlayerPhase
}

data class PlayerState(
    val episode: Episode? = null,
    val phase: PlayerPhase = PlayerPhase.LoadingServers,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val paused: Boolean = false,
    /** Aviso breve ("Probando Voe…", "Continuando en 12:30"). */
    val notice: String? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** Servidores del episodio actual (para "Cambiar servidor"). */
    val options: List<ServerOption> = emptyList(),
    /** Se está usando el reproductor nativo (video directo) en vez de la página del servidor. */
    val nativePlayback: Boolean = false,
)

/** Órdenes para el reproductor (web o nativo). */
sealed interface PlayerCommand {
    /** Abrir la página del servidor en el reproductor web. */
    data class Load(val url: String) : PlayerCommand
    /** Reproducir el video directo en el reproductor nativo. */
    data class PlayNative(val stream: DirectStream) : PlayerCommand
    /** JSON para window.__animehubCmd: {"type":"toggle"}, {"type":"seekBy","seconds":10}… */
    data class Script(val json: String) : PlayerCommand
    data object Stop : PlayerCommand
}

/**
 * Lógica del reproductor sin dependencias de Android (se prueba en la JVM):
 * elegir servidor (preferido o el primero que funciona), retomar 5 s antes del minuto guardado,
 * guardar el progreso, cambiar de servidor si el video no arranca y pasar al siguiente episodio.
 */
class PlaybackController(
    private val scope: CoroutineScope,
    private val episodes: List<Episode>,
    startIndex: Int,
    private val loadServers: suspend (Episode) -> List<ServerOption>,
    private val savedProgress: suspend (Episode) -> WatchProgress?,
    private val saveProgress: suspend (Episode, ServerOption?, positionMs: Long, durationMs: Long, completed: Boolean) -> Unit,
    private val rememberPreferred: suspend (String) -> Unit,
    private val watchdogMs: Long = 25_000,
    private val countdownSeconds: Int = 10,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var index = startIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _commands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<PlayerCommand> = _commands.asSharedFlow()

    private var current: ServerOption? = null
    private val tried = mutableSetOf<String>()
    /** Servidores cuyo video directo falló: se usan con el reproductor web. */
    private val nativeBroken = mutableSetOf<String>()
    private var native = false
    private var started = false
    private var resumeAtMs: Long? = null
    private var lastSave = -SAVE_EVERY_MS
    private var ended = false
    private var watchdog: Job? = null
    private var countdown: Job? = null
    private var loadJob: Job? = null

    val episode: Episode? get() = episodes.getOrNull(index)

    /** Arranca el episodio actual: si hay servidor preferido que no está caído, reproduce directo. */
    fun start() {
        val ep = episode ?: run { _state.update { it.copy(phase = PlayerPhase.Failed("Este anime no tiene episodios")) }; return }
        countdown?.cancel()
        watchdog?.cancel()
        tried.clear()
        current = null
        started = false
        ended = false
        lastSave = -SAVE_EVERY_MS
        _state.update {
            PlayerState(episode = ep, phase = PlayerPhase.LoadingServers, hasNext = index < episodes.lastIndex, hasPrevious = index > 0)
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val saved = runCatching { savedProgress(ep) }.getOrNull()
            resumeAtMs = saved?.takeIf { !it.completed && it.positionMs > RESUME_MIN_MS }?.let { (it.positionMs - RESUME_BACK_MS).coerceAtLeast(0) }
            val options = runCatching { loadServers(ep) }.getOrElse { e ->
                _state.update { it.copy(phase = PlayerPhase.Failed(e.message ?: "No se pudieron leer los servidores")) }
                return@launch
            }
            _state.update { it.copy(options = options) }
            val usable = options.filter { it.health != ServerHealth.NO_DISPONIBLE }
            val auto = usable.firstOrNull { it.preferred }
            when {
                options.isEmpty() -> _state.update { it.copy(phase = PlayerPhase.Failed("No se encontraron servidores para este episodio")) }
                auto != null -> play(auto, notice = "Servidor preferido: ${auto.server.name} · ↑ para cambiar")
                else -> _state.update { it.copy(phase = PlayerPhase.ChoosingServer(options)) }
            }
        }
    }

    /** El usuario eligió un servidor: se recuerda como preferido para este sitio. */
    fun choose(option: ServerOption) {
        scope.launch { runCatching { rememberPreferred(option.server.name) } }
        play(option)
    }

    fun showServers() {
        watchdog?.cancel()
        _state.update { it.copy(phase = PlayerPhase.ChoosingServer(it.options), notice = null) }
        _commands.tryEmit(PlayerCommand.Stop)
    }

    private fun play(option: ServerOption, notice: String? = null) {
        current = option
        tried += option.server.embedUrl
        started = false
        val stream = option.stream?.takeIf { option.server.embedUrl !in nativeBroken }
        native = stream != null
        _state.update {
            it.copy(phase = PlayerPhase.Playing(option), notice = notice, positionMs = 0, durationMs = 0, paused = false, nativePlayback = native)
        }
        _commands.tryEmit(if (stream != null) PlayerCommand.PlayNative(stream) else PlayerCommand.Load(option.server.embedUrl))
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(watchdogMs)
            if (!started) {
                if (native) nativeFailed("no arrancó") else serverFailed("no respondió")
            }
        }
    }

    /**
     * El video directo falló: el mismo servidor se abre en el reproductor web (con su página),
     * retomando donde iba si ya se estaba viendo.
     */
    private fun nativeFailed(reason: String) {
        val option = current ?: return
        nativeBroken += option.server.embedUrl
        if (started && _state.value.positionMs > RESUME_MIN_MS) resumeAtMs = _state.value.positionMs
        play(option, notice = "El reproductor nativo $reason con ${option.server.name}; usando el reproductor web")
    }

    /** Si el video no arranca o da error, se prueba el siguiente servidor que no esté caído. */
    private fun serverFailed(reason: String) {
        val failed = current ?: return
        watchdog?.cancel()
        val next = _state.value.options.firstOrNull { it.health != ServerHealth.NO_DISPONIBLE && it.server.embedUrl !in tried }
        if (next != null) {
            play(next, notice = "${failed.server.name} $reason; probando ${next.server.name}…")
        } else {
            _commands.tryEmit(PlayerCommand.Stop)
            _state.update { it.copy(phase = PlayerPhase.ChoosingServer(it.options), notice = "Ningún servidor respondió. Elige otro o vuelve más tarde.") }
        }
    }

    /** Mensajes del script inyectado (JSON). */
    fun onMessage(raw: String) {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val type = obj["type"]?.jsonPrimitive?.content ?: return
        val pos = obj["pos"]?.jsonPrimitive?.doubleOrNull?.let { (it * 1000).toLong() }
        val dur = obj["dur"]?.jsonPrimitive?.doubleOrNull?.let { (it * 1000).toLong() }
        val paused = obj["paused"]?.jsonPrimitive?.booleanOrNull
        if (_state.value.phase !is PlayerPhase.Playing) return
        when (type) {
            "playing", "progress", "status", "loadedmetadata", "pause" -> {
                if (type == "playing" || (type == "progress" && paused == false)) markStarted()
                _state.update {
                    it.copy(positionMs = pos ?: it.positionMs, durationMs = dur?.takeIf { d -> d > 0 } ?: it.durationMs, paused = paused ?: it.paused)
                }
                val s = _state.value
                // Solo progreso real: "playing" llega con 0:00 antes de saltar al minuto guardado
                // y no debe borrar el progreso anterior.
                if ((type == "progress" || type == "pause") && (type == "pause" || clock() - lastSave >= SAVE_EVERY_MS)) save()
                // Algunos reproductores no avisan del final: a 1,5 s del final cuenta como terminado.
                if (s.durationMs > MIN_EPISODE_MS && s.positionMs >= s.durationMs - END_MARGIN_MS) onEnded()
            }
            "ended" -> onEnded()
            "error" -> when {
                native -> nativeFailed("dio un error")
                !started -> serverFailed("dio un error")
            }
            // El servidor preguntó si continuar donde se dejó: el script ya respondió por el usuario.
            "prompt" -> _state.update { it.copy(notice = "Se cerró el aviso del servidor") }
            "cleanup" -> if ((obj["hidden"]?.jsonPrimitive?.intOrNull ?: 0) > 0) {
                _state.update { it.copy(notice = "Se quitaron los recuadros que tapaban el video") }
            }
        }
    }

    private fun markStarted() {
        if (started) return
        started = true
        watchdog?.cancel()
        resumeAtMs?.let { ms ->
            resumeAtMs = null
            seekTo(ms)
            _state.update { it.copy(notice = "Continuando en ${formatTime(ms)}") }
        }
    }

    private fun onEnded() {
        if (ended) return
        ended = true
        val s = _state.value
        val ep = episode ?: return
        scope.launch { saveProgress(ep, current, s.durationMs, s.durationMs, true) }
        val next = episodes.getOrNull(index + 1)
        if (next == null) {
            _state.update { it.copy(phase = PlayerPhase.Finished, notice = "Último episodio disponible") }
            return
        }
        countdown?.cancel()
        countdown = scope.launch {
            for (left in countdownSeconds downTo 1) {
                _state.update { it.copy(phase = PlayerPhase.NextCountdown(next, left)) }
                delay(1000)
            }
            nextEpisode()
        }
    }

    fun cancelNext() {
        countdown?.cancel()
        _state.update { it.copy(phase = PlayerPhase.Finished) }
    }

    fun nextEpisode() {
        if (index >= episodes.lastIndex) return
        saveIfPlaying()
        index++
        start()
    }

    fun previousEpisode() {
        if (index <= 0) return
        saveIfPlaying()
        index--
        start()
    }

    fun togglePlay() = script("""{"type":"toggle"}""")

    /** Quita los recuadros o capas que tapan el video (menú ↑ → "Quitar recuadros"). */
    fun cleanupOverlays() {
        script("""{"type":"cleanup"}""")
        _state.update { it.copy(notice = "Quitando recuadros…") }
    }
    fun seekBy(seconds: Int) = script("""{"type":"seekBy","seconds":$seconds}""")
    private fun seekTo(ms: Long) = script("""{"type":"seekTo","seconds":${ms / 1000.0}}""")

    private fun script(json: String) {
        if (_state.value.phase is PlayerPhase.Playing) _commands.tryEmit(PlayerCommand.Script(json))
    }

    /** Al salir: guardar el minuto exacto. */
    fun onExit() {
        saveIfPlaying()
        countdown?.cancel()
        watchdog?.cancel()
    }

    private fun saveIfPlaying() {
        if (started && !ended) save()
    }

    private fun save() {
        val ep = episode ?: return
        val s = _state.value
        if (s.durationMs <= 0 || s.positionMs < MIN_SAVE_POSITION_MS) return
        lastSave = clock()
        scope.launch { saveProgress(ep, current, s.positionMs, s.durationMs, WatchProgress.isCompleted(s.positionMs, s.durationMs)) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        const val SAVE_EVERY_MS = 5_000L
        const val MIN_SAVE_POSITION_MS = 1_000L
        const val RESUME_BACK_MS = 5_000L
        const val RESUME_MIN_MS = 30_000L
        const val END_MARGIN_MS = 1_500L
        const val MIN_EPISODE_MS = 120_000L

        fun formatTime(ms: Long): String {
            val t = ms / 1000
            return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
        }
    }
}
