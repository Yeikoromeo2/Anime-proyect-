package com.animehub.tv.di

import com.animehub.tv.remote.PlayRequest
import com.animehub.tv.remote.RemoteClient
import com.animehub.tv.remote.RemoteCommand
import com.animehub.tv.remote.RemoteResult
import com.animehub.tv.remote.RemoteStatus
import com.animehub.tv.remote.RemoteTv
import com.animehub.tv.remote.ServiceAdvertiser
import com.animehub.tv.remote.TvFinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Enviar a mi TV" simulado para las pruebas de interfaz de la tablet: TVs encontradas a mano y
 * un cliente que guarda lo que se le pide y devuelve el estado que la prueba elija.
 */
object FakeRemote : TvFinder, RemoteClient {
    val found = MutableStateFlow<List<RemoteTv>>(emptyList())
    override val tvs: StateFlow<List<RemoteTv>> = found
    val played = mutableListOf<PlayRequest>()
    val commands = mutableListOf<RemoteCommand>()
    var status: RemoteStatus = RemoteStatus()

    override fun start() = Unit
    override fun stop() = Unit

    override suspend fun play(tv: RemoteTv, request: PlayRequest): RemoteResult {
        played += request
        status = RemoteStatus(state = "playing", title = request.animeTitle, episode = request.episodeNumber, positionMs = 60_000, durationMs = 1_440_000, server = "HLS", hasNext = false, hasPrevious = true)
        return RemoteResult.Ok
    }

    override suspend fun command(tv: RemoteTv, command: RemoteCommand): RemoteResult {
        commands += command
        if (command.type == "toggle") status = status.copy(state = if (status.state == "paused") "playing" else "paused")
        return RemoteResult.Ok
    }

    override suspend fun status(tv: RemoteTv): RemoteStatus = status

    fun reset() {
        found.value = emptyList()
        played.clear()
        commands.clear()
        status = RemoteStatus()
    }

    /** Anuncio en la red que no hace nada (en Robolectric no hay NSD). */
    val advertiser = object : ServiceAdvertiser {
        override fun advertise(name: String, port: Int, attributes: Map<String, String>) = Unit
        override fun withdraw() = Unit
    }
}
