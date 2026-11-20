package com.animehub.tv.providers.jkanime

import com.animehub.tv.domain.model.AnimeStatus
import com.animehub.tv.domain.model.AnimeType
import com.animehub.tv.domain.model.ShowcaseKind
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.common.ShowcaseParser
import com.animehub.tv.providers.common.SiteSupport
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JKAnime (jkanime.org) contra las páginas guardadas el 2026-11-09. */
class JkanimeParserTest {

    private val site = TestSites.site("jkanime")
    private val support = SiteSupport(site)
    private val parser = JkanimeParser(support)
    private fun html(name: String) = Fixtures.read("jkanime/$name")
    private fun doc(name: String, url: String = "https://jkanime.org/") = Jsoup.parse(html(name), url)

    @Test
    fun `novedades - solo la pestana de animes, con numero, imagen y ficha`() {
        val latest = parser.latest(doc("inicio.html"))
        assertTrue("Novedades: ${latest.size}", latest.size in 10..40)
        with(latest.first()) {
            assertEquals("Kanojo no Tomodachi", animeTitle)
            assertEquals(6f, episodeNumber)
            assertEquals("https://jkanime.org/kanojo-no-tomodachi/6/", episodeUrl)
            assertEquals("https://jkanime.org/kanojo-no-tomodachi/", animeUrl)
            assertTrue(imageUrl!!.startsWith("https://cdn.jkdesa.com/"))
            assertEquals("Ayer", dateLabel)
        }
        // Las pestañas de donghuas, OVAs y adultos no se mezclan con los animes.
        assertTrue(latest.none { it.animeTitle.startsWith("Wangu Zhizun") || it.animeTitle.startsWith("NTR Kishi") })
    }

    @Test
    fun `busqueda con portada en data-setbg y tipo`() {
        val page = parser.search(doc("busqueda_dragon.html"), 1)
        assertEquals(30, page.items.size)
        assertFalse(page.hasNextPage)
        assertTrue(page.items.all { it.coverUrl!!.startsWith("https://cdn.jkdesa.com/assets/images/animes/image/") })
        val onePiece = parser.search(doc("busqueda_one_piece.html"), 1).items.first()
        assertEquals("One Piece", onePiece.title)
        assertEquals("https://jkanime.org/one-piece/", onePiece.url)
        assertEquals(AnimeType.TV, onePiece.type)
    }

    @Test
    fun `directorio con filtros - JSON paginado`() {
        val p1 = parser.directory(html("directorio_accion_2025.html"), 1)
        assertEquals(30, p1.items.size)
        assertTrue(p1.hasNextPage)
        assertEquals("Undead Unluck: Winter-hen", p1.items.first().title)
        assertTrue(p1.items.all { it.url.startsWith("https://jkanime.org/") && it.coverUrl != null })
        val p2 = parser.directory(html("directorio_accion_2025_p2.html"), 2)
        assertEquals("Kaiju Ditan Mai Dali", p2.items.first().title)
        assertTrue(p2.hasNextPage)
        assertTrue(parser.directory(html("directorio.html"), 1).hasNextPage)
    }

    @Test
    fun `filtros reales del directorio, con los anios sin value`() {
        val d = doc("directorio.html", "https://jkanime.org/directorio")
        val genres = parser.selectOptions(d, "select[name=genero]", emptyMap())
        assertEquals("accion", genres.first().value)
        assertEquals("Accion", genres.first().label)
        assertTrue(genres.any { it.value == "isekai" })
        val years = parser.selectOptions(d, "select[name=fecha]", emptyMap())
        assertEquals("2026", years.first().value)
        val types = parser.selectOptions(d, "select[name=tipo]", mapOf("peliculas" to "pelicula"))
        assertEquals(listOf("animes", "peliculas", "especiales", "ovas", "onas"), types.map { it.value })
        assertEquals("pelicula", types[1].canonical)
    }

    @Test
    fun `ficha en emision - episodios del 1 al ultimo publicado`() {
        val detail = parser.detail(doc("anime_tank_chair.html", "https://jkanime.org/tank-chair/"), "https://jkanime.org/tank-chair/")
        assertEquals("Tank Chair", detail.title)
        assertTrue(detail.synopsis!!.startsWith("Shizuka acepta el encargo más peligroso"))
        assertEquals(listOf("Accion"), detail.genres)
        assertEquals(AnimeStatus.EMISION, detail.status)
        assertEquals(AnimeType.TV, detail.type)
        assertEquals(2026, detail.year)
        assertEquals(6, detail.episodes.size)
        assertEquals("https://jkanime.org/tank-chair/6/", detail.episodes.last().url)
        assertEquals("https://cdn.jkdesa.com/assets/images/animes/image/tank-chair.jpg", detail.coverUrl)

        val onePiece = parser.detail(doc("anime_one_piece.html", "https://jkanime.org/one-piece/"), "https://jkanime.org/one-piece/")
        assertEquals(1180, onePiece.episodes.size)
        assertEquals(1999, onePiece.year)
    }

    @Test
    fun `ficha finalizada - usa el numero de episodios`() {
        val detail = parser.detail(doc("anime_moyashimon.html", "https://jkanime.org/moyashimon/"), "https://jkanime.org/moyashimon/")
        assertEquals(AnimeStatus.FINALIZADO, detail.status)
        assertEquals(11, detail.episodes.size)
        assertEquals(2007, detail.year)
    }

    @Test
    fun `servidores propios y externos, sin los de descarga`() {
        val servers = parser.servers(doc("episodio_tank_chair_6.html", "https://jkanime.org/tank-chair/6/"))
        assertEquals(
            listOf("Desu", "Magi", "Mega", "Streamwish", "Vidhide", "VOE", "Filemoon", "Mixdrop", "Streamtape", "Doodstream"),
            servers.map { it.name },
        )
        assertTrue(servers[0].embedUrl.startsWith("https://jkanime.org/jkplayer/um?e="))
        assertEquals("https://voe.sx/e/28jgxswmgfca", servers.first { it.name == "VOE" }.embedUrl)
        assertTrue(servers.all { it.language == "Subtitulado" })
        assertTrue(servers.none { it.name == "Mediafire" })
    }

    @Test
    fun `portada - recomendados con resena y animes recientes`() = runTest {
        val items = ShowcaseParser(support).load(site.config.showcase, "jkanime") { doc("inicio.html") }
        val recommended = items.filter { it.kind == ShowcaseKind.RECOMENDADO }
        val new = items.filter { it.kind == ShowcaseKind.NUEVO }
        assertEquals("Dragon Ball Super: Beerus", recommended.first().card.title)
        assertEquals("https://jkanime.org/dragon-ball-super-beerus/", recommended.first().card.url)
        assertTrue(recommended.first().synopsis!!.startsWith("Dragon Ball Super: Beerus, un proyecto"))
        assertTrue(recommended.first().card.coverUrl!!.startsWith("https://cdn.jkdesa.com/"))
        // El carrusel repite las diapositivas: una sola vez cada una.
        assertEquals(recommended.map { it.card.url }.distinct().size, recommended.size)
        assertEquals("Dominion", new.first().card.title)
        assertEquals(1988, new.first().card.year)
        assertNull(new.first().synopsis)
    }

    @Test
    fun `miniaturas de episodios - clave, id y carpeta del CDN como hace la web`() {
        val doc = Jsoup.parse(Fixtures.read("jkanime/anime_tank_chair.html"), "https://jkanime.org/tank-chair/")
        val api = parser.episodesApi(doc)!!
        assertEquals("4988", api.animeId)
        assertTrue(api.token.length > 20)
        assertEquals("https://cdn.jkdesa.com/assets/images/animes/video/image_thumb/", api.thumbBase)

        // Respuesta real de /ajax/episodes/4988/1 (16 por página).
        val thumbs = parser.thumbnails(Fixtures.read("jkanime/ajax_episodios_tank_chair.json"), api.thumbBase)
        assertEquals(6, thumbs.size)
        assertEquals("https://cdn.jkdesa.com/assets/images/animes/video/image_thumb/jkvideo_4505b39c84cd467f9b4717a3d62d6a4e.jpg", thumbs[1f])
        assertTrue(parser.thumbnails("no es json", api.thumbBase).isEmpty())
    }
}
