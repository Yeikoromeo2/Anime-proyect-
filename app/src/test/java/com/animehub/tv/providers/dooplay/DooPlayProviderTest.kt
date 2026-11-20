package com.animehub.tv.providers.dooplay

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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** El proveedor completo contra un servidor local que sirve los HTML guardados. */
class DooPlayProviderTest {

    private val server = MockWebServer()
    private val requests = mutableListOf<String>()
    private lateinit var provider: DooPlayProvider

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val target = request.target
                requests += "${request.method} $target"
                fun html(name: String) = MockResponse.Builder().body(Fixtures.read("animeninja/$name")).build()
                return when {
                    target == "/" -> html("inicio.html")
                    target == "/?s=one+piece" -> html("busqueda_one_piece.html")
                    target == "/page/2/?s=dragon" -> html("busqueda_dragon_p2.html")
                    target == "/?s=bloqueado" -> MockResponse.Builder().code(403)
                        .body(Fixtures.read("cloudflare/jkanime_inicio_interactive.html")).build()
                    target == "/genero/accion/" -> html("genero_accion.html")
                    target == "/anime/" -> html("directorio_anime.html")
                    target == "/anime/tank-chair/" -> html("anime_tank_chair.html")
                    target == "/ver/tank-chair-episodio-6/" -> html("episodio_tank_chair_6.html")
                    target == "/wp-admin/admin-ajax.php" -> MockResponse.Builder()
                        .body(Fixtures.read("animeninja/player_ajax_tank_chair_6.json")).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        provider = DooPlayProvider(TestSites.site("animeninja", baseUrl = base), HtmlFetcher(OkHttpClient()))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `usa el dominio configurado y las rutas de la configuracion`() = runBlocking {
        assertEquals(30, provider.latestEpisodes().size)
        assertEquals("One Piece", provider.search("one piece", FilterSelection.NONE, 1).items.single().title)
        assertEquals(19, provider.search("dragon", FilterSelection.NONE, 2).items.size)
        assertTrue(requests.containsAll(listOf("GET /", "GET /?s=one+piece", "GET /page/2/?s=dragon")))
    }

    @Test
    fun `sin texto lista el archivo del genero elegido`() = runBlocking {
        val filters = FilterSelection(mapOf("genre" to setOf("accion")))
        assertEquals(30, provider.search("", filters, 1).items.size)
        assertTrue("GET /genero/accion/" in requests)
    }

    @Test
    fun `filtros reales leidos del sitio`() = runBlocking {
        val groups = provider.availableFilters().associateBy { it.id }
        assertEquals(listOf("/anime/", "/pelicula/"), groups.getValue("type").options.map { it.value })
        assertEquals(61, groups.getValue("genre").options.size)
    }

    @Test
    fun `ficha guardada con otro dominio se pide al dominio actual`() = runBlocking {
        val detail = provider.animeDetail("https://dominio-viejo.ninja/anime/tank-chair/")
        assertEquals("Tank Chair", detail.title)
        assertEquals(6, detail.episodes.size)
        assertTrue("GET /anime/tank-chair/" in requests)
    }

    @Test
    fun `servidores via admin-ajax de DooPlay`() = runBlocking {
        val servers = provider.servers(server.url("/ver/tank-chair-episodio-6/").toString())
        assertEquals("https://animeav1.com/media/tank-chair/6", servers.single().embedUrl)
        assertEquals("MULTISERVER animeav1.com", servers.single().name)
        assertTrue("POST /wp-admin/admin-ajax.php" in requests)
    }

    @Test
    fun `un desafio de Cloudflare se informa como sitio protegido`() = runBlocking {
        try {
            provider.search("bloqueado", FilterSelection.NONE, 1)
            fail("Debía lanzar SiteProtectedException")
        } catch (e: SiteProtectedException) {
            assertEquals(ProtectionState.DESAFIO_INTERACTIVO, e.state)
        }
    }
}
