package com.animehub.tv.util

import com.animehub.tv.fixtures.Fixtures
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverUrlNormalizerTest {

    private fun attrs(vararg pairs: Pair<String, String>): (String) -> String? = pairs.toMap()::get

    @Test
    fun `prefiere data-src sobre el placeholder de src`() {
        val url = CoverUrlNormalizer.fromAttributes(
            attrs("src" to "https://latanime.org/img/anime.png", "data-src" to "https://latanime.org/thumbs/imagen/a.jpg"),
            baseUrl = "https://latanime.org",
            placeholders = listOf("/img/anime.png"),
        )
        assertEquals("https://latanime.org/thumbs/imagen/a.jpg", url)
    }

    @Test
    fun `convierte rutas relativas y protocol-relative en absolutas`() {
        assertEquals("https://sitio.com/uploads/p.jpg", CoverUrlNormalizer.resolve("/uploads/p.jpg", "https://sitio.com/anime/x"))
        assertEquals("https://cdn.sitio.com/p.jpg", CoverUrlNormalizer.resolve("//cdn.sitio.com/p.jpg", "https://sitio.com"))
        assertEquals("https://sitio.com/anime/p.jpg", CoverUrlNormalizer.resolve("p.jpg", "https://sitio.com/anime/x"))
    }

    @Test
    fun `descarta GIF data y placeholders`() {
        val url = CoverUrlNormalizer.fromAttributes(
            attrs("src" to "data:image/gif;base64,R0lGOD"),
            baseUrl = "https://veranime.ninja",
        )
        assertNull(url)
    }

    @Test
    fun `elige la imagen mas grande del srcset`() {
        assertEquals("b.jpg", CoverUrlNormalizer.pickFromSrcset("a.jpg 300w, b.jpg 600w"))
        assertEquals("x2.jpg", CoverUrlNormalizer.pickFromSrcset("x1.jpg 1x, x2.jpg 2x"))
    }

    @Test
    fun `reescribe el tamano de TMDB`() {
        val rewrite = listOf(UrlRewrite("/t/p/w\\d+/", "/t/p/w342/"))
        assertEquals(
            "https://image.tmdb.org/t/p/w342/1g9x.jpg",
            CoverUrlNormalizer.applyRewrites("https://image.tmdb.org/t/p/w92/1g9x.jpg", rewrite),
        )
    }

    @Test
    fun `todas las tarjetas del directorio de Latanime tienen portada absoluta`() {
        val doc = Jsoup.parse(Fixtures.read("latanime/directorio.html"), "https://latanime.org/animes")
        val cards = doc.select("a:has(> div.series)")
        assertEquals(30, cards.size)
        cards.forEach { card ->
            val img = card.selectFirst("div.serieimg img")!!
            val cover = CoverUrlNormalizer.fromAttributes(img::attr, "https://latanime.org", listOf("/img/anime.png", "/img/capblank.png"))
            assertTrue("portada inválida: $cover", cover!!.startsWith("https://latanime.org/thumbs/imagen/"))
        }
    }

    @Test
    fun `la pagina de emision de Latanime trae la portada en src sin lazy`() {
        val doc = Jsoup.parse(Fixtures.read("latanime/emision.html"))
        val img = doc.selectFirst("a:has(> div.series) div.serieimg img")!!
        val cover = CoverUrlNormalizer.fromAttributes(img::attr, "https://latanime.org", listOf("/img/anime.png"))
        assertTrue(cover!!.startsWith("https://latanime.org/thumbs/imagen/"))
    }

    @Test
    fun `las portadas de busqueda de Anime Ninja se piden en mayor tamano`() {
        val doc = Jsoup.parse(Fixtures.read("animeninja/busqueda_one_piece.html"))
        val img = doc.selectFirst("div.result-item article div.thumbnail img")!!
        val cover = CoverUrlNormalizer.fromAttributes(
            img::attr, "https://veranime.ninja", rewrites = listOf(UrlRewrite("/t/p/w\\d+/", "/t/p/w342/")),
        )
        assertEquals("https://image.tmdb.org/t/p/w342/1g9xCTVrwAwszsdQpLi0lY8dX2a.jpg", cover)
    }
}
