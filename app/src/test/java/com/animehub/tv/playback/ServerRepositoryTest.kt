package com.animehub.tv.playback

import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.ServerHealth
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.domain.model.VideoServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ServerRepositoryTest {
    private val server = VideoServer("Voe", "https://voe.example/e/1", "Latino")

    @Test
    fun `los que no responden o estan caidos se descartan`() {
        assertNull(ServerRepository.toOption(server, null, null, null, false)) // no respondió
        assertNull(ServerRepository.toOption(server, ServerHealth.NO_DISPONIBLE, null, "Video borrado", false))
        assertNotNull(ServerRepository.toOption(server, ServerHealth.FUNCIONA, null, null, false))
        assertNotNull(ServerRepository.toOption(server, ServerHealth.SIN_VERIFICAR, null, null, false))
    }

    @Test
    fun `con video directo funciona aunque su pagina no responda`() {
        val stream = DirectStream("https://cdn.example/a.m3u8", StreamType.HLS)
        val option = ServerRepository.toOption(server, null, stream, null, false)
        assertEquals(ServerHealth.FUNCIONA, option?.health)
    }

    @Test
    fun `la preferencia guarda servidor e idioma y puntua mejor lo mas parecido`() {
        val pref = ServerRepository.parsePreferred(ServerRepository.encodePreferred("Voe", "Latino"))
        assertEquals("Voe", pref.name)
        assertEquals("Latino", pref.language)
        assertEquals(2, ServerRepository.preferenceScore(VideoServer("Voe", "u1", "Latino"), pref))
        assertEquals(1, ServerRepository.preferenceScore(VideoServer("Filemoon", "u2", "Latino"), pref)) // mismo idioma
        assertEquals(0, ServerRepository.preferenceScore(VideoServer("Voe", "u3", "Subtitulado"), pref)) // otro idioma
        // Una preferencia antigua (solo el nombre) sigue funcionando.
        val old = ServerRepository.parsePreferred("Voe")
        assertNull(old.language)
        assertEquals(2, ServerRepository.preferenceScore(VideoServer("Voe", "u4", "Subtitulado"), old))
    }
}
