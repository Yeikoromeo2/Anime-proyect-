package com.animehub.tv.providers.animeflvwp

import com.animehub.tv.domain.model.ShowcaseKind
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.common.ShowcaseParser
import com.animehub.tv.providers.common.SiteSupport
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** animeflv.or.at contra las páginas guardadas el 2026-11-09. */
class AnimeFlvWpParserTest {

    private val site = TestSites.site("animeflv")
    private val support = SiteSupport(site)
    private val parser = AnimeFlvWpParser(support)
    private fun doc(name: String, url: String = "https://animeflv.or.at/") = Jsoup.parse(Fixtures.read("animeflv/$name"), url)

    @Test
    fun `novedades - titulo sin el numero y ficha desde la categoria`() {
        val latest = parser.latest(doc("inicio.html"))
        assertEquals(24, latest.size)
        with(latest.first()) {
            assertEquals("Dark Summoner to Dekiteiru", animeTitle)
            assertEquals(6f, episodeNumber)
            assertEquals("https://animeflv.or.at/2026/11/08/dark-summoner-to-dekiteiru-episodio-6/", episodeUrl)
            assertEquals("https://animeflv.or.at/anime/dark-summoner-to-dekiteiru/", animeUrl)
            assertTrue(imageUrl!!.startsWith("https://animeflv.or.at/wp-content/uploads/"))
        }
        val tank = latest.first { it.animeTitle == "Tank Chair" }
        assertEquals("https://animeflv.or.at/anime/tank-chair/", tank.animeUrl)
    }

    @Test
    fun `busqueda y catalogo completo con paginas`() {
        val dragon = parser.search(doc("busqueda_dragon.html"), 1)
        assertEquals(listOf("Dragon Ball Super: Beerus"), dragon.items.map { it.title })
        assertEquals("https://animeflv.or.at/anime/dragon-ball-super-beerus/", dragon.items.first().url)
        assertFalse(dragon.hasNextPage)
        val catalog = parser.catalog(doc("anime_lista.html"), 1)
        assertEquals(18, catalog.items.size)
        assertEquals("Demons' Crest", catalog.items.first().title)
        assertTrue(catalog.items.all { it.coverUrl != null })
        assertTrue(catalog.hasNextPage)
        assertEquals(11, parser.catalog(doc("anime_lista_p11.html"), 11).items.size)
    }

    @Test
    fun `ficha con sinopsis, generos y episodios del JSON`() {
        val detail = parser.detail(doc("anime_tank_chair.html", "https://animeflv.or.at/anime/tank-chair/"), "https://animeflv.or.at/anime/tank-chair/")
        assertEquals("Tank Chair", detail.title)
        assertTrue(detail.synopsis!!.startsWith("Shizuka acepta los trabajos de asesinato"))
        assertEquals(listOf("Acción", "Gore", "Superpoderes"), detail.genres)
        assertEquals((1..6).map { it.toFloat() }, detail.episodes.map { it.number })
        assertEquals("https://animeflv.or.at/2026/10/04/tank-chair-episodio-1/", detail.episodes.first().url)
        assertEquals("https://animeflv.or.at/wp-content/uploads/2026/10/4463.jpg", detail.coverUrl)
        // La copia solo tiene los episodios recientes de One Piece.
        val onePiece = parser.detail(doc("anime_one_piece.html", "https://animeflv.or.at/anime/one-piece/"), "https://animeflv.or.at/anime/one-piece/")
        assertEquals(1167f, onePiece.episodes.first().number)
        assertEquals(1180f, onePiece.episodes.last().number)
    }

    @Test
    fun `servidores en Base64 con su nombre e idioma`() {
        val servers = parser.servers(doc("episodio_tank_chair_6.html", "https://animeflv.or.at/2026/11/08/tank-chair-episodio-6/"))
        assertEquals(listOf("HLS", "Voe", "Byse", "MP4Upload"), servers.map { it.name })
        assertEquals("https://player.zilla-networks.com/m3u8/fa543590d3399084aa0f1dc73ee1ccac", servers.first().embedUrl)
        assertTrue(servers.all { it.language == "Subtitulado" })
    }

    @Test
    fun `portada - animes recientes con resena`() = runTest {
        val items = ShowcaseParser(support).load(site.config.showcase, "animeflv") { doc("inicio.html") }
        assertTrue(items.all { it.kind == ShowcaseKind.NUEVO })
        with(items.first()) {
            assertEquals("Dark Summoner to Dekiteiru", card.title)
            assertEquals("https://animeflv.or.at/anime/dark-summoner-to-dekiteiru/", card.url)
            assertTrue(synopsis!!.startsWith("Amona, una poderosa invocadora oscura"))
        }
    }
}
