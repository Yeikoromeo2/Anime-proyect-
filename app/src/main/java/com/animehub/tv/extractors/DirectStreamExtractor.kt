package com.animehub.tv.extractors

import com.animehub.tv.data.config.AggregatorConfig
import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.StreamType
import java.net.URI
import java.util.Base64

/**
 * Saca el enlace directo del video (HLS `.m3u8` o `.mp4`) de los servidores que lo permiten,
 * para reproducirlo en el reproductor nativo sin la página del servidor (sin anuncios ni avisos).
 * Qué servidor usa cada extractor sale de `directStreams` en la configuración remota.
 *
 * - "zilla": `player.zilla-networks.com/play/{id}` → `/m3u8/{id}` (se comprueba que sea una lista HLS).
 * - "jkplayer": reproductores propios de JKAnime (Desu, Magi): el `.m3u8` está en la página, a veces en `atob('…')`;
 *   se comprueba que la lista responda (a veces el enlace del sitio está roto).
 * - "mp4upload": la página del reproductor trae `src: "https://….mp4"`.
 *
 * @param fetch descarga una página (URL, Referer) y devuelve su texto; lanza si falla.
 */
class DirectStreamExtractor(
    private val configs: List<AggregatorConfig>,
    private val fetch: suspend (url: String, referer: String?) -> String,
) {
    /** null si el servidor no tiene extractor o no se encontró el video. */
    suspend fun extract(embedUrl: String, referer: String?): DirectStream? {
        val host = host(embedUrl) ?: return null
        val type = configs.firstOrNull { c -> c.hosts.any { host == it || host.endsWith(".$it") } }?.type ?: return null
        return runCatching {
            when (type) {
                "zilla" -> zilla(embedUrl)
                "jkplayer" -> jkplayer(embedUrl, referer)
                "mp4upload" -> mp4upload(embedUrl, referer)
                else -> null
            }
        }.getOrNull()
    }

    private suspend fun zilla(url: String): DirectStream? {
        val uri = URI(url)
        val id = ZILLA_ID.find(uri.path.orEmpty())?.groupValues?.get(1) ?: return null
        val origin = "${uri.scheme}://${uri.host}"
        val playlist = "$origin/m3u8/$id"
        if (!fetch(playlist, "$origin/").trimStart().startsWith("#EXTM3U")) return null
        return DirectStream(playlist, StreamType.HLS, mapOf("Referer" to "$origin/", "Origin" to origin))
    }

    private suspend fun jkplayer(url: String, referer: String?): DirectStream? {
        val html = fetch(url, referer)
        val fromAtob = ATOB.findAll(html).mapNotNull { decode(it.groupValues[1]) }
        val plain = DIRECT.findAll(html).map { it.value }
        val link = (fromAtob + plain).firstOrNull { it.startsWith("http") && isVideo(it) } ?: return null
        val origin = URI(url).let { "${it.scheme}://${it.host}" }
        val stream = DirectStream(link, typeOf(link), mapOf("Referer" to "$origin/", "Origin" to origin))
        if (stream.type == StreamType.HLS && !fetch(link, "$origin/").trimStart().startsWith("#EXTM3U")) return null
        return stream
    }

    private suspend fun mp4upload(url: String, referer: String?): DirectStream? {
        val html = fetch(url, referer)
        val link = MP4UPLOAD_SRC.find(html)?.groupValues?.get(1) ?: return null
        val origin = URI(url).let { "${it.scheme}://${it.host}" }
        return DirectStream(link, StreamType.MP4, mapOf("Referer" to "$origin/"))
    }

    companion object {
        private val ZILLA_ID = Regex("""/(?:play|m3u8)/([A-Za-z0-9]+)""")
        private val ATOB = Regex("""atob\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*\)""")
        private val DIRECT = Regex("""https?://[^'"\s<>\\]+\.(?:m3u8|mp4)(?:\?[^'"\s<>\\]*)?""")
        private val MP4UPLOAD_SRC = Regex("""src\s*:\s*"(https?://[^"]+\.mp4[^"]*)"""")

        fun typeOf(url: String): StreamType = if (url.substringBefore('?').endsWith(".mp4")) StreamType.MP4 else StreamType.HLS

        private fun isVideo(url: String) = url.substringBefore('?').let { it.endsWith(".m3u8") || it.endsWith(".mp4") }

        private fun decode(base64: String): String? =
            runCatching { String(Base64.getDecoder().decode(base64), Charsets.UTF_8).trim() }.getOrNull()

        private fun host(url: String) = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    }
}
