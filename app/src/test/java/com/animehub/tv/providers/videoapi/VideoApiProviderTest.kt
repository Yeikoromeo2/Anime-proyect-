package com.animehub.tv.providers.videoapi

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** SeriesLan (API JSON) contra respuestas guardadas. */
class VideoApiProviderTest {

    private val server = MockWebServer()
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val t = request.target
                requests += t
                fun json(name: String) = MockResponse.Builder().body(Fixtures.read("serieslan/$name")).build()
                return when {
                    t.startsWith("/api/rest/v1/listing?kind=serie") -> json("listing_serie.json")
                    t.startsWith("/api/rest/v1/search?q=chicago") -> json("search_chicago.json")
                    t.startsWith("/api/rest/v1/taxonomies/genre/drama") -> json("tg.json")
                    t.startsWith("/api/rest/v1/content/serie/policias-de-chicago-2014") -> json("detalle_chicago.json")
                    t.startsWith("/api/rest/v1/episodes?item_id=19764847&season=1") -> json("eps_s1.json")
                    t.startsWith("/api/rest/v1/episodes?") -> MockResponse.Builder().body("""{"episodes":[],"pagination":{"has_next":false}}""").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun provider() = VideoApiProvider(TestSites.site("serieslan", baseUrl = server.url("/").toString().trimEnd('/')), HtmlFetcher(OkHttpClient()))

    @Test
    fun `lista el catalogo con portada, anio y pagina siguiente`() = runBlocking {
        val result = provider().search("", FilterSelection.NONE, 1)
        assertEquals(24, result.items.size)
        val first = result.items.first()
        assertEquals("Policías de Chicago", first.title)
        assertEquals(2014, first.year)
        assertTrue(first.coverUrl.orEmpty().startsWith("https://image.tmdb.org/t/p/w342/"))
        assertTrue(first.url.endsWith("/serie/policias-de-chicago-2014"))
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `busca por texto y por genero`() = runBlocking {
        val p = provider()
        assertTrue(p.search("chicago", FilterSelection.NONE, 1).items.any { it.title == "Chicago Med" })
        assertTrue(p.search("", FilterSelection(mapOf("genre" to setOf("drama"))), 1).items.isNotEmpty())
        assertTrue(requests.any { it.startsWith("/api/rest/v1/taxonomies/genre/drama") })
        assertEquals(14, p.availableFilters().first { it.id == "genre" }.options.size)
    }

    @Test
    fun `ficha con temporadas y el reproductor de cada episodio`() = runBlocking {
        val p = provider()
        val detail = p.animeDetail("https://serieslan.live/serie/policias-de-chicago-2014")
        assertEquals("Policías de Chicago", detail.title)
        assertTrue(detail.synopsis.orEmpty().contains("Distrito 21"))
        val first = detail.episodes.first()
        assertEquals(1, first.season)
        assertEquals(1f, first.number)
        assertEquals("https://player.serieslan.live/e/tv/58841/1/1", first.url)
        assertEquals(15, detail.episodes.count { it.season == 1 })
        assertEquals(first.url, p.servers(first.url).single().embedUrl)
    }
}
