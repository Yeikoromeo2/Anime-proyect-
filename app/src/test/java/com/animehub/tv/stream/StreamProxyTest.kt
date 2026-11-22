package com.animehub.tv.stream

import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.remote.HttpResponse
import com.animehub.tv.remote.MiniHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Intermediario para Chromecast/DLNA: el "servidor de video" de la prueba exige Referer (como los
 * reales) y el intermediario lo pone; las listas HLS salen reescritas y los MP4 respetan Range.
 */
class StreamProxyTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val referers = mutableListOf<String?>()
    private val video = ByteArray(1000) { (it % 251).toByte() }

    private val origin: MiniHttpServer = MiniHttpServer({ request ->
        referers += request.header("referer")
        if (request.header("referer") != "https://origen.example/") return@MiniHttpServer HttpResponse.text(403, "Sin Referer")
        when (request.path) {
            "/hls/master.m3u8" -> HttpResponse(200, mapOf("Content-Type" to "application/vnd.apple.mpegurl"), (
                "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",URI=\"audio/index.m3u8\"\n" +
                    "#EXT-X-STREAM-INF:BANDWIDTH=800000\n720/index.m3u8\n").toByteArray())
            "/hls/720/index.m3u8" -> HttpResponse(200, mapOf("Content-Type" to "text/plain"), (
                "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:10,\nseg0.m4s\n#EXTINF:10,\nhttp://127.0.0.1:${port()}/otro/seg1.m4s\n#EXT-X-ENDLIST\n").toByteArray())
            "/hls/720/seg0.m4s" -> HttpResponse(200, mapOf("Content-Type" to "video/iso.segment"), byteArrayOf(0, 0, 0, 24, 'f'.code.toByte()))
            "/video.mp4" -> {
                val range = Regex("""bytes=(\d+)-(\d+)""").find(request.header("range").orEmpty())
                if (range == null) HttpResponse(200, mapOf("Content-Type" to "video/mp4"), video)
                else {
                    val (a, b) = range.destructured.toList().map { it.toInt() }
                    HttpResponse(206, mapOf("Content-Type" to "video/mp4", "Content-Range" to "bytes $a-$b/${video.size}"), video.copyOfRange(a, b + 1))
                }
            }
            else -> HttpResponse.text(404, "No")
        }
    })
    private fun port(): Int = origin.port

    private val proxy = StreamProxy(hostAddress = { "127.0.0.1" }, http = OkHttpClient())
    private val http = OkHttpClient()
    private val headers = mapOf("Referer" to "https://origen.example/")

    @Before
    fun setUp() = origin.start(scope)

    @After
    fun tearDown() {
        proxy.stop()
        origin.stop()
        scope.cancel()
    }

    private fun get(url: String, range: String? = null) =
        http.newCall(Request.Builder().url(url).apply { range?.let { header("Range", it) } }.build()).execute()

    @Test
    fun `hls - las listas salen reescritas y los trozos llegan con el Referer del servidor`() {
        val url = proxy.publish(DirectStream("http://127.0.0.1:${port()}/hls/master.m3u8", StreamType.HLS, headers))!!
        assertTrue(url, url.startsWith("http://127.0.0.1:") && "/p/" in url && "v.m3u8" in url)

        val master = get(url).use { r ->
            assertEquals(200, r.code)
            assertEquals("*", r.header("Access-Control-Allow-Origin")) // CORS para el receptor de Chromecast
            assertTrue(r.header("Content-Type")!!.contains("mpegurl"))
            r.body.string()
        }
        val lines = master.lines()
        val variant = lines.first { it.startsWith("http") }
        assertTrue(variant, variant.startsWith("http://127.0.0.1:") && "v.m3u8" in variant && "720%2Findex.m3u8" in variant)
        assertTrue(lines[1], lines[1].contains("URI=\"http://127.0.0.1:") && "audio%2Findex.m3u8" in lines[1])

        val media = get(variant).use { it.body.string() }
        val mapLine = media.lines().first { it.startsWith("#EXT-X-MAP") }
        assertTrue(mapLine, "init.mp4" in mapLine && "/p/" in mapLine)
        val segments = media.lines().filter { it.startsWith("http") }
        assertEquals(2, segments.size)
        assertTrue(segments.all { "/p/" in it })
        val segment = get(segments.first()).use { it.body.bytes() }
        assertArrayEquals(byteArrayOf(0, 0, 0, 24, 'f'.code.toByte()), segment)
        // Cada petición al servidor llevó el Referer (aunque el Chromecast no lo envía).
        assertTrue(referers.all { it == "https://origen.example/" })
    }

    @Test
    fun `mp4 - respeta Range para poder saltar`() {
        val url = proxy.publish(DirectStream("http://127.0.0.1:${port()}/video.mp4", StreamType.MP4, headers))!!
        get(url, "bytes=10-19").use { r ->
            assertEquals(206, r.code)
            assertEquals("bytes 10-19/1000", r.header("Content-Range"))
            assertArrayEquals(video.copyOfRange(10, 20), r.body.bytes())
        }
        get(url).use { r ->
            assertEquals(200, r.code)
            assertEquals(1000, r.body.bytes().size)
        }
    }

    @Test
    fun `solo sirve videos publicados`() {
        proxy.publish(DirectStream("http://127.0.0.1:${port()}/video.mp4", StreamType.MP4, headers))!!
        val port = Regex(""":(\d+)/""").find(proxy.publish(DirectStream("http://x/y.mp4", StreamType.MP4, emptyMap()))!!)!!.groupValues[1]
        get("http://127.0.0.1:$port/p/clave-falsa/v.mp4?u=http%3A%2F%2F127.0.0.1%3A${port()}%2Fvideo.mp4").use { assertEquals(404, it.code) }
        http.newCall(Request.Builder().url("http://127.0.0.1:$port/p/x").method("OPTIONS", null).build()).execute().use {
            assertEquals(204, it.code)
            assertEquals("*", it.header("Access-Control-Allow-Origin"))
        }
    }

    @Test
    fun `rewrite - direcciones relativas, absolutas y atributos URI`() {
        val out = StreamProxy.rewrite(
            "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:5,\nseg.ts?t=1\n#EXTINF:5,\nhttps://cdn.example/a/seg2.ts\n",
            "https://origen.example/hls/list.m3u8?st=abc",
        ) { url, playlist -> "P[$url|$playlist]" }
        assertTrue(out, "URI=\"P[https://origen.example/hls/key.bin|false]\"" in out)
        assertTrue(out, "P[https://origen.example/hls/seg.ts?t=1|false]" in out)
        assertTrue(out, "P[https://cdn.example/a/seg2.ts|false]" in out)
    }
}
