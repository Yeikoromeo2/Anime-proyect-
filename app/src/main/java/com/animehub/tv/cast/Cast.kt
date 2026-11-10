package com.animehub.tv.cast

import android.content.Context
import android.net.Uri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.animehub.tv.remote.CastDevice
import com.animehub.tv.remote.DeviceDiscovery
import com.animehub.tv.remote.MediaRenderer
import com.animehub.tv.remote.PlayTarget
import com.animehub.tv.remote.RemoteResult
import com.animehub.tv.remote.RendererStatus
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.common.images.WebImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Google Cast, si el aparato tiene Google Play Services (las tablets sí; en las pruebas no hay). */
@Suppress("DEPRECATION")
internal fun castContext(context: Context): CastContext? = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

/** Busca Chromecasts y TVs con Chromecast integrado en la red (desde la tablet). */
@Singleton
class CastDiscovery @Inject constructor(@ApplicationContext private val context: Context) : DeviceDiscovery {
    private val _devices = MutableStateFlow<List<PlayTarget>>(emptyList())
    override val devices: StateFlow<List<PlayTarget>> = _devices.asStateFlow()
    private var router: MediaRouter? = null
    private var selector: MediaRouteSelector? = null

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    override fun start() {
        val cast = castContext(context) ?: return
        val sel = cast.mergedSelector ?: return
        val r = MediaRouter.getInstance(context)
        router = r
        selector = sel
        r.addCallback(sel, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
    }

    override fun stop() {
        router?.removeCallback(callback)
        _devices.value = emptyList()
    }

    /** Conecta con ese Chromecast (Google Cast abre la sesión al elegir la ruta). */
    fun select(id: String): Boolean {
        val r = router ?: MediaRouter.getInstance(context).also { router = it }
        val route = r.routes.firstOrNull { it.id == id } ?: return false
        r.selectRoute(route)
        return true
    }

    private fun refresh() {
        val r = router ?: return
        val sel = selector ?: return
        _devices.value = r.routes
            .filter { !it.isDefault && it.isEnabled && it.matchesSelector(sel) }
            .map { CastDevice(it.id, it.name) }
    }
}

/** Reproduce en un Chromecast con el receptor estándar (la dirección viene del intermediario de la tablet). */
class CastRenderer(
    private val context: Context,
    private val routeId: String,
    private val discovery: CastDiscovery,
) : MediaRenderer {

    override suspend fun load(
        url: String, mime: String, title: String, subtitle: String, imageUrl: String?, startMs: Long, fragmentedMp4: Boolean,
    ): RemoteResult = withContext(Dispatchers.Main) {
        val cast = castContext(context) ?: return@withContext RemoteResult.Failed("Este aparato no tiene Google Play Services para Chromecast")
        val session = connectedSession(cast) ?: run {
            discovery.select(routeId)
            waitForSession(cast)
        } ?: return@withContext RemoteResult.Failed("No se pudo conectar con el Chromecast")
        val client = session.remoteMediaClient ?: return@withContext RemoteResult.Failed("El Chromecast no acepta videos ahora")
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, subtitle)
            imageUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mime)
            .setMetadata(metadata)
            .apply { if (fragmentedMp4) setHlsVideoSegmentFormat(HlsVideoSegmentFormat.FMP4) }
            .build()
        val request = MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(startMs).build()
        val ok = withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont -> client.load(request).setResultCallback { cont.resume(it.status.isSuccess) } }
        } ?: false
        if (ok) RemoteResult.Ok else RemoteResult.Failed("El Chromecast no pudo abrir el video")
    }

    override suspend fun toggle(): Boolean = withClient { it.togglePlayback(); true } ?: false

    override suspend fun seekBy(seconds: Int): Boolean = withClient { client ->
        val target = (client.approximateStreamPosition + seconds * 1000L).coerceAtLeast(0)
        client.seek(MediaSeekOptions.Builder().setPosition(target).build())
        true
    } ?: false

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            castContext(context)?.sessionManager?.endCurrentSession(true)
        }
    }

    override suspend fun status(): RendererStatus? = withClient { client ->
        val state = when (client.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> "playing"
            MediaStatus.PLAYER_STATE_PAUSED -> "paused"
            MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> "loading"
            else -> "idle"
        }
        RendererStatus(state, client.approximateStreamPosition, client.streamDuration.coerceAtLeast(0))
    }

    private suspend fun <T> withClient(block: (com.google.android.gms.cast.framework.media.RemoteMediaClient) -> T): T? =
        withContext(Dispatchers.Main) {
            val client = castContext(context)?.let(::connectedSession)?.remoteMediaClient ?: return@withContext null
            runCatching { block(client) }.getOrNull()
        }

    private fun connectedSession(cast: CastContext): CastSession? = cast.sessionManager.currentCastSession?.takeIf { it.isConnected }

    private suspend fun waitForSession(cast: CastContext): CastSession? {
        repeat(40) {
            connectedSession(cast)?.let { return it }
            delay(500)
        }
        return null
    }
}
