package com.animehub.tv.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/** Búsqueda por voz con el reconocedor del sistema (en Android TV / Google TV viene incluido). */
object VoiceSearch {
    const val LANGUAGE = "es-419"

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el nombre del anime")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /** Si el dispositivo no tiene reconocimiento de voz, el botón se oculta. */
    fun isAvailable(context: Context): Boolean =
        runCatching { context.packageManager.queryIntentActivities(intent(), 0).isNotEmpty() }.getOrDefault(false)

    fun parseResult(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
