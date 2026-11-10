package com.animehub.tv.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Lo que la TV hace con lo que pide la tablet (en la app: navegar al reproductor y manejarlo). */
interface PlaybackBridge {
    suspend fun play(request: PlayRequest)
    /** false si no se está reproduciendo nada. */
    suspend fun command(command: RemoteCommand): Boolean
    fun status(): RemoteStatus
}

/**
 * Emparejamiento: la primera vez que un dispositivo quiere controlar la TV, la TV pregunta
 * "¿Permitir…?" y espera la respuesta (como mucho [timeoutMs]). Los aceptados se recuerdan.
 */
class Pairing(
    private val isAllowed: suspend (deviceId: String) -> Boolean,
    private val allow: suspend (deviceId: String) -> Unit,
    private val timeoutMs: Long = 45_000,
) {
    data class Request(val deviceId: String, val deviceName: String)

    private val _pending = MutableStateFlow<Request?>(null)
    /** Pregunta en pantalla (null = ninguna). */
    val pending: StateFlow<Request?> = _pending.asStateFlow()
    private var decision: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    suspend fun isKnown(deviceId: String) = isAllowed(deviceId)

    /** true si el dispositivo ya estaba permitido o el usuario lo acepta ahora. */
    suspend fun authorize(deviceId: String, deviceName: String): Boolean {
        if (isAllowed(deviceId)) return true
        return mutex.withLock {
            if (isAllowed(deviceId)) return@withLock true
            val answer = CompletableDeferred<Boolean>()
            decision = answer
            _pending.value = Request(deviceId, deviceName)
            val accepted = withTimeoutOrNull(timeoutMs) { answer.await() } ?: false
            _pending.value = null
            decision = null
            if (accepted) allow(deviceId)
            accepted
        }
    }

    /** Respuesta del usuario en la TV. */
    fun answer(accept: Boolean) {
        decision?.complete(accept)
    }
}

/** Atiende las peticiones de la tablet (ver [RemoteProtocol]). */
class RemoteHandler(
    private val info: suspend () -> DeviceInfo,
    private val bridge: PlaybackBridge,
    private val pairing: Pairing,
) {
    private val json = RemoteProtocol.json

    suspend fun handle(request: HttpRequest): HttpResponse {
        val deviceId = request.header(RemoteProtocol.HEADER_ID)
        val deviceName = RemoteProtocol.decodeName(request.header(RemoteProtocol.HEADER_NAME)) ?: "Dispositivo sin nombre"
        return when (request.method to request.path) {
            "GET" to "/info" -> HttpResponse.json(200, json.encodeToString(DeviceInfo.serializer(), info()))
            "POST" to "/play" -> {
                if (deviceId.isNullOrBlank() || !pairing.authorize(deviceId, deviceName)) return forbidden()
                val play = runCatching { json.decodeFromString(PlayRequest.serializer(), request.bodyText) }.getOrNull()
                    ?: return HttpResponse.json(400, """{"error":"Pedido inválido"}""")
                bridge.play(play)
                HttpResponse.json(200, """{"ok":true}""")
            }
            "POST" to "/command" -> {
                if (deviceId.isNullOrBlank() || !pairing.authorize(deviceId, deviceName)) return forbidden()
                val command = runCatching { json.decodeFromString(RemoteCommand.serializer(), request.bodyText) }.getOrNull()
                    ?: return HttpResponse.json(400, """{"error":"Orden inválida"}""")
                if (command.type !in COMMANDS) return HttpResponse.json(400, """{"error":"Orden desconocida"}""")
                if (bridge.command(command)) HttpResponse.json(200, """{"ok":true}""")
                else HttpResponse.json(409, """{"error":"La TV no está reproduciendo nada"}""")
            }
            // El estado no abre la pregunta: solo para dispositivos ya aceptados.
            "GET" to "/status" -> {
                if (deviceId.isNullOrBlank() || !pairing.isKnown(deviceId)) return forbidden()
                HttpResponse.json(200, json.encodeToString(RemoteStatus.serializer(), bridge.status()))
            }
            else -> HttpResponse.json(404, """{"error":"No existe"}""")
        }
    }

    private fun forbidden() = HttpResponse.json(403, """{"error":"La TV no permitió este dispositivo"}""")

    private companion object {
        val COMMANDS = setOf("toggle", "seekBy", "next", "previous", "stop")
    }
}
