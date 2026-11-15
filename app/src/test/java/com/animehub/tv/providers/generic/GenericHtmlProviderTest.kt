package com.animehub.tv.providers.generic

import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.fixtures.TestSites
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Retro (lacartoons) y Pelis (cinehindi, gnula) con el proveedor genérico, contra sus HTML guardados. */
class GenericHtmlProviderTest {

    private val server = MockWebServer()
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val t = request.target
                requests += t
                fun html(name: String) = MockResponse.Builder().body(Fixtures.read(name)).build()
                return when (t) {
                    "/" -> html("lacartoons/inicio.html")
                    "/?Titulo=bob" -> html("lacartoons/busqueda_bob.html")
                    "/?Categoria_id=1&page=2" -> html("lacartoons/categoria_1_p2.html")
                    "/serie/1" -> html("lacartoons/serie_1.html")
                    "/serie/capitulo/1?t=1" -> html("lacartoons/capitulo_1.html")
                    "/pelicula/" -> html("cinehindi/peliculas.html")
                    "/pelicula/page/2/" -> html("cinehindi/peliculas_p2.html")
                    "/genero/accion/" -> html("cinehindi/genero_accion.html")
                    "/pelicula/mujhse-shaadi-karogi-completa-subtitulada-online/" -> html("cinehindi/pelicula_mujhse.html")
                    "/peliculas/" -> html("gnula/peliculas.html")
                    "/movies/the-last-temptation-of-becky/" -> html("gnula/pelicula_becky.html")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun provider(id: String) =
        GenericHtmlProvider(TestSites.site(id, baseUrl = server.url("/").toString().trimEnd('/')), HtmlFetcher(OkHttpClient()))

    @Test
    fun `lacartoons lista el catalogo con portada y anio`() = runBlocking {
        val result = provider("lacartoons").search("", FilterSelection.NONE, 1)
        assertTrue("tarjetas: ${result.items.size}", result.items.size >= 10)
        val first = result.items.first()
        assertEquals("2 Perros Tontos", first.title)
        assertEquals(1993, first.year)
        assertNotNull(first.coverUrl)
        assertTrue(first.url.endsWith("/serie/1"))
        assertTrue("hay página siguiente", result.hasNextPage)
    }

    @Test
    fun `lacartoons busca por titulo y filtra por canal`() = runBlocking {
        val p = provider("lacartoons")
        assertTrue(p.search("bob", FilterSelection.NONE, 1).items.isNotEmpty())
        val byChannel = p.search("", FilterSelection(mapOf("channel" to setOf("1"))), 2)
        assertTrue(byChannel.items.isNotEmpty())
        assertTrue(requests.contains("/?Categoria_id=1&page=2"))
        assertEquals(listOf("Nickelodeon"), p.availableFilters().first().options.take(1).map { it.label })
    }

    @Test
    fun `lacartoons ficha con temporadas y reproductor incrustado`() = runBlocking {
        val p = provider("lacartoons")
        val detail = p.animeDetail("https://lacartoons.com/serie/1")
        assertEquals("2 Perros Tontos", detail.title)
        assertEquals(1993, detail.year)
        assertTrue(detail.synopsis.orEmpty().contains("perros"))
        assertEquals(12, detail.episodes.size)
        assertEquals(1, detail.episodes.first().season)
        assertEquals(1f, detail.episodes.first().number)
        val servers = p.servers(detail.episodes.first().url)
        assertEquals("https://ok.ru/videoembed/1392690399817", servers.single().embedUrl)
        assertEquals("Latino", servers.single().language)
    }

    @Test
    fun `cinehindi lista peliculas, pagina y filtra por genero`() = runBlocking {
        val p = provider("cinehindi")
        val page1 = p.search("", FilterSelection.NONE, 1)
        assertTrue("tarjetas: ${page1.items.size}", page1.items.size >= 10)
        assertEquals("Mujhse Shaadi Karogi", page1.items.first().title)
        assertEquals(2004, page1.items.first().year)
        assertTrue(page1.hasNextPage)
        assertTrue(p.search("", FilterSelection.NONE, 2).items.isNotEmpty())
        assertTrue(p.search("", FilterSelection(mapOf("genre" to setOf("accion"))), 1).items.isNotEmpty())
    }

    @Test
    fun `cinehindi busca filtrando su catalogo porque su buscador no sirve`() = runBlocking {
        val result = provider("cinehindi").search("mujhse", FilterSelection.NONE, 1)
        assertTrue(result.items.any { it.title == "Mujhse Shaadi Karogi" })
        assertTrue("no usó la búsqueda del sitio", requests.none { it.contains("?s=") })
    }

    @Test
    fun `cinehindi ficha de pelicula y opciones de reproductor con idioma`() = runBlocking {
        val p = provider("cinehindi")
        val detail = p.animeDetail("https://cinehindi.com/pelicula/mujhse-shaadi-karogi-completa-subtitulada-online/")
        assertEquals("Mujhse Shaadi Karogi", detail.title)
        assertEquals(1, detail.episodes.size) // película = un solo episodio
        assertTrue(detail.genres.contains("Comedia"))
        val servers = p.servers(detail.episodes.single().url)
        assertEquals(listOf("Hqq", "Mixdrop", "Streamz"), servers.map { it.name })
        assertEquals("Subtitulado", servers.first().language)
        assertTrue(servers.first().embedUrl.contains("trembed=0"))
    }

    @Test
    fun `gnula lista peliculas con anio e idioma y abre la ficha con sus servidores`() = runBlocking {
        val p = provider("gnula")
        val items = p.search("", FilterSelection.NONE, 1).items
        assertTrue("tarjetas: ${items.size}", items.size >= 10)
        assertNotNull(items.first().year)
        assertNotNull(items.first().language)
        val detail = p.animeDetail("https://ver.gnula.one/movies/the-last-temptation-of-becky/")
        assertEquals("The Last Temptation of Becky", detail.title)
        val servers = p.servers(detail.episodes.single().url)
        assertEquals(listOf("Links", "Player", "Streamwish", "Vidhidepro"), servers.map { it.name })
        assertEquals("Subtitulado", servers.first().language)
    }
}
