package com.animehub.tv.remote

import com.animehub.tv.BuildConfig
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.playback.PlaybackController
import com.animehub.tv.playback.PlayerPhase
import com.animehub.tv.ui.navigation.PlayerRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** El reproductor que está abierto en la TV (lo registra el PlayerViewModel). */
@Singleton
class ActivePlayback @Inject constructor() {
    @Volatile var controller: PlaybackController? = null
    @Volatile var animeTitle: String? = null
}

/** Lo que la tablet le pide a la navegación de la TV. */
sealed interface RemoteEvent {
    data class Play(val route: PlayerRoute) : RemoteEvent
    /** Cerrar el reproductor. */
    data object Stop : RemoteEvent
}

/** Puente entre las órdenes de la tablet y el reproductor de la TV. */
@Singleton
class TvPlaybackBridge @Inject constructor(private val active: ActivePlayback) : PlaybackBridge {
    private val _events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RemoteEvent> = _events.asSharedFlow()

    // Los eventos se emiten en el hilo principal: abren o cierran pantallas.
    override suspend fun play(request: PlayRequest) = withContext(Dispatchers.Main) {
        _events.emit(
            RemoteEvent.Play(
                PlayerRoute(
                    siteId = request.siteId, animeUrl = request.animeUrl, animeTitle = request.animeTitle,
                    coverUrl = request.coverUrl, episodeUrl = request.episodeUrl, episodeNumber = request.episodeNumber,
                    startMs = request.startMs,
                ),
            ),
        )
    }

    override suspend fun command(command: RemoteCommand): Boolean = withContext(Dispatchers.Main) {
        if (command.type == "stop") {
            _events.emit(RemoteEvent.Stop)
            return@withContext true
        }
        val controller = active.controller ?: return@withContext false
        when (command.type) {
            "toggle" -> controller.togglePlay()
            "seekBy" -> controller.seekBy(command.seconds)
            "next" -> controller.nextEpisode()
            "previous" -> controller.previousEpisode()
        }
        true
    }

    override fun status(): RemoteStatus {
        val controller = active.controller ?: return RemoteStatus()
        val s = controller.state.value
        val state = when (s.phase) {
            is PlayerPhase.Playing -> if (s.paused) "paused" else "playing"
            PlayerPhase.LoadingServers, is PlayerPhase.ChoosingServer -> "loading"
            is PlayerPhase.NextCountdown, PlayerPhase.Finished -> "finished"
            is PlayerPhase.Failed -> "idle"
        }
        return RemoteStatus(
            state = state,
            title = active.animeTitle,
            episode = s.episode?.number,
            positionMs = s.positionMs,
            durationMs = s.durationMs,
            server = (s.phase as? PlayerPhase.Playing)?.option?.server?.name,
            hasNext = s.hasNext,
            hasPrevious = s.hasPrevious,
        )
    }
}

/**
 * "Enviar a mi TV", lado de la TV: mientras AnimeHub está abierta en la TV, escucha en la red de
 * casa y se anuncia para que la tablet la encuentre. La primera vez que una tablet le envía algo,
 * pregunta en pantalla si se permite ([pairing]).
 */
@Singleton
class TvReceiver @Inject constructor(
    private val settings: SettingsRepository,
    private val identity: DeviceIdentity,
    val bridge: TvPlaybackBridge,
    private val advertiser: ServiceAdvertiser,
) {
    val pairing = Pairing(
        isAllowed = { id -> id in settings.current().allowedRemotes },
        allow = { id -> settings.allowRemote(id) },
    )
    val handler = RemoteHandler(
        info = { DeviceInfo(identity.id(), identity.name, BuildConfig.VERSION_NAME) },
        bridge = bridge,
        pairing = pairing,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: MiniHttpServer? = null

    fun start() {
        if (server?.isRunning == true) return
        val s = MiniHttpServer(handler::handle)
        runCatching { s.start(scope) }.onFailure { return }
        server = s
        scope.launch {
            advertiser.advertise(identity.name, s.port, mapOf(RemoteProtocol.ATTR_ID to identity.id()))
        }
    }

    fun stop() {
        advertiser.withdraw()
        server?.stop()
        server = null
    }
}
