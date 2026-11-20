package com.animehub.tv.providers.dooplay

import com.animehub.tv.domain.model.AnimeType
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.common.SiteSupport
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anime Ninja (DooPlay) contra las páginas guardadas en la Fase 0. */
class DooPlayParserTest {

    private val parser = DooPlayParser(SiteSupport(TestSites.site("animeninja")))
    private fun doc(name: String, url: String = "https://veranime.ninja/") = Jsoup.parse(Fixtures.read("animeninja/$name"), url)

    @Test
    fun `novedades con titulo, numero, imagen y enlace a la ficha`() {
        val latest = parser.latest(doc("inicio.html"))
        assertEquals(30, latest.size)
        val first = latest.first()
        assertEquals("Mouse Cursor de Genjitsu wo Sousa Dekiru You ni Natta node, Onna no Ko wo Ippai Click Shimaasu", first.animeTitle)
        assertEquals(6f, first.episodeNumber)
        assertEquals("https://veranime.ninja/ver/mouse-cursor-de-genjitsu-wo-sousa-dekiru-you-ni-natta-node-onna-no-ko-wo-ippai-click-shimaasu-episodio-6/", first.episodeUrl)
        assertEquals("https://veranime.ninja/anime/mouse-cursor-de-genjitsu-wo-sousa-dekiru-you-ni-natta-node-onna-no-ko-wo-ippai-click-shimaasu/", first.animeUrl)
        assertEquals("https://image.tmdb.org/t/p/w342/nge8GsnWnUn6lAcOs5rlDWNoA9e.jpg", first.imageUrl)
        assertEquals("Sin Censura", first.language)
        assertTrue(latest.all { it.imageUrl!!.startsWith("https://image.tmdb.org/") && it.episodeNumber != null })
    }

    @Test
    fun `busqueda devuelve portada grande, tipo y anio`() {
        val result = parser.search(doc("busqueda_one_piece.html"), page = 1)
        val card = result.items.single()
        assertEquals("One Piece", card.title)
        assertEquals("https://veranime.ninja/anime/one-piece/", card.url)
        assertEquals("https://image.tmdb.org/t/p/w342/1g9xCTVrwAwszsdQpLi0lY8dX2a.jpg", card.coverUrl)
        assertEquals(AnimeType.TV, card.type)
        assertEquals(1999, card.year)
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `busqueda paginada detecta si hay pagina siguiente`() {
        val p1 = parser.search(doc("busqueda_dragon.html"), page = 1)
        assertEquals(30, p1.items.size)
        assertTrue(p1.hasNextPage)
        assertTrue(p1.items.any { it.type == AnimeType.PELICULA })
        val p2 = parser.search(doc("busqueda_dragon_p2.html"), page = 2)
        assertEquals(19, p2.items.size)
        assertFalse(p2.hasNextPage)
        assertTrue((p1.items + p2.items).all { it.coverUrl != null })
    }

    @Test
    fun `archivos de genero y peliculas sin carruseles`() {
        val genre = parser.archive(doc("genero_accion.html"), page = 1)
        assertEquals(30, genre.items.size)
        assertTrue(genre.hasNextPage)
        assertTrue(genre.items.all { it.coverUrl!!.contains("/w342/") })
        val movies = parser.archive(doc("directorio_peliculas.html"), page = 1)
        assertEquals(30, movies.items.size)
        assertTrue(movies.items.all { it.type == AnimeType.PELICULA })
    }

    @Test
    fun `ficha de One Piece con 23 temporadas y 1180 episodios`() {
        val detail = parser.detail(doc("anime_one_piece.html"), "https://veranime.ninja/anime/one-piece/")
        assertEquals("One Piece", detail.title)
        assertEquals("https://image.tmdb.org/t/p/w342/1g9xCTVrwAwszsdQpLi0lY8dX2a.jpg", detail.coverUrl)
        assertTrue(detail.synopsis!!.startsWith("Esta historia se sitúa"))
        assertTrue("Accion" in detail.genres)
        assertEquals(1999, detail.year)
        assertEquals(1180, detail.episodes.size)
        assertEquals(23, detail.episodes.mapNotNull { it.season }.distinct().size)
        val first = detail.episodes.first()
        assertEquals(1f, first.number)
        assertEquals(1, first.season)
        assertEquals("https://veranime.ninja/ver/one-piece-episodio-1/", first.url)
        assertNotNull(first.imageUrl)
    }

    @Test
    fun `ficha corta y pelicula`() {
        val tank = parser.detail(doc("anime_tank_chair.html"), "https://veranime.ninja/anime/tank-chair/")
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f), tank.episodes.map { it.number })
        assertEquals(AnimeType.TV, tank.type)

        val akira = parser.detail(doc("pelicula.html"), "https://veranime.ninja/pelicula/akira/")
        assertEquals("Akira", akira.title)
        assertEquals(AnimeType.PELICULA, akira.type)
        assertEquals(1988, akira.year)
        assertEquals(listOf("https://veranime.ninja/pelicula/akira/"), akira.episodes.map { it.url })
    }

    @Test
    fun `opciones de reproductor sin el trailer`() {
        val episode = parser.playerOptions(doc("episodio_tank_chair_6.html"))
        assertEquals(listOf(DooPlayOption("MULTISERVER animeav1.com", "70455", "1", "tv")), episode)
        val movie = parser.playerOptions(doc("pelicula.html"))
        assertEquals(listOf("1"), movie.map { it.nume })
        assertEquals("movie", movie.single().type)
    }

    @Test
    fun `generos leidos del menu`() {
        val genres = parser.genres(doc("directorio_anime.html"), "a[href*='/genero/']")
        assertEquals(61, genres.size)
        assertTrue(genres.any { it.value == "accion" && it.label == "Accion" })
    }

    @Test
    fun `fechas en ingles de DooPlay`() {
        assertNotNull(DooPlayParser.parseEnglishDate("Jul. 19, 2026"))
        assertEquals(DooPlayParser.parseEnglishDate("Oct. 20, 1999"), DooPlayParser.parseEnglishDate("October 20, 1999"))
    }
}
