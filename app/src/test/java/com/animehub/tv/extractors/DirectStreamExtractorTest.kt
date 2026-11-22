package com.animehub.tv.extractors

import com.animehub.tv.data.config.AggregatorConfig
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.fixtures.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Video directo para el reproductor nativo, con las páginas de los servidores guardadas el 2026-11-09. */
class DirectStreamExtractorTest {

    private val configs = listOf(
        AggregatorConfig("zilla", listOf("player.zilla-networks.com")),
        AggregatorConfig("jkplayer", listOf("jkanime.org")),
        AggregatorConfig("mp4upload", listOf("mp4upload.com")),
    )
    private val requested = mutableListOf<Pair<String, String?>>()

    private fun extractor(pages: Map<String, String>) = DirectStreamExtractor(configs) { url, referer ->
        requested += url to referer
        pages.entries.firstOrNull { url.startsWith(it.key) }?.let { Fixtures.read(it.value) } ?: error("Sin página para $url")
    }

    @Test
    fun `zilla - de la pagina del reproductor a la lista HLS`() = runTest {
        val stream = extractor(mapOf("https://player.zilla-networks.com/m3u8/" to "servidores/zilla.m3u8"))
            .extract("https://player.zilla-networks.com/play/fa543590d3399084aa0f1dc73ee1ccac", "https://veranime.ninja/")!!
        assertEquals("https://player.zilla-networks.com/m3u8/fa543590d3399084aa0f1dc73ee1ccac", stream.url)
        assertEquals(StreamType.HLS, stream.type)
        assertEquals("https://player.zilla-networks.com/", stream.headers["Referer"])
        assertEquals(1, requested.size) // solo la lista, para comprobar que es HLS
    }

    @Test
    fun `zilla - si no devuelve una lista HLS no hay video directo`() = runTest {
        val stream = DirectStreamExtractor(configs) { _, _ -> "<html>Attention Required! | Cloudflare</html>" }
            .extract("https://player.zilla-networks.com/play/abc123", null)
        assertNull(stream)
    }

    @Test
    fun `jkanime Desu - m3u8 dentro de atob`() = runTest {
        val stream = extractor(mapOf("https://jkanime.org/jkplayer/" to "servidores/jkplayer_desu.html", "https://nika.playmudos.com/" to "servidores/zilla.m3u8"))
            .extract("https://jkanime.org/jkplayer/um?e=xyz&t=1", "https://jkanime.org/")!!
        assertTrue(stream.url, stream.url.startsWith("https://nika.playmudos.com/") && ".m3u8?" in stream.url)
        assertEquals(StreamType.HLS, stream.type)
        assertEquals("https://jkanime.org/", stream.headers["Referer"])
        assertEquals("https://jkanime.org/", requested.first().second) // la página se pide con el Referer del sitio
        assertEquals(stream.url, requested.last().first) // y se comprueba que la lista responda
    }

    @Test
    fun `jkanime - si la lista del video no responde no hay video directo`() = runTest {
        val stream = DirectStreamExtractor(configs) { url, _ ->
            if (url.startsWith("https://jkanime.org/")) Fixtures.read("servidores/jkplayer_desu.html") else error("404")
        }.extract("https://jkanime.org/jkplayer/um?e=xyz", "https://jkanime.org/")
        assertNull(stream)
    }

    @Test
    fun `jkanime Magi - m3u8 en texto plano`() = runTest {
        val stream = extractor(mapOf("https://jkanime.org/jkplayer/umv" to "servidores/jkplayer_magi.html", "https://nika.playmudos.com/" to "servidores/zilla.m3u8"))
            .extract("https://jkanime.org/jkplayer/umv?e=abc", "https://jkanime.org/")!!
        assertTrue(stream.url.startsWith("https://nika.playmudos.com/"))
        assertEquals(StreamType.HLS, stream.type)
    }

    @Test
    fun `mp4upload - mp4 con su Referer`() = runTest {
        val stream = extractor(mapOf("https://www.mp4upload.com/embed-" to "servidores/mp4upload_embed.html"))
            .extract("https://www.mp4upload.com/embed-vbpqmysc8ft1.html", "https://animeflv.or.at/")!!
        assertTrue(stream.url, stream.url.startsWith("https://") && stream.url.endsWith("/video.mp4"))
        assertEquals(StreamType.MP4, stream.type)
        assertEquals("https://www.mp4upload.com/", stream.headers["Referer"])
    }

    @Test
    fun `servidores sin extractor o con error quedan con reproductor web`() = runTest {
        val ex = DirectStreamExtractor(configs) { _, _ -> error("sin red") }
        assertNull(ex.extract("https://voe.sx/e/28jgxswmgfca", null))
        assertNull(ex.extract("https://www.mp4upload.com/embed-x.html", null))
        assertNull(ex.extract("no es una url", null))
    }

    @Test
    fun `zilla - si Cloudflare bloquea los trozos el servidor va al reproductor web`() = runTest {
        val probed = mutableListOf<Pair<String, Map<String, String>>>()
        val ex = DirectStreamExtractor(configs, probe = { url, headers -> probed += url to headers; false }) { url, _ ->
            if (url.startsWith("https://player.zilla-networks.com/m3u8/")) Fixtures.read("servidores/zilla.m3u8") else error("404")
        }
        assertNull(ex.extract("https://player.zilla-networks.com/play/fa543590d3399084aa0f1dc73ee1ccac", null))
        // Se prueba el trozo de inicio (EXT-X-MAP) con las mismas cabeceras que usaría el reproductor.
        assertEquals("https://player.zilla-networks.com/segs/fa543590d3399084aa0f1dc73ee1ccac/init.html", probed.single().first)
        assertEquals("https://player.zilla-networks.com/", probed.single().second["Referer"])
    }

    @Test
    fun `lista maestra - se baja la primera calidad y se prueba su primer trozo`() = runTest {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720\n720/index.m3u8\n"
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nseg-0.ts\n#EXT-X-ENDLIST\n"
        val probed = mutableListOf<String>()
        val ex = DirectStreamExtractor(configs, probe = { url, _ -> probed += url; true }) { url, _ ->
            when {
                url.startsWith("https://jkanime.org/") -> "<script>var src = 'https://cdn.example.com/v/master.m3u8?t=1';</script>"
                url.startsWith("https://cdn.example.com/v/master.m3u8") -> master
                url == "https://cdn.example.com/v/720/index.m3u8" -> media
                else -> error("404 $url")
            }
        }
        val stream = ex.extract("https://jkanime.org/jkplayer/um?e=1", "https://jkanime.org/")!!
        assertEquals("https://cdn.example.com/v/master.m3u8?t=1", stream.url)
        assertEquals(listOf("https://cdn.example.com/v/720/seg-0.ts"), probed)
    }

    @Test
    fun `mp4upload - si el mp4 no responde no hay video directo`() = runTest {
        val ex = DirectStreamExtractor(configs, probe = { _, _ -> false }) { _, _ -> Fixtures.read("servidores/mp4upload_embed.html") }
        assertNull(ex.extract("https://www.mp4upload.com/embed-vbpqmysc8ft1.html", "https://animeflv.or.at/"))
    }

    @Test
    fun `prueba del trozo - video si responde bien y no es una pagina`() {
        assertTrue(StreamProbe.looksLikeVideo(206, byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte())))
        assertTrue(StreamProbe.looksLikeVideo(200, byteArrayOf(0x47, 0x40, 0x11)))
        assertTrue(!StreamProbe.looksLikeVideo(403, byteArrayOf(0, 0, 0)))
        assertTrue(!StreamProbe.looksLikeVideo(200, "\n<!DOCTYPE html><title>Attention Required! | Cloudflare</title>".toByteArray()))
        assertTrue(!StreamProbe.looksLikeVideo(200, ByteArray(0)))
    }
}
