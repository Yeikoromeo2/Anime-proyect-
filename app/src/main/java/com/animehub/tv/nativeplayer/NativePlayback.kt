package com.animehub.tv.nativeplayer

import android.content.Context
import android.view.View
import com.animehub.tv.data.settings.PreferredQuality
import com.animehub.tv.domain.model.DirectStream

/**
 * Reproductor nativo para videos directos (HLS o MP4). Es una interfaz para poder simularlo en
 * las pruebas; la implementación real usa Media3/ExoPlayer ([ExoNativePlayback]).
 *
 * Envía a la app los mismos mensajes JSON que el script del reproductor web
 * (`playing`, `pause`, `progress`, `ended`, `error`), así el control de la reproducción es uno solo.
 */
interface NativePlayback {
    /** Vista con el video (null en las pruebas). */
    val view: View?

    fun play(stream: DirectStream)

    /** Las mismas órdenes que el reproductor web: toggle, play, pause, seekBy, seekTo. */
    fun command(json: String)

    fun stop()

    fun release()
}

fun interface NativePlaybackFactory {
    fun create(context: Context, quality: PreferredQuality, onMessage: (String) -> Unit): NativePlayback
}
