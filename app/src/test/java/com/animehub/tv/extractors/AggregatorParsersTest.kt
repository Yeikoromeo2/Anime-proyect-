package com.animehub.tv.extractors

import com.animehub.tv.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class AggregatorParsersTest {

    @Test
    fun `darkanimes abre sus 5 servidores con la URL descifrada`() {
        val servers = AggregatorParsers.mspXor(Fixtures.read("animeninja/embed_darkanimes_one_piece_1.html"))
        assertEquals(listOf("Mp4Upload", "Mega", "Zilla-networks", "Uns", "Terabox"), servers.map { it.name })
        assertEquals("https://www.mp4upload.com/embed-78kptpyotrle.html", servers[0].embedUrl)
        assertEquals("https://player.zilla-networks.com/m3u8/7601658844858539e438dceee32e7924", servers[2].embedUrl)
        assertEquals("Subtitulado", servers[0].language)
    }

    @Test
    fun `animeav1 separa subtitulado y doblado`() {
        val servers = AggregatorParsers.embedsObject(Fixtures.read("animeninja/embed_animeav1_tank_chair_6.html"))
        assertEquals(8, servers.size)
        assertEquals(listOf("HLS", "Voe", "Byse", "MP4Upload"), servers.filter { it.language == "Subtitulado" }.map { it.name })
        assertEquals(4, servers.count { it.language == "Doblado" })
        assertEquals("https://voe.sx/e/fwraobjpq5fq", servers.first { it.name == "Voe" }.embedUrl)
    }

    @Test
    fun `paginas sin datos no devuelven nada`() {
        assertEquals(emptyList<Any>(), AggregatorParsers.mspXor("<html></html>"))
        assertEquals(emptyList<Any>(), AggregatorParsers.embedsObject("<html></html>"))
    }
}
