package com.animehub.tv.vod

import com.animehub.tv.data.iptv.IptvAccount
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.vod.cache.VodCache
import com.animehub.tv.vod.models.Episode
import com.animehub.tv.vod.models.MediaItem
import com.animehub.tv.vod.models.MediaType
import com.animehub.tv.vod.models.ProviderId
import com.animehub.tv.vod.models.StreamRequest
import com.animehub.tv.vod.providers.CinemetaProvider
import com.animehub.tv.vod.providers.VodFetcher
import com.animehub.tv.vod.providers.XtreamVodProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Proveedores del módulo VOD con red simulada: URLs que se piden, lectura, caché de 6 h y fuentes sin guardar. */
class VodProvidersTest {
    @get:Rule val tmp = TemporaryFolder()

    private val requested = mutableListOf<String>()
    private val responses = mutableMapOf<String, String>()
    private val headersSeen = mutableListOf<Map<String, String>>()
    private val fetcher = VodFetcher { url, headers -> requested += url; headersSeen += headers; responses[url] }
    private var clockMs = 1_000_000L
    private fun cache() = VodCache(tmp.newFolder(), clock = { clockMs })
    private val account = IptvAccount("http://lista.test", "usu", "cla ve", "ts")
    private fun config(addons: List<String> = listOf("http://addon.test", "http://addon2.test")) =
        VodConfigProvider { VodConfig("https://cm.test", addons, account) }

    @Test
    fun `comunidad pide populares con genero y skip y lee los modelos unificados`() = runBlocking {
        responses["https://cm.test/catalog/movie/top.json"] = Fixtures.read("vod/cinemeta_top_movie.json")
        responses["https://cm.test/catalog/movie/top/genre=Action&skip=100.json"] = Fixtures.read("vod/cinemeta_top_movie.json")
        val p = CinemetaProvider(fetcher, cache(), config())
        val items = p.catalog(MediaType.MOVIE, null, 0)
        assertEquals(listOf("Batman Test", "Otra Pelicula"), items.map { it.title })
        val b = items.first()
        assertEquals("tt0001", b.imdbId); assertEquals("2005", b.year); assertEquals(8.2f, b.rating!!, 0.001f)
        assertEquals(listOf("Action", "Crime"), b.genres); assertEquals(ProviderId.COMMUNITY, b.provider); assertEquals(MediaType.MOVIE, b.type)
        assertEquals("2019", items[1].year)
        p.catalog(MediaType.MOVIE, "Action", 100)
        assertTrue(requested.contains("https://cm.test/catalog/movie/top/genre=Action&skip=100.json"))
    }

    @Test
    fun `comunidad busca con el texto codificado y agrupa temporadas con los especiales al final`() = runBlocking {
        responses["https://cm.test/catalog/series/top/search=breaking%20bad.json"] = Fixtures.read("vod/cinemeta_top_series.json")
        responses["https://cm.test/meta/series/tt0002.json"] = Fixtures.read("vod/cinemeta_meta_series.json")
        val p = CinemetaProvider(fetcher, cache(), config())
        assertEquals("Serie Test", p.search(MediaType.SERIES, "breaking bad").single().title)
        val d = p.detail(MediaType.SERIES, "tt0002")!!
        assertEquals(listOf(1, 2, 0), d.seasons.map { it.seasonNumber })
        assertEquals(listOf("Piloto", "Segundo"), d.seasons.first().episodes.map { it.title })
        assertEquals("El segundo.", d.seasons.first().episodes[1].overview)
        assertEquals("Especiales", d.seasons.last().label)
    }

    @Test
    fun `comunidad resuelve las fuentes de cada addon y descarta los torrents`() = runBlocking {
        responses["http://addon.test/stream/movie/tt0001.json"] = Fixtures.read("vod/addon_stream_movie.json")
        // el segundo addon está caído (sin respuesta): no afecta al primero
        val p = CinemetaProvider(fetcher, cache(), config())
        val item = MediaItem("tt0001", "tt0001", "Batman Test", MediaType.MOVIE, null, null, null, "2005", emptyList(), null, ProviderId.COMMUNITY)
        val sources = p.streams(StreamRequest(item, null))
        assertEquals(2, sources.size)
        assertEquals("1080p", sources.first().quality)
        assertEquals("https://cdn.test/a/1080.mp4", sources.first().url)
        assertTrue(sources.first().headers["User-Agent"]!!.startsWith("VLC/3.0.20"))
        assertEquals("https://cdn.test/", sources.first().headers["Referer"])
        assertEquals("720p", sources[1].quality)
        assertTrue(requested.contains("http://addon2.test/stream/movie/tt0001.json"))
    }

    @Test
    fun `comunidad pide los episodios con id temporada y episodio`() = runBlocking {
        responses["http://addon.test/stream/series/tt0002%3A1%3A2.json"] = Fixtures.read("vod/addon_stream_series.json")
        val p = CinemetaProvider(fetcher, cache(), config(listOf("http://addon.test")))
        val item = MediaItem("tt0002", "tt0002", "Serie Test", MediaType.SERIES, null, null, null, null, emptyList(), null, ProviderId.COMMUNITY)
        val s = p.streams(StreamRequest(item, Episode(1, 2, "Segundo", null, null, "tt0002:1:2", null))).single()
        assertEquals("4K", s.quality)
        assertEquals(emptyList<Any>(), p.streams(StreamRequest(item, null)))
    }

    @Test
    fun `servidor lee peliculas filtra por categoria busca sin tildes y pagina`() = runBlocking {
        val base = "http://lista.test/player_api.php?username=usu&password=cla%20ve&action="
        responses[base + "get_vod_streams"] = Fixtures.read("iptv/vod_streams.json")
        responses[base + "get_vod_categories"] = Fixtures.read("iptv/vod_categories.json")
        val p = XtreamVodProvider(fetcher, cache(), config())
        assertEquals(listOf("Top", "Acción", "Animación", "Vacía"), p.categories(MediaType.MOVIE).map { it.name })
        assertEquals(2, p.catalog(MediaType.MOVIE, null, 0).size)
        assertEquals(listOf("Película Uno"), p.catalog(MediaType.MOVIE, "6", 0).map { it.title })
        assertEquals(emptyList<Any>(), p.catalog(MediaType.MOVIE, null, 5))
        assertEquals(listOf("Película Dos"), p.search(MediaType.MOVIE, "PELICULA dos").map { it.title })
        val uno = p.catalog(MediaType.MOVIE, null, 0).first()
        assertEquals(ProviderId.XTREAM, uno.provider); assertEquals("https://image.tmdb.org/t/p/w342/uno.jpg", uno.poster)
        assertEquals(4.2f * 2, uno.rating!!, 0.001f)
    }

    @Test
    fun `servidor arma las direcciones de pelicula y episodio solo al reproducir`() = runBlocking {
        val base = "http://lista.test/player_api.php?username=usu&password=cla%20ve&action="
        responses[base + "get_series"] = Fixtures.read("iptv/series.json")
        responses[base + "get_series_info&series_id=701"] = Fixtures.read("vod/xtream_series_info.json")
        val p = XtreamVodProvider(fetcher, cache(), config())
        val movie = MediaItem("501", null, "Película Uno", MediaType.MOVIE, null, null, null, null, emptyList(), null, ProviderId.XTREAM)
        val m = p.streams(StreamRequest(movie, null))
        assertEquals("http://lista.test/movie/usu/cla%20ve/501.mp4", m.first().url)
        assertEquals(listOf("mp4", "mkv", "m3u8"), m.map { it.url.substringAfterLast('.') })
        assertTrue(m.all { it.headers["User-Agent"]!!.startsWith("VLC/3.0.20") && it.isDirect })
        val d = p.detail(MediaType.SERIES, "701")!!
        assertEquals(listOf(1, 2), d.seasons.map { it.seasonNumber })
        assertEquals(listOf("Piloto", "Segundo"), d.seasons.first().episodes.map { it.title })
        assertEquals("Episodio 1", d.seasons[1].episodes.single().title)
        assertEquals("Una historia de prueba.", d.item.overview)
        val ep = d.seasons.first().episodes[1] // el 2, en mkv
        val s = p.streams(StreamRequest(d.item, ep))
        assertEquals("http://lista.test/series/usu/cla%20ve/9002.mkv", s.first().url)
        assertEquals(listOf("mkv", "mp4", "m3u8"), s.map { it.url.substringAfterLast('.') })
    }

    @Test
    fun `la cache dura 6 horas se puede borrar y las fuentes nunca se guardan`() = runBlocking {
        responses["https://cm.test/catalog/movie/top.json"] = Fixtures.read("vod/cinemeta_top_movie.json")
        responses["http://addon.test/stream/movie/tt0001.json"] = Fixtures.read("vod/addon_stream_movie.json")
        val cache = cache()
        val p = CinemetaProvider(fetcher, cache, config(listOf("http://addon.test")))
        p.catalog(MediaType.MOVIE, null, 0); p.catalog(MediaType.MOVIE, null, 0)
        assertEquals(1, requested.count { it.endsWith("/catalog/movie/top.json") })
        clockMs += 5 * 60 * 60_000L // 5 h: sigue
        p.catalog(MediaType.MOVIE, null, 0)
        assertEquals(1, requested.count { it.endsWith("/catalog/movie/top.json") })
        clockMs += 2 * 60 * 60_000L // 7 h: caducó
        p.catalog(MediaType.MOVIE, null, 0)
        assertEquals(2, requested.count { it.endsWith("/catalog/movie/top.json") })
        cache.clear()
        p.catalog(MediaType.MOVIE, null, 0)
        assertEquals(3, requested.count { it.endsWith("/catalog/movie/top.json") })
        // Las fuentes se piden cada vez (sin caché).
        val item = MediaItem("tt0001", "tt0001", "B", MediaType.MOVIE, null, null, null, null, emptyList(), null, ProviderId.COMMUNITY)
        p.streams(StreamRequest(item, null)); p.streams(StreamRequest(item, null))
        assertEquals(2, requested.count { it.endsWith("/stream/movie/tt0001.json") })
    }

    @Test
    fun `sin red se usa la copia vieja y sin ella no hay resultados`() = runBlocking {
        responses["https://cm.test/catalog/movie/top.json"] = Fixtures.read("vod/cinemeta_top_movie.json")
        val p = CinemetaProvider(fetcher, cache(), config())
        assertEquals(2, p.catalog(MediaType.MOVIE, null, 0).size)
        clockMs += 10 * 60 * 60_000L
        responses.clear() // se cae la red: la copia vencida sirve de respaldo
        assertEquals(2, p.catalog(MediaType.MOVIE, null, 0).size)
        assertEquals(emptyList<Any>(), p.catalog(MediaType.SERIES, null, 0))
        assertNull(p.detail(MediaType.MOVIE, "tt9"))
        assertFalse(p.search(MediaType.MOVIE, "x").isNotEmpty())
    }

    @Test
    fun `los addons se limpian de manifest y barras`() {
        assertEquals(listOf("https://a.test", "http://b.test/x"), VodConfig.parseAddons("https://a.test/manifest.json, http://b.test/x/\nnada"))
    }
}
