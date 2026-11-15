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
        val text = """
            #EXTM3U
            #EXTINF:-1 group-title="News",Canal Uno (720p)
            http://a.example/uno720.m3u8
            #EXTINF:-1 group-title="News",Canal Uno (1080p)
            https://a.example/uno1080.m3u8
            #EXTINF:-1 group-title="News",Canal Uno (720p)
            https://b.example/uno720-seguro.m3u8
            #EXTINF:-1 group-title="Music",Canal Uno HD
            https://c.example/uno-en-otra-categoria.m3u8
            #EXTINF:-1 group-title="News",Canal Uno SD
            https://c.example/uno-sd.m3u8
            #EXTINF:-1 group-title="Sports",Deportes Dos
            https://a.example/dos.m3u8
        """.trimIndent()
        val list = M3uParser.buildList("l", "L", entries(text))
        // Lista "en vivo": un canal una sola vez, con una señal por calidad (el https reemplazó al http).
        val uno = list.channels.single { it.name == "Canal Uno" }
        assertEquals(listOf(1080, 720, 480), uno.variants.map { it.lines })
        assertEquals("https://b.example/uno720-seguro.m3u8", uno.variants[1].url)
        assertEquals(listOf("Full HD", "HD", "SD"), uno.qualities)
        assertEquals("Noticias", uno.group)
        assertEquals(2, list.channels.size)
    }

    @Test
    fun `una lista general repite el canal en cada submenu pero una sola vez en cada uno`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 group-title="Peru",Canal Uno
            https://a.example/uno.m3u8
            #EXTINF:-1 group-title="Peru",Canal Uno
            https://b.example/uno-repetido.m3u8
            #EXTINF:-1 group-title="Chile",Canal Uno
            https://c.example/uno-chile.m3u8
            #EXTINF:-1 group-title="Spain",Otro Canal
            https://c.example/otro.m3u8
        """.trimIndent()
        val list = M3uParser.buildList("pais", "Por país", entries(text), kind = "grouped", order = listOf("España", "Perú"), translate = "country")
        assertEquals(listOf("España", "Perú", "Chile"), list.groups.map { it.first }) // primero el orden pedido, luego alfabético
        assertEquals(1, list.groups.first { it.first == "Perú" }.second.size)
        assertEquals(3, list.channels.size) // Uno en Perú, Uno en Chile y Otro en España
    }

    @Test
    fun `traduce idiomas y deja las direcciones de archive org estables`() {
        val list = M3uParser.buildList("i", "I", entries("#EXTM3U\n#EXTINF:-1 group-title=\"Spanish\",Uno\nhttps://a.example/u.m3u8"), kind = "grouped", translate = "language")
        assertEquals("Español", list.channels.single().group)
        assertEquals(
            "https://archive.org/download/Peliculas-wii/Cuentos%20de%20Terramar.mp4",
            M3uParser.stableUrl("http://ia802200.us.archive.org/26/items/Peliculas-wii/Cuentos%20de%20Terramar.mp4"),
        )
        assertEquals("https://archive.org/download/x/y.mp4", M3uParser.stableUrl("https://archive.org/download/x/y.mp4"))
    }

    @Test
    fun `sin categoria agrupa por pais y descarta lo para adultos`() {
        val list = M3uParser.buildList("mundo", "Mundo", entries(Fixtures.read("iptv/mundo_inicio.m3u")))
        val groups = list.groups.map { it.first }
        assertTrue("países: $groups", groups.contains("Chile") && groups.contains("Israel"))
        val adult = M3uParser.buildList("x", "X", entries("#EXTM3U\n#EXTINF:-1 group-title=\"XXX\",Para adultos\nhttps://x.example/a.m3u8"))
        assertTrue(adult.channels.isEmpty())
    }

    @Test
    fun `listas de peliculas y capitulos conservan cada titulo`() {
        val cartoons = M3uParser.buildList("dibujos", "Dibujos", entries(Fixtures.read("iptv/dibujos_inicio.m3u")), kind = "vod")
        assertTrue(cartoons.onDemand && cartoons.channels.all { it.onDemand })
        assertTrue(cartoons.groups.first().first.startsWith("Las aventuras de Tintin"))
        assertTrue(cartoons.channels.first().best.url.endsWith(".mp4"))
    }
}
