package com.animehub.tv.stream

import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.remote.HttpRequest
import com.animehub.tv.remote.HttpResponse
import com.animehub.tv.remote.MiniHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Intermediario en la tablet para Chromecast y las TVs DLNA (Samsung, LG…): ellas piden el video
 * a la tablet, y la tablet lo pide al servidor con las cabeceras que exige (Referer, Origin) y se
 * lo pasa. En las listas HLS reescribe cada dirección para que también los trozos pasen por aquí.
 *
 * Solo sirve los videos publicados con [publish] (cada uno con una clave al azar) y responde con
 * CORS abierto, que el receptor de Chromecast necesita para HLS.
 *
 * @param hostAddress dirección de la tablet en la red de casa (en las pruebas, 127.0.0.1).
 */
class StreamProxy(
    private val hostAddress: () -> String? = ::lanAddress,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MiniHttpServer(::handle)
    private val sessions = ConcurrentHashMap<String, Map<String, String>>()
    private val random = SecureRandom()

    /** La dirección que debe abrir el Chromecast o la TV; null si la tablet no está en una red local. */
    fun publish(stream: DirectStream): String? {
        val host = hostAddress() ?: return null
        if (!server.isRunning) server.start(scope)
        val token = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        sessions[token] = stream.headers
        return proxied(host, token, stream.url, if (stream.type == StreamType.HLS) "v.m3u8" else "v.mp4")
    }

    /** Deja de servir los videos publicados (al terminar de ver). */
    fun stop() {
        sessions.clear()
        server.stop()
    }

    private fun proxied(host: String, token: String, url: String, name: String) =
        "http://$host:${server.port}/p/$token/$name?u=${URLEncoder.encode(url, "UTF-8")}"

    suspend fun handle(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        if (request.method == "OPTIONS") return@withContext HttpResponse(204, CORS)
        val parts = request.path.split('/').filter { it.isNotEmpty() }
        if (parts.size < 2 || parts[0] != "p") return@withContext HttpResponse.text(404, "No existe")
        val token = parts[1]
        val headers = sessions[token] ?: return@withContext HttpResponse.text(404, "Video no publicado")
        val url = request.query["u"]?.takeIf { it.startsWith("http") } ?: return@withContext HttpResponse.text(400, "Falta la dirección")
        val upstream = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .apply { request.header("range")?.let { header("Range", it) } }
            .build()
        val response = runCatching { http.newCall(upstream).execute() }.getOrElse {
            return@withContext HttpResponse.text(502, "El servidor del video no respondió")
        }
        val isPlaylist = response.isSuccessful &&
            (response.header("Content-Type").orEmpty().contains("mpegurl", ignoreCase = true) ||
                runCatching { response.peekBody(7).string() }.getOrDefault("") == "#EXTM3U")
        if (isPlaylist) {
            val text = response.use { it.body.string() }
            val host = request.header("host")?.substringBefore(':') ?: hostAddress() ?: "127.0.0.1"
            val body = rewrite(text, response.request.url.toString()) { absolute, isPlaylistUri ->
                proxied(host, token, absolute, if (isPlaylistUri) "v.m3u8" else "s")
            }.toByteArray(Charsets.UTF_8)
            return@withContext HttpResponse(200, CORS + ("Content-Type" to "application/vnd.apple.mpegurl"), body)
        }
        val out = CORS.toMutableMap()
        if (request.method == "HEAD") response.close()
        response.header("Content-Type")?.let { out["Content-Type"] = it }
        response.header("Content-Range")?.let { out["Content-Range"] = it }
        out["Accept-Ranges"] = response.header("Accept-Ranges") ?: "bytes"
        val length = response.body.contentLength().takeIf { it >= 0 }
        HttpResponse(response.code, out, contentLength = length, stream = { sink ->
            response.use { r -> r.body.byteStream().use { it.copyTo(sink) } }
        })
    }

    companion object {
        private val CORS = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Headers" to "Range, Content-Type",
            "Access-Control-Expose-Headers" to "Content-Length, Content-Range, Accept-Ranges",
        )
        private val URI_ATTR = Regex("""URI="([^"]+)"""")

        /**
         * Reescribe una lista HLS: cada dirección (trozos, calidades y las de `URI="…"` de
         * EXT-X-MAP, EXT-X-KEY, EXT-X-MEDIA) pasa a ser absoluta y a través del intermediario.
         * @param proxy (dirección absoluta, ¿es otra lista?) → dirección por el intermediario.
         */
        fun rewrite(playlist: String, baseUrl: String, proxy: (String, Boolean) -> String): String {
            val base = runCatching { URI(baseUrl) }.getOrNull()
            fun absolute(ref: String) = runCatching { base?.resolve(ref.trim())?.toString() }.getOrNull() ?: ref.trim()
            var nextIsPlaylist = false
            return playlist.lineSequence().joinToString("\n") { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> line
                    trimmed.startsWith("#") -> {
                        if (trimmed.startsWith("#EXT-X-STREAM-INF") || trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF")) nextIsPlaylist = true
                        val media = trimmed.startsWith("#EXT-X-MEDIA") || trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF")
                        URI_ATTR.replace(line) { m -> "URI=\"${proxy(absolute(m.groupValues[1]), media)}\"" }
                    }
                    else -> proxy(absolute(trimmed), nextIsPlaylist || trimmed.substringBefore('?').endsWith(".m3u8"))
                        .also { nextIsPlaylist = false }
                }
            }
        }

        /** Dirección IPv4 de la tablet en la red de casa (Wi-Fi o cable). */
        fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
