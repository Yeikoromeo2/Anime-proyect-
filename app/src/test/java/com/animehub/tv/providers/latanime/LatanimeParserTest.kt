package com.animehub.tv.providers.latanime

import com.animehub.tv.domain.model.AnimeStatus
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.common.SiteSupport
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Latanime contra las páginas guardadas en la Fase 0. */
class LatanimeParserTest {

    private val parser = LatanimeParser(SiteSupport(TestSites.site("latanime")))
    private fun doc(name: String) = Jsoup.parse(Fixtures.read("latanime/$name"), "https://latanime.org/")

    @Test
    fun `novedades separan numero y titulo`() {
        val latest = parser.latest(doc("inicio.html"))
        assertEquals(36, latest.size)
        val first = latest.first()
        assertEquals("Tensei Kizoku, Kantei Skill de Nariagaru S1 Latino", first.animeTitle)
        assertEquals(12f, first.episodeNumber)
        assertEquals("https://latanime.org/ver/as-a-reincarnated-aristocrat-ill-use-my-appraisal-skill-to-rise-in-the-new-world-episodio-12", first.episodeUrl)
        assertEquals("https://latanime.org/anime/as-a-reincarnated-aristocrat-ill-use-my-appraisal-skill-to-rise-in-the-new-world", first.animeUrl)
        assertTrue(first.imageUrl!!.startsWith("https://latanime.org/thumbs/portada/"))
        assertEquals("Latino", first.language)
        assertEquals("08 Nov 2026", first.dateLabel)
    }

    @Test
    fun `directorio con portada, idioma, anio y paginacion`() {
        val page = parser.list(doc("directorio.html"), 1)
        assertEquals(30, page.items.size)
        assertTrue(page.hasNextPage)
        assertTrue(page.items.all { it.coverUrl!!.startsWith("https://latanime.org/thumbs/imagen/") })
        assertEquals("Devils' Crest Castellano", page.items[1].title)
        assertEquals("Castellano", page.items[1].language)
    }

    @Test
    fun `filtro genero accion y anio 2024 devuelve solo animes de 2024`() {
        val page = parser.list(doc("directorio_2024_accion.html"), 1)
        assertEquals(30, page.items.size)
        assertTrue("Todos deben ser de 2024: ${page.items.map { it.year }}", page.items.all { it.year == 2024 })
        assertTrue(page.hasNextPage)
        val p2 = parser.list(doc("directorio_2024_accion_p2.html"), 2)
        assertTrue(p2.items.all { it.year == 2024 })
    }

    @Test
    fun `emision sin carga diferida`() {
        val page = parser.list(doc("emision.html"), 1)
        assertEquals(30, page.items.size)
        assertTrue(page.hasNextPage)
        assertTrue(page.items.all { it.coverUrl != null })
    }

    @Test
    fun `ficha finalizada y en emision`() {
        val fin = parser.detail(doc("anime_finalizado.html"), "https://latanime.org/anime/devils-crest-castellano")
        assertEquals("Devils' Crest Castellano", fin.title)
        assertEquals("Demons' Crest Audio Castellano", fin.alternativeTitle)
        assertEquals(listOf("Acción", "Aventura", "Fantasía"), fin.genres)
        assertEquals(AnimeStatus.FINALIZADO, fin.status)
        assertEquals(2026, fin.year)
        assertEquals(listOf(1f, 2f), fin.episodes.map { it.number })
        assertEquals("https://latanime.org/ver/devils-crest-castellano-episodio-1", fin.episodes.first().url)
        assertTrue(fin.synopsis!!.startsWith("Yuma Ashihara"))

        val airing = parser.detail(doc("anime_estreno.html"), "https://latanime.org/anime/devils-crest")
        assertEquals(AnimeStatus.EMISION, airing.status)
    }

    @Test
    fun `servidores decodificados de Base64`() {
        val servers = parser.servers(doc("episodio.html"))
        assertEquals(listOf("dsvplay", "hexload", "savefiles", "mega", "mixdrop", "voe", "mp4upload", "byse"), servers.map { it.name })
        assertEquals("https://voe.sx/e/y1ggfu10fcqn", servers.first { it.name == "voe" }.embedUrl)
        assertTrue(servers.all { it.embedUrl.startsWith("https://") })
    }

    @Test
    fun `opciones de filtros leidas del HTML con equivalencias`() {
        val types = parser.selectOptions(doc("directorio.html"), "select[name=categoria]", "false", mapOf("Película" to "pelicula", "anime" to "tv"))
        assertEquals(20, types.size)
        assertEquals("pelicula", types.first { it.value == "Película" }.canonical)
        assertEquals("tv", types.first { it.value == "anime" }.canonical)
        val genres = parser.selectOptions(doc("directorio.html"), "select[name=genero]", "false", emptyMap())
        assertEquals(48, genres.size)
        assertEquals("accion", genres.first { it.value == "accion" }.canonical)
        assertFalse(genres.any { it.value == "false" })
    }
}
