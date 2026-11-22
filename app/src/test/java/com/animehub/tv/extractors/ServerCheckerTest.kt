package com.animehub.tv.extractors

import com.animehub.tv.domain.model.ServerHealth
import com.animehub.tv.domain.model.ServerOption
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.playback.ServerRepository
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ServerCheckerTest {

    private val server = MockWebServer()
    private lateinit var checker: ServerChecker

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.target) {
                "/e/ok" -> MockResponse.Builder().body("<html><video src='x.mp4'></video></html>").build()
                "/e/borrado" -> MockResponse.Builder().body("<h1>File Not Found</h1><p>The file was deleted</p>").build()
                "/e/404" -> MockResponse.Builder().code(404).build()
                "/e/caido" -> MockResponse.Builder().code(522).addHeader("server", "cloudflare").build()
                "/e/cloudflare" -> MockResponse.Builder().code(403).body(Fixtures.read("cloudflare/jkanime_inicio_interactive.html")).build()
                "/hls.m3u8" -> MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl").body("#EXTM3U").build()
                else -> MockResponse.Builder().code(500).build()
            }
        }
        server.start()
        checker = ServerChecker(OkHttpClient(), listOf("file not found", "file was deleted"))
    }

    @After
    fun tearDown() = server.close()

    private fun check(path: String) = runBlocking { checker.check(server.url(path).toString(), "https://veranime.ninja/") }

    @Test
    fun `clasifica servidores`() {
        assertEquals(ServerHealth.FUNCIONA, check("/e/ok").health)
        assertEquals(ServerHealth.FUNCIONA, check("/hls.m3u8").health)
        assertEquals(ServerHealth.NO_DISPONIBLE to "Video borrado", check("/e/borrado").let { it.health to it.detail })
        assertEquals(ServerHealth.NO_DISPONIBLE, check("/e/404").health)
        assertEquals(ServerHealth.NO_DISPONIBLE, check("/e/caido").health)
        assertEquals(ServerHealth.NO_DISPONIBLE, check("/e/500").health)
        assertEquals(ServerHealth.SIN_VERIFICAR, check("/e/cloudflare").health)
    }

    @Test
    fun `mega no se puede comprobar sin abrirlo`() = runBlocking {
        assertEquals(ServerHealth.SIN_VERIFICAR, checker.check("https://mega.nz/embed/abc", null).health)
    }

    @Test
    fun `orden preferido y funcionando primero, no disponibles al final`() {
        fun opt(name: String, h: ServerHealth, pref: Boolean = false) = ServerOption(VideoServer(name, "https://$name"), h, pref)
        val sorted = ServerRepository.sort(
            listOf(
                opt("roto", ServerHealth.NO_DISPONIBLE, pref = true),
                opt("mega", ServerHealth.SIN_VERIFICAR),
                opt("voe", ServerHealth.FUNCIONA),
                opt("mp4upload", ServerHealth.FUNCIONA, pref = true),
            ),
        )
        assertEquals(listOf("mp4upload", "voe", "mega", "roto"), sorted.map { it.server.name })
        assertEquals(true, ServerRepository.sameServer("MP4Upload", "mp4upload"))
    }
}
