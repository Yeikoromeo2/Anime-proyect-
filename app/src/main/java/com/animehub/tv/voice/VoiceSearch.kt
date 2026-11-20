package com.animehub.tv.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Búsqueda por voz con el reconocedor del sistema (en Android TV / Google TV viene incluido). */
object VoiceSearch {
    const val LANGUAGE = "es-419"

    /** Respaldo: la pantalla de voz del sistema (en la TV abre el asistente encima de la app). */
    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el nombre del anime")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /** ¿Hay pantalla de voz del sistema? */
    fun isAvailable(context: Context): Boolean =
        runCatching { context.packageManager.queryIntentActivities(intent(), 0).isNotEmpty() }.getOrDefault(false)

    /** ¿Hay voz de algún tipo (dentro de la app o la pantalla del sistema)? */
    fun anyAvailable(context: Context): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false) || isAvailable(context)

    fun parseResult(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

/** Lo que pasa mientras se escucha (para el panel de voz de la app). */
sealed interface VoiceState {
    data object Idle : VoiceState
    /** @param level volumen de la voz (0–1) para animar el micrófono. */
    data class Listening(val level: Float = 0f, val partial: String = "") : VoiceState
    /** Terminó de hablar: se está reconociendo el texto. */
    data class Thinking(val partial: String = "") : VoiceState
    data class Result(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

/** Una escucha: empieza, informa su estado y se libera al cerrar el panel. */
interface VoiceSession {
    val state: StateFlow<VoiceState>
    fun start()
    fun stop()
    fun destroy()
}

/**
 * Reconocimiento de voz DENTRO de la app (sin abrir la pantalla del asistente de la TV, que tapa
 * la app y hace un "doble pantallazo"). Es una interfaz para poder simularla en las pruebas.
 */
interface VoiceEngine {
    fun isAvailable(context: Context): Boolean
    fun create(context: Context): VoiceSession
}

/** Implementación con [SpeechRecognizer] (el mismo servicio de voz de Google que usa la TV). */
class AndroidVoiceEngine @Inject constructor() : VoiceEngine {
    override fun isAvailable(context: Context): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    override fun create(context: Context): VoiceSession = AndroidVoiceSession(context)
}

private class AndroidVoiceSession(context: Context) : VoiceSession {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var partial = ""

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { _state.value = VoiceState.Listening() }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            // El volumen llega de -2 a 10 dB aproximadamente.
            val current = _state.value
            if (current is VoiceState.Listening) _state.value = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { _state.value = VoiceState.Thinking(partial) }
        override fun onError(error: Int) { _state.value = VoiceState.Error(messageFor(error)) }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            _state.value = if (text.isNotEmpty()) VoiceState.Result(text) else VoiceState.Error(messageFor(SpeechRecognizer.ERROR_NO_MATCH))
        }
        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val current = _state.value
            if (current is VoiceState.Listening) _state.value = current.copy(partial = partial)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun start() {
        partial = ""
        _state.value = VoiceState.Listening()
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(
            VoiceSearch.intent()
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "com.animehub.tv"),
        )
    }

    override fun stop() = recognizer.stopListening()

    override fun destroy() {
        runCatching { recognizer.cancel() }
        recognizer.destroy()
    }

    private fun messageFor(error: Int) = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió. Prueba otra vez."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se escuchó nada. Prueba otra vez."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso del micrófono."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para reconocer la voz."
        SpeechRecognizer.ERROR_AUDIO -> "No se pudo usar el micrófono."
        else -> "No se pudo reconocer la voz."
    }
}
