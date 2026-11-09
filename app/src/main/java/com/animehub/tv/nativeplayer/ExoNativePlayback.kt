package com.animehub.tv.nativeplayer

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.animehub.tv.data.settings.PreferredQuality
import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.StreamType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Reproductor nativo con Media3/ExoPlayer. Usa el mismo cliente HTTP de la app (User-Agent,
 * "no rastrear") más las cabeceras que pide cada servidor de video (Referer, Origin).
 * La calidad máxima sale de Ajustes; en "Automática" se adapta a la conexión.
 */
@OptIn(UnstableApi::class)
class ExoNativePlayback(
    context: Context,
    client: OkHttpClient,
    quality: PreferredQuality,
    private val onMessage: (String) -> Unit,
) : NativePlayback {

    private val dataSources = OkHttpDataSource.Factory(client)
    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters().apply {
                when (quality) {
                    PreferredQuality.Q1080 -> setMaxVideoSize(1920, 1080)
                    PreferredQuality.Q720 -> setMaxVideoSize(1280, 720)
                    PreferredQuality.Q480 -> setMaxVideoSize(854, 480)
                    PreferredQuality.AUTO -> Unit
                }
            },
        )
    }
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
        .build()

    override val view: View = PlayerView(context).apply {
        useController = false // los controles son los de la app (barra de información, menú ↑)
        player = this@ExoNativePlayback.player
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(Color.BLACK)
        setBackgroundColor(Color.BLACK)
        keepScreenOn = true
        isFocusable = false
        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (player.isPlaying) send("progress")
            handler.postDelayed(this, PROGRESS_EVERY_MS)
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Una pausa por carga (buffering) no es una pausa del usuario.
                if (isPlaying) send("playing") else if (!player.playWhenReady) send("pause")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> send("loadedmetadata")
                    Player.STATE_ENDED -> send("ended")
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onMessage("""{"type":"error","code":${error.errorCode},"frame":"native"}""")
            }
        })
        handler.postDelayed(ticker, PROGRESS_EVERY_MS)
    }

    override fun play(stream: DirectStream) {
        dataSources.setDefaultRequestProperties(stream.headers)
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .setMimeType(if (stream.type == StreamType.HLS) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    override fun command(json: String) {
        val cmd = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        val seconds = cmd["seconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        when (cmd["type"]?.jsonPrimitive?.content) {
            "toggle" -> player.playWhenReady = !player.playWhenReady
            "play" -> player.playWhenReady = true
            "pause" -> player.playWhenReady = false
            "seekBy" -> player.seekTo((player.currentPosition + (seconds * 1000).toLong()).coerceAtLeast(0))
            "seekTo" -> player.seekTo((seconds * 1000).toLong().coerceAtLeast(0))
            else -> return // "cleanup" y otras: no aplican al reproductor nativo
        }
        send("status")
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun release() {
        handler.removeCallbacks(ticker)
        player.release()
    }

    private fun send(type: String) {
        val pos = player.currentPosition.coerceAtLeast(0) / 1000.0
        val dur = player.duration.takeIf { it > 0 }?.div(1000.0) ?: 0.0
        onMessage("""{"type":"$type","pos":$pos,"dur":$dur,"paused":${!player.playWhenReady},"frame":"native"}""")
    }

    private companion object {
        const val PROGRESS_EVERY_MS = 5_000L
    }
}

/**
 * Crea el reproductor nativo con el cliente HTTP de la app, ajustado para video: sin tiempo
 * total por petición (un MP4 se descarga mientras se ve) y sin guardar el video en la caché.
 */
class ExoNativePlaybackFactory @Inject constructor(client: OkHttpClient) : NativePlaybackFactory {
    private val videoClient = client.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cache(null)
        .build()

    override fun create(context: Context, quality: PreferredQuality, onMessage: (String) -> Unit): NativePlayback =
        ExoNativePlayback(context, videoClient, quality, onMessage)
}
