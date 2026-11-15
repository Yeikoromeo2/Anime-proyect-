package com.animehub.tv.data.iptv

import com.animehub.tv.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private fun entries(text: String) = M3uParser.parseEntries(text)

    @Test
    fun `lee nombre, logo, categoria y direccion`() {
        val e = entries(Fixtures.read("iptv/pe_inicio.m3u"))
        assertTrue(e.size >= 20)
        val first = e.first()
        assertEquals("36 TV Huacho (720p)", first.name)
        assertEquals("General", first.group)
        assertTrue(first.url.endsWith("playlist.m3u8"))
        assertTrue(first.logo.orEmpty().startsWith("https://"))
    }

    @Test
    fun `quita calidad pais y etiquetas del nombre y detecta las lineas`() {
        assertEquals("Latina", M3uParser.baseName("Latina (720p) [Not 24/7]"))
        assertEquals("Kan 11", M3uParser.baseName("Kan 11 ✪ | IL"))
        assertEquals("Canal Cinco", M3uParser.baseName("Canal Cinco HD"))
        assertEquals(1080, M3uParser.linesOf("A&E Latin America (1080p)"))
        assertEquals(720, M3uParser.linesOf("Canal Cinco HD"))
        assertEquals(1080, M3uParser.linesOf("Canal Cinco FHD"))
        assertEquals(0, M3uParser.linesOf("Rewind"))
    }

    @Test
    fun `un canal repetido se copia una sola vez y las calidades distintas se conservan`() {
        val a = """
            #EXTM3U
            #EXTINF:-1 group-title="News",Canal Uno (720p)
            http://a.example/uno720.m3u8
            #EXTINF:-1 group-title="News",Canal Uno (1080p)
            https://a.example/uno1080.m3u8
            #EXTINF:-1 group-title="News",Canal Uno (720p)
            https://b.example/uno720-seguro.m3u8
            #EXTINF:-1 group-title="Sports",Deportes Dos
            https://a.example/dos.m3u8
        """.trimIndent()
        val b = """
            #EXTM3U
            #EXTINF:-1 group-title="News",Canal Uno HD
            https://c.example/uno-otra-lista.m3u8
            #EXTINF:-1 group-title="News",Canal Uno SD
            https://c.example/uno-sd.m3u8
            #EXTINF:-1 group-title="Music",Radio Tres
            https://c.example/tres.m3u8
        """.trimIndent()
        val lists = M3uParser.build(listOf(Triple("a", "A", entries(a)), Triple("b", "B", entries(b))))
        val uno = lists[0].channels.single { it.name == "Canal Uno" }
        // 1080 + 720 (el https reemplazó al http) + SD que llegó de la otra lista; la otra "HD" era repetida.
        assertEquals(listOf(1080, 720, 480), uno.variants.map { it.lines })
        assertEquals("https://b.example/uno720-seguro.m3u8", uno.variants[1].url)
        assertEquals(listOf("Full HD", "HD", "SD"), uno.qualities)
        // En la segunda lista ya no vuelve a aparecer; solo lo que no estaba.
        assertEquals(listOf("Radio Tres"), lists[1].channels.map { it.name })
        assertEquals("Noticias", uno.group)
        assertEquals("Música", lists[1].channels.single().group)
    }

    @Test
    fun `sin categoria agrupa por pais y descarta lo para adultos`() {
        val lists = M3uParser.build(listOf(Triple("mundo", "Mundo", entries(Fixtures.read("iptv/mundo_inicio.m3u")))))
        val groups = lists.single().groups.map { it.first }
        assertTrue("países: $groups", groups.contains("Chile") && groups.contains("Israel"))
        val adult = M3uParser.build(listOf(Triple("x", "X", entries("#EXTM3U\n#EXTINF:-1 group-title=\"XXX\",Para adultos\nhttps://x.example/a.m3u8"))))
        assertTrue(adult.single().channels.isEmpty())
    }

    @Test
    fun `listas de peliculas y capitulos conservan cada titulo`() {
        val ghibli = M3uParser.build(listOf(Triple("ghibli", "Studio Ghibli", entries(Fixtures.read("iptv/ghibli.m3u")))), onDemandIds = setOf("ghibli")).single()
        assertEquals(23, ghibli.channels.size)
        assertTrue(ghibli.onDemand && ghibli.channels.all { it.onDemand && it.group == "Películas" })
        val cartoons = M3uParser.build(listOf(Triple("dibujos", "Dibujos", entries(Fixtures.read("iptv/dibujos_inicio.m3u")))), onDemandIds = setOf("dibujos")).single()
        assertTrue(cartoons.groups.first().first.startsWith("Las aventuras de Tintin"))
        assertTrue(cartoons.channels.first().best.url.endsWith(".mp4"))
    }
}
