package com.animehub.tv.providers.latanime

import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.data.net.ProtectionState
import com.animehub.tv.data.net.SiteProtectedException
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class LatanimeProviderTest {

    private val server = MockWebServer()
    private val requests = mutableListOf<String>()
    private lateinit var provider: LatanimeProvider

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val target = request.target
                requests += target
                fun html(name: String) = MockResponse.Builder().body(Fixtures.read(name)).build()
                return when {
                    target == "/" -> html("latanime/inicio.html")
                    target == "/animes?fecha=2024&genero=accion&categoria=false&letra=false" -> html("latanime/directorio_2024_accion.html")
                    target == "/animes?fecha=2024&genero=accion&categoria=false&letra=false&p=2" -> html("latanime/directorio_2024_accion_p2.html")
                    target.startsWith("/animes") -> html("latanime/directorio.html")
                    target == "/emision" -> html("latanime/emision.html")
                    target.startsWith("/buscar") -> MockResponse.Builder().code(403).body(Fixtures.read("cloudflare/latanime_buscar_interactive.html")).build()
                    target == "/anime/devils-crest-castellano" -> html("latanime/anime_finalizado.html")
                    target == "/ver/devils-crest-castellano-episodio-1" -> html("latanime/episodio.html")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        provider = LatanimeProvider(TestSites.site("latanime", server.url("/").toString().trimEnd('/')), HtmlFetcher(OkHttpClient()))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `genero y anio se combinan en el directorio`() = runBlocking {
        val filters = FilterSelection(mapOf("genre" to setOf("accion"), "year" to setOf("2024")))
        val p1 = provider.search("", filters, 1)
        assertEquals(30, p1.items.size)
        assertTrue(p1.items.all { it.year == 2024 })
        assertTrue(p1.hasNextPage)
        val p2 = provider.search("", filters, 2)
        assertTrue(p2.items.all { it.year == 2024 })
        assertTrue("/animes?fecha=2024&genero=accion&categoria=false&letra=false&p=2" in requests)
    }

    @Test
    fun `en emision usa su propia pagina`() = runBlocking {
        val result = provider.search("", FilterSelection(mapOf("status" to setOf("/emision"))), 1)
        assertEquals(30, result.items.size)
        assertTrue("/emision" in requests)
    }

    @Test
    fun `filtros reales con estado y equivalencias`() = runBlocking {
        val groups = provider.availableFilters().associateBy { it.id }
        assertEquals(setOf("year", "genre", "type", "letter", "status"), groups.keys)
        assertEquals(45, groups.getValue("year").options.size)
        assertEquals("emision", groups.getValue("status").options.single().canonical)
        // Las cuatro listas salen de una sola descarga de /animes.
        assertEquals(1, requests.count { it.startsWith("/animes") })
    }

    @Test
    fun `la busqueda por texto avisa de la verificacion de Cloudflare`() = runBlocking {
        try {
            provider.search("one piece", FilterSelection.NONE, 1)
            fail("Debía lanzar SiteProtectedException")
        } catch (e: SiteProtectedException) {
            assertEquals(ProtectionState.DESAFIO_INTERACTIVO, e.state)
        }
    }

    @Test
    fun `novedades, ficha y servidores`() = runBlocking {
        assertEquals(36, provider.latestEpisodes().size)
        val detail = provider.animeDetail("https://latanime.org/anime/devils-crest-castellano")
        assertEquals(2, detail.episodes.size)
        val servers = provider.servers(detail.episodes.first().url)
        assertEquals(8, servers.size)
        assertNotNull(servers.firstOrNull { it.name == "mp4upload" })
    }
}
