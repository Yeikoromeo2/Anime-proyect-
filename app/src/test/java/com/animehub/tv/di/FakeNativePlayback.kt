package com.animehub.tv.di

import android.view.View
import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.nativeplayer.NativePlayback
import com.animehub.tv.nativeplayer.NativePlaybackFactory

/**
 * Reproductor nativo simulado para las pruebas de interfaz: guarda lo que se le pide reproducir
 * y deja enviar a la app los mensajes que mandaría ExoPlayer (progreso, fin, error).
 */
object FakeNative {
    val played = mutableListOf<DirectStream>()
    val commands = mutableListOf<String>()
    var stopped = 0
    private var onMessage: ((String) -> Unit)? = null

    val factory = NativePlaybackFactory { _, _, callback ->
        onMessage = callback
        object : NativePlayback {
            override val view: View? = null
            override fun play(stream: DirectStream) { played += stream }
            override fun command(json: String) { commands += json }
            override fun stop() { stopped++ }
            override fun release() = Unit
        }
    }

    /** Mensaje como los del reproductor real: {"type":"progress","pos":312.5,"dur":1440}. */
    fun emit(json: String) = onMessage?.invoke(json)

    fun reset() {
        played.clear()
        commands.clear()
        stopped = 0
        onMessage = null
    }
}
