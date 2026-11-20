package com.animehub.tv.remote

import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.EpisodePager
import com.animehub.tv.domain.model.ServerHealth
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.playback.ServerRepository
import com.animehub.tv.providers.ProviderRegistry
import com.animehub.tv.stream.StreamProxy
import com.animehub.tv.ui.navigation.RemoteRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dónde se puede reproducir desde la tablet: una TV con AnimeHub, un Chromecast o una TV DLNA
 * (Samsung, LG…).
 */
sealed interface PlayTarget {
    val id: String
    val name: String
    val kind: String
    /** Texto del botón en "Ver en:". */
    val label: String

    companion object {
        const val KIND_ANIMEHUB = "animehub"
        const val KIND_CAST = "cast"
        const val KIND_DLNA = "dlna"
    }
}

/** Un Chromecast o TV con Chromecast integrado. */
data class CastDevice(override val id: String, override val name: String) : PlayTarget {
    override val kind get() = PlayTarget.KIND_CAST
    override val label get() = "📡 $name"
}

/** Una TV con DLNA (renderizador UPnP): Samsung, LG y muchas otras. */
data class DlnaDevice(override val id: String, override val name: String, val controlUrl: String) : PlayTarget {
    override val kind get() = PlayTarget.KIND_DLNA
    override val label get() = "🖥 $name"
}

/** Busca dispositivos de un tipo en la red (Chromecast, DLNA). */
interface DeviceDiscovery {
    val devices: StateFlow<List<PlayTarget>>
    fun start()
    fun stop()
}

/** Todos los destinos de la red en una sola lista (TVs con AnimeHub primero). */
@Singleton
class TargetDirectory @Inject constructor(
    private val finder: TvFinder,
    private val discoveries: Set<@JvmSuppressWildcards DeviceDiscovery>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val targets: StateFlow<List<PlayTarget>> =
        combine(listOf(finder.tvs) + discoveries.map { it.devices }) { lists -> lists.flatMap { it } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun start() {
        finder.start()
        discoveries.forEach { runCatching { it.start() } }
    }

    fun stop() {
        finder.stop()
        discoveries.forEach { runCatching { it.stop() } }
    }
}

/** Una reproducción en un destino: enviar, manejar y saber cómo va. */
interface TargetSession {
    suspend fun play(request: PlayRequest): RemoteResult
    suspend fun command(command: RemoteCommand): RemoteResult
    suspend fun status(): RemoteStatus?
}

/** TV con AnimeHub: la TV hace todo (busca servidor, bloquea anuncios…). */
class AnimeHubSession(private val client: RemoteClient, private val tv: RemoteTv) : TargetSession {
    override suspend fun play(request: PlayRequest) = client.play(tv, request)
    override suspend fun command(command: RemoteCommand) = client.command(tv, command)
    override suspend fun status() = client.status(tv)
}

/** Estado de un Chromecast o una TV DLNA. @param state "playing", "paused", "loading", "idle". */
data class RendererStatus(val state: String, val positionMs: Long = 0, val durationMs: Long = 0)

/** Chromecast o TV DLNA: reproducen una dirección de video; no saben nada de servidores ni anuncios. */
interface MediaRenderer {
    suspend fun load(url: String, mime: String, title: String, subtitle: String, imageUrl: String?, startMs: Long, fragmentedMp4: Boolean): RemoteResult
    suspend fun toggle(): Boolean
    suspend fun seekBy(seconds: Int): Boolean
    suspend fun stop()
    suspend fun status(): RendererStatus?
}

/** Crea el reproductor de un Chromecast o una TV DLNA (las pruebas lo simulan). */
fun interface RendererFactory {
    fun create(route: RemoteRoute): MediaRenderer?
}

/**
 * Busca el video directo de un episodio (como el reproductor nativo de la TV) y los episodios
 * vecinos para "anterior" y "siguiente".
 */
class DirectStreamResolver @Inject constructor(
    private val registry: ProviderRegistry,
    private val servers: ServerRepository,
    private val http: OkHttpClient,
) {
    data class Resolved(val stream: DirectStream, val serverName: String, val fragmentedMp4: Boolean)

    suspend fun resolve(request: PlayRequest): Resolved? = withContext(Dispatchers.IO) {
        val provider = registry.provider(request.siteId) ?: return@withContext null
        val options = runCatching { servers.serversFor(provider, request.episodeUrl) }.getOrNull() ?: return@withContext null
        val option = options.firstOrNull { it.stream != null && it.health != ServerHealth.NO_DISPONIBLE } ?: return@withContext null
        val stream = option.stream!!
        Resolved(stream, option.server.name, stream.type == StreamType.HLS && usesFragmentedMp4(stream))
    }

    suspend fun neighbor(request: PlayRequest, delta: Int): PlayRequest? = withContext(Dispatchers.IO) {
        val provider = registry.provider(request.siteId) ?: return@withContext null
        val episodes = runCatching { provider.animeDetail(request.animeUrl).episodes }.getOrNull() ?: return@withContext null
        val index = episodes.indexOfFirst { EpisodePager.sameUrl(it.url, request.episodeUrl) }
        val next = episodes.getOrNull(index + delta)?.takeIf { index >= 0 } ?: return@withContext null
        request.copy(episodeUrl = next.url, episodeNumber = next.number, startMs = 0)
    }

    /** Las listas con EXT-X-MAP usan trozos MP4 (el receptor de Chromecast necesita saberlo). */
    private fun usesFragmentedMp4(stream: DirectStream): Boolean = runCatching {
        fun get(url: String) = http.newCall(Request.Builder().url(url).apply { stream.headers.forEach { (k, v) -> header(k, v) } }.build())
            .execute().use { it.body.string() }
        var text = get(stream.url)
        if ("#EXT-X-STREAM-INF" in text) {
            val variant = text.lineSequence().map { it.trim() }.first { it.isNotEmpty() && !it.startsWith("#") }
            text = get(URI(stream.url).resolve(variant).toString())
        }
        "#EXT-X-MAP" in text
    }.getOrDefault(false)
}

/**
 * Chromecast o TV DLNA: la tablet busca el video directo del episodio, lo publica con el
 * intermediario ([StreamProxy], que pone las cabeceras que exige el servidor) y se lo pasa al
 * dispositivo. "Anterior" y "siguiente" repiten lo mismo con el episodio vecino.
 */
class DirectPlaybackSession(
    private val renderer: MediaRenderer,
    private val resolver: DirectStreamResolver,
    private val proxy: StreamProxy,
) : TargetSession {
    private var current: PlayRequest? = null
    private var serverName: String? = null

    override suspend fun play(request: PlayRequest): RemoteResult {
        val resolved = resolver.resolve(request)
            ?: return RemoteResult.Failed("Este episodio no tiene un servidor con video directo (Chromecast y las TV DLNA solo reproducen video directo)")
        val url = proxy.publish(resolved.stream)
            ?: return RemoteResult.Failed("La tablet no está conectada a la red Wi-Fi de casa")
        val mime = if (resolved.stream.type == StreamType.HLS) "application/x-mpegURL" else "video/mp4"
        val result = renderer.load(
            url, mime, request.animeTitle, "Episodio ${com.animehub.tv.util.Format.episode(request.episodeNumber)}",
            request.coverUrl, request.startMs, resolved.fragmentedMp4,
        )
        if (result == RemoteResult.Ok) {
            current = request
            serverName = resolved.serverName
        }
        return result
    }

    override suspend fun command(command: RemoteCommand): RemoteResult {
        val playing = current ?: return RemoteResult.NothingPlaying
        return when (command.type) {
            "toggle" -> if (renderer.toggle()) RemoteResult.Ok else RemoteResult.Failed("No respondió")
            "seekBy" -> if (renderer.seekBy(command.seconds)) RemoteResult.Ok else RemoteResult.Failed("No respondió")
            "next", "previous" -> resolver.neighbor(playing, if (command.type == "next") 1 else -1)?.let { play(it) }
                ?: RemoteResult.Failed(if (command.type == "next") "No hay episodio siguiente" else "No hay episodio anterior")
            "stop" -> {
                renderer.stop()
                current = null
                RemoteResult.Ok
            }
            else -> RemoteResult.Failed("Orden desconocida")
        }
    }

    override suspend fun status(): RemoteStatus? {
        val playing = current ?: return RemoteStatus()
        val s = renderer.status() ?: return null
        return RemoteStatus(
            state = s.state, title = playing.animeTitle, episode = playing.episodeNumber,
            positionMs = s.positionMs, durationMs = s.durationMs, server = serverName,
            hasNext = true, hasPrevious = playing.episodeNumber > 1f,
        )
    }
}

/** Crea la sesión adecuada para el destino elegido. */
@Singleton
class TargetSessionFactory @Inject constructor(
    private val client: RemoteClient,
    private val resolver: DirectStreamResolver,
    private val proxy: StreamProxy,
    private val renderers: RendererFactory,
) {
    fun create(route: RemoteRoute): TargetSession? = when (route.kind) {
        PlayTarget.KIND_CAST, PlayTarget.KIND_DLNA -> renderers.create(route)?.let { DirectPlaybackSession(it, resolver, proxy) }
        else -> AnimeHubSession(client, RemoteTv(route.tvId, route.tvName, route.tvHost, route.tvPort))
    }
}
