package com.animehub.tv.playback

import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.ServerHealth
import com.animehub.tv.domain.model.ServerOption
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.domain.model.WatchProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.StreamType
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {

    private val episodes = (1..3).map { Episode(it.toFloat(), "Episodio $it", "https://sitio/ver/x-episodio-$it") }
    private fun opt(name: String, health: ServerHealth = ServerHealth.FUNCIONA, preferred: Boolean = false) =
        ServerOption(VideoServer(name, "https://$name.example/e/1"), health, preferred)

    private class Saved(val ep: Float, val pos: Long, val dur: Long, val completed: Boolean, val server: String?)

    private fun TestScope.controller(
        options: List<ServerOption>,
        saved: WatchProgress? = null,
        startIndex: Int = 0,
        saves: MutableList<Saved> = mutableListOf(),
        preferred: MutableList<String> = mutableListOf(),
    ) = PlaybackController(
        scope = this,
        episodes = episodes,
        startIndex = startIndex,
        loadServers = { options },
        savedProgress = { saved },
        saveProgress = { ep, s, pos, dur, done -> saves += Saved(ep.number, pos, dur, done, s?.server?.name) },
        rememberPreferred = { preferred += it },
        watchdogMs = 25_000,
        countdownSeconds = 10,
        clock = { testScheduler.currentTime },
    )

    private fun progress(pos: Double, dur: Double, paused: Boolean = false) = """{"type":"progress","pos":$pos,"dur":$dur,"paused":$paused}"""

    @Test
    fun `con servidor preferido que funciona reproduce directo`() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(listOf(opt("voe"), opt("mp4upload", preferred = true)))
        val commands = mutableListOf<PlayerCommand>()
        val job = launch { c.commands.toList(commands) }
        c.start()
        runCurrent()
        assertEquals("mp4upload", (c.state.value.phase as PlayerPhase.Playing).option.server.name)
        assertEquals(PlayerCommand.Load("https://mp4upload.example/e/1"), commands.first())
        job.cancel()
        c.onExit()
    }

    @Test
    fun `sin preferido muestra solo la lista y al elegir lo recuerda`() = runTest(UnconfinedTestDispatcher()) {
        val preferred = mutableListOf<String>()
        val c = controller(listOf(opt("voe"), opt("roto", ServerHealth.NO_DISPONIBLE)), preferred = preferred)
        c.start()
        runCurrent()
        val phase = c.state.value.phase as PlayerPhase.ChoosingServer
        c.choose(phase.options.first())
        runCurrent()
        assertEquals(listOf("voe"), preferred)
        assertTrue(c.state.value.phase is PlayerPhase.Playing)
        c.onExit()
    }

    @Test
    fun `si no arranca en 25 s prueba el siguiente servidor y salta los caidos`() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(listOf(opt("a", preferred = true), opt("roto", ServerHealth.NO_DISPONIBLE), opt("b", ServerHealth.SIN_VERIFICAR)))
        c.start()
        runCurrent()
        advanceTimeBy(25_001)
        val playing = c.state.value.phase as PlayerPhase.Playing
        assertEquals("b", playing.option.server.name)
        assertTrue(c.state.value.notice!!.contains("a no respondió; probando b"))
        advanceTimeBy(25_001)
        assertTrue(c.state.value.phase is PlayerPhase.ChoosingServer)
        assertTrue(c.state.value.notice!!.startsWith("Ningún servidor respondió"))
    }

    @Test
    fun `retoma 5 s antes del minuto guardado y guarda cada 5 s`() = runTest(UnconfinedTestDispatcher()) {
        val saved = WatchProgress("s", "a", "A", null, 1f, episodes[0].url, "voe", 600_000, 1_440_000, 1, false)
        val saves = mutableListOf<Saved>()
        val c = controller(listOf(opt("voe", preferred = true)), saved = saved, saves = saves)
        val commands = mutableListOf<PlayerCommand>()
        val job = launch { c.commands.toList(commands) }
        c.start()
        runCurrent()
        c.onMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false}""")
        assertEquals(PlayerCommand.Script("""{"type":"seekTo","seconds":595.0}"""), commands.last())
        assertEquals("Continuando en 9:55", c.state.value.notice)
        c.onMessage(progress(596.0, 1440.0))
        advanceTimeBy(5_000)
        c.onMessage(progress(601.0, 1440.0))
        assertEquals(listOf(596_000L, 601_000L), saves.map { it.pos })
        assertEquals("voe", saves.last().server)
        job.cancel()
        c.onExit()
    }

    @Test
    fun `al terminar cuenta 10 s y pasa solo al siguiente episodio`() = runTest(UnconfinedTestDispatcher()) {
        val saves = mutableListOf<Saved>()
        val c = controller(listOf(opt("voe", preferred = true)), saves = saves)
        c.start()
        runCurrent()
        c.onMessage(progress(1.0, 1440.0))
        c.onMessage("""{"type":"ended"}""")
        val countdown = c.state.value.phase as PlayerPhase.NextCountdown
        assertEquals(2f, countdown.next.number)
        assertEquals(10, countdown.secondsLeft)
        assertTrue(saves.last().completed)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(2f, c.state.value.episode!!.number)
        assertTrue(c.state.value.phase is PlayerPhase.Playing)
        c.onExit()
    }

    @Test
    fun `cerca del final cuenta como terminado y en el ultimo episodio no hay siguiente`() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(listOf(opt("voe", preferred = true)), startIndex = 2)
        c.start()
        runCurrent()
        assertTrue(!c.state.value.hasNext)
        c.onMessage(progress(1439.0, 1440.0))
        assertTrue(c.state.value.phase is PlayerPhase.Finished)
    }

    @Test
    fun `cancelar la cuenta atras`() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(listOf(opt("voe", preferred = true)))
        c.start()
        runCurrent()
        c.onMessage(progress(1.0, 1440.0))
        c.onMessage("""{"type":"ended"}""")
        c.cancelNext()
        advanceTimeBy(15_000)
        assertEquals(1f, c.state.value.episode!!.number)
        assertTrue(c.state.value.phase is PlayerPhase.Finished)
    }

    @Test
    fun `quitar recuadros manda la orden y avisa cuando el servidor pregunta si continuar`() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(listOf(opt("voe", preferred = true)))
        val commands = mutableListOf<PlayerCommand>()
        val job = launch { c.commands.toList(commands) }
        c.start()
        runCurrent()
        c.onMessage("""{"type":"prompt","accepted":true,"text":"¿Continuar viendo desde 05:12?"}""")
        assertEquals("Se cerró el aviso del servidor", c.state.value.notice)
        c.cleanupOverlays()
        assertEquals(PlayerCommand.Script("""{"type":"cleanup"}"""), commands.last())
        c.onMessage("""{"type":"cleanup","hidden":1}""")
        assertEquals("Se quitaron los recuadros que tapaban el video", c.state.value.notice)
        job.cancel()
        c.onExit()
    }

    private fun native(name: String, preferred: Boolean = false) = ServerOption(
        VideoServer(name, "https://$name.example/e/1"), ServerHealth.FUNCIONA, preferred,
        stream = DirectStream("https://$name.example/video.m3u8", StreamType.HLS, mapOf("Referer" to "https://$name.example/")),
    )

    @Test
    fun `con video directo usa el reproductor nativo y si falla abre la pagina del mismo servidor`() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(listOf(native("zilla", preferred = true), opt("voe")))
        val commands = mutableListOf<PlayerCommand>()
        val job = launch { c.commands.toList(commands) }
        c.start()
        runCurrent()
        assertEquals(PlayerCommand.PlayNative(DirectStream("https://zilla.example/video.m3u8", StreamType.HLS, mapOf("Referer" to "https://zilla.example/"))), commands.last())
        assertTrue(c.state.value.nativePlayback)
        // El nativo no pudo: mismo servidor en el reproductor web (no salta a Voe).
        c.onMessage("""{"type":"error","code":2004,"frame":"native"}""")
        assertEquals(PlayerCommand.Load("https://zilla.example/e/1"), commands.last())
        assertFalse(c.state.value.nativePlayback)
        assertTrue(c.state.value.notice!!.contains("usando el reproductor web"))
        // Si la página tampoco arranca, entonces sí pasa al siguiente servidor.
        advanceTimeBy(25_001)
        assertEquals(PlayerCommand.Load("https://voe.example/e/1"), commands.last())
        job.cancel()
        c.onExit()
    }

    @Test
    fun `si el nativo falla a mitad sigue en la web desde el mismo minuto`() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(listOf(native("zilla", preferred = true)))
        val commands = mutableListOf<PlayerCommand>()
        val job = launch { c.commands.toList(commands) }
        c.start()
        runCurrent()
        c.onMessage(progress(600.0, 1440.0))
        c.onMessage("""{"type":"error","code":2001,"frame":"native"}""")
        assertEquals(PlayerCommand.Load("https://zilla.example/e/1"), commands.last())
        // La página arranca: salta al minuto donde iba.
        c.onMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false}""")
        assertEquals(PlayerCommand.Script("""{"type":"seekTo","seconds":600.0}"""), commands.last())
        job.cancel()
        c.onExit()
    }
}
