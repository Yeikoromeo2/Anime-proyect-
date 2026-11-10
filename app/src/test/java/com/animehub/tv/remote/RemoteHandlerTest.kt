package com.animehub.tv.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** "Enviar a mi TV" de punta a punta en la JVM: servidor HTTP real en localhost, emparejamiento y órdenes. */
class RemoteHandlerTest {

    private class FakeBridge : PlaybackBridge {
        val played = mutableListOf<PlayRequest>()
        val commands = mutableListOf<RemoteCommand>()
        var playing = false
        override suspend fun play(request: PlayRequest) { played += request; playing = true }
        override suspend fun command(command: RemoteCommand): Boolean { commands += command; return playing }
        override fun status() = RemoteStatus(state = if (playing) "playing" else "idle", title = played.lastOrNull()?.animeTitle, positionMs = 1_000, durationMs = 2_000)
    }

    private val allowed = mutableSetOf<String>()
    private val bridge = FakeBridge()
    private val pairing = Pairing(isAllowed = { it in allowed }, allow = { allowed += it }, timeoutMs = 3_000)
    private val handler = RemoteHandler({ DeviceInfo("tv-1", "TV de la sala", "0.9.0") }, bridge, pairing)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MiniHttpServer(handler::handle)
    private val http = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    @Before
    fun setUp() = server.start(scope)

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private suspend fun call(method: String, path: String, body: String? = null, device: String? = "tablet-1", name: String = "Tablet de Harold") =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("http://127.0.0.1:${server.port}$path")
                .apply { if (device != null) header(RemoteProtocol.HEADER_ID, device).header(RemoteProtocol.HEADER_NAME, RemoteProtocol.encodeName(name)) }
                .method(method, body?.toRequestBody(json))
                .build()
            http.newCall(request).execute().use { it.code to it.body.string() }
        }

    private val play = RemoteProtocol.json.encodeToString(
        PlayRequest.serializer(),
        PlayRequest("animeninja", "https://veranime.ninja/anime/tank-chair/", "Tank Chair", null, "https://veranime.ninja/episodio/tank-chair-1x6/", 6f, 312_000),
    )

    @Test
    fun `info responde con el nombre de la TV`() = runBlocking {
        val (code, body) = call("GET", "/info", device = null)
        assertEquals(200, code)
        assertTrue(body, "TV de la sala" in body)
    }

    @Test
    fun `la primera vez la TV pregunta y al aceptar reproduce y recuerda el dispositivo`() = runBlocking {
        val response = async { call("POST", "/play", play) }
        // La TV muestra la pregunta con el nombre de la tablet (con acentos).
        while (pairing.pending.value == null) delay(20)
        assertEquals("Tablet de Harold", pairing.pending.value!!.deviceName)
        pairing.answer(true)
        assertEquals(200, response.await().first)
        assertEquals(312_000L, bridge.played.single().startMs)
        assertNull(pairing.pending.value)

        // Ya aceptado: no vuelve a preguntar.
        assertEquals(200, call("POST", "/command", """{"type":"toggle"}""").first)
        assertNull(pairing.pending.value)
        assertEquals("toggle", bridge.commands.single().type)
        val (code, status) = call("GET", "/status")
        assertEquals(200, code)
        assertTrue(status, "\"playing\"" in status && "Tank Chair" in status)
    }

    @Test
    fun `si se rechaza o nadie responde la TV no obedece`() = runBlocking {
        val response = async { call("POST", "/play", play) }
        while (pairing.pending.value == null) delay(20)
        pairing.answer(false)
        assertEquals(403, response.await().first)
        assertTrue(bridge.played.isEmpty())
        // Sin respuesta en la TV (3 s en la prueba): tampoco.
        assertEquals(403, call("POST", "/play", play, device = "otra").first)
        // El estado no abre la pregunta para dispositivos desconocidos.
        assertEquals(403, call("GET", "/status", device = "desconocido").first)
        assertNull(pairing.pending.value)
        // Sin identificador: no.
        assertEquals(403, call("POST", "/play", play, device = null).first)
    }

    @Test
    fun `ordenes invalidas o sin nada reproduciendo`() = runBlocking {
        allowed += "tablet-1"
        assertEquals(409, call("POST", "/command", """{"type":"toggle"}""").first)
        assertEquals(400, call("POST", "/command", """{"type":"formatear"}""").first)
        assertEquals(400, call("POST", "/play", "no es json").first)
        assertEquals(404, call("GET", "/otra").first)
        assertEquals(413, call("POST", "/play", "x".repeat(70_000)).first)
    }
}
