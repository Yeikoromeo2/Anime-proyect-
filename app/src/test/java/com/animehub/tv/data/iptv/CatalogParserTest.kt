package com.animehub.tv.data.iptv

import com.animehub.tv.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lectura del catálogo de películas y series (datos de prueba con el mismo formato que la lista). */
class CatalogParserTest {
    @Test
    fun `las categorias sin nombre o de portada no son estantes`() {
        val names = CatalogParser.parseCategories(Fixtures.read("iptv/vod_categories.json")).map { it.name }
        assertEquals(listOf("Top", "Acción", "Animación", "Vacía"), names)
    }

    @Test
    fun `lee peliculas con portada completa ano y valoracion`() {
        val items = CatalogParser.parseMovies(Fixtures.read("iptv/vod_streams.json"))
        assertEquals(2, items.size) // la de nombre vacío se descarta
        val uno = items.first()
        assertEquals("Película Uno", uno.title)
        assertEquals("https://image.tmdb.org/t/p/w342/uno.jpg", uno.poster)
        assertEquals("https://image.tmdb.org/t/p/w780/uno_fondo.jpg", uno.backdrop)
        assertEquals("2024", uno.year)
        assertEquals(4.2f, uno.rating, 0.001f)
        assertEquals(listOf("6", "401", "1000"), uno.categoryIds)
        assertEquals("2019", items[1].year)
        assertNull(items[1].backdrop)
    }

    @Test
    fun `lee series con sinopsis y categorias`() {
        val serie = CatalogParser.parseSeries(Fixtures.read("iptv/series.json")).single()
        assertEquals("Serie Uno", serie.title)
        assertEquals("Una historia de prueba.", serie.plot)
        assertEquals(listOf("1", "500"), serie.categoryIds)
    }

    @Test
    fun `los canales de la API suenan solo si el servidor da la direccion`() {
        val list = CatalogParser.parseLive("tv", "TV en vivo", Fixtures.read("iptv/live_categories.json"), Fixtures.read("iptv/live_streams.json"))
        assertEquals(3, list.channels.size)
        val sin = list.channels.first { it.name == "Canal Sin Direccion" }
        assertEquals("", sin.best.url)
        assertEquals("Mas vistos", sin.group)
        assertEquals("http://servidor.test/live/directo/4.ts", list.channels.first { it.name == "Canal Cuatro" }.best.url)
        // direct_source también vale
        assertEquals("https://servidor.test/live/directo/5.m3u8", list.channels.first { it.name == "Canal Directo" }.best.url)
        assertEquals(listOf("Mas vistos", "Noticias"), list.groups.map { it.first })
    }

    @Test
    fun `las direcciones de imagen completas se dejan igual`() {
        assertEquals("http://x.test/a.png", CatalogParser.image("http://x.test/a.png", false))
        assertNull(CatalogParser.image("", false))
    }
}
