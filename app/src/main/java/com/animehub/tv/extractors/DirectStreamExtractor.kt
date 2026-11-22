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
 * - "zilla": `player.zilla-networks.com/play/{id}` → `/m3u8/{id}`.
 * - "jkplayer": reproductores propios de JKAnime (Desu, Magi): el `.m3u8` está en la página, a veces en `atob('…')`.
 * - "mp4upload": la página del reproductor trae `src: "https://….mp4"`.
 *
 * Antes de darlo por bueno se comprueba que la lista HLS responda y que su primer trozo de video
 * también llegue (no una página de bloqueo): si no, el servidor se abre en el reproductor web.
 *
 * @param probe ¿llega video de esta URL con estas cabeceras? (ver [StreamProbe]).
 * @param fetch descarga una página (URL, Referer) y devuelve su texto; lanza si falla.
 */
class DirectStreamExtractor(
    private val configs: List<AggregatorConfig>,
    private val probe: suspend (url: String, headers: Map<String, String>) -> Boolean = { _, _ -> true },
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
        val stream = DirectStream("$origin/m3u8/$id", StreamType.HLS, mapOf("Referer" to "$origin/", "Origin" to origin))
        return stream.takeIf { playable(it) }
    }

    private suspend fun jkplayer(url: String, referer: String?): DirectStream? {
        val html = fetch(url, referer)
        val fromAtob = ATOB.findAll(html).mapNotNull { decode(it.groupValues[1]) }
        val plain = DIRECT.findAll(html).map { it.value }
        val link = (fromAtob + plain).firstOrNull { it.startsWith("http") && isVideo(it) } ?: return null
        val origin = URI(url).let { "${it.scheme}://${it.host}" }
        val stream = DirectStream(link, typeOf(link), mapOf("Referer" to "$origin/", "Origin" to origin))
        return stream.takeIf { playable(it) }
    }

    private suspend fun mp4upload(url: String, referer: String?): DirectStream? {
        val html = fetch(url, referer)
        val link = MP4UPLOAD_SRC.find(html)?.groupValues?.get(1) ?: return null
        val origin = URI(url).let { "${it.scheme}://${it.host}" }
        return DirectStream(link, StreamType.MP4, mapOf("Referer" to "$origin/")).takeIf { playable(it) }
    }

    /**
     * MP4: que el archivo responda. HLS: que la lista lo sea (si es maestra, la primera calidad) y
     * que el primer trozo (o el de inicio, `EXT-X-MAP`) llegue con las cabeceras del servidor.
     */
    private suspend fun playable(stream: DirectStream): Boolean {
        if (stream.type == StreamType.MP4) return probe(stream.url, stream.headers)
        val referer = stream.headers["Referer"]
        var url = stream.url
        var text = fetch(url, referer)
        if (!text.trimStart().startsWith("#EXTM3U")) return false
        if ("#EXT-X-STREAM-INF" in text) {
            url = resolve(url, firstUri(text) ?: return false) ?: return false
            text = fetch(url, referer)
            if (!text.trimStart().startsWith("#EXTM3U")) return false
        }
        val first = MAP_URI.find(text)?.groupValues?.get(1) ?: firstUri(text) ?: return false
        return probe(resolve(url, first) ?: return false, stream.headers)
    }

    companion object {
        private val ZILLA_ID = Regex("""/(?:play|m3u8)/([A-Za-z0-9]+)""")
        private val ATOB = Regex("""atob\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*\)""")
        private val DIRECT = Regex("""https?://[^'"\s<>\\]+\.(?:m3u8|mp4)(?:\?[^'"\s<>\\]*)?""")
        private val MP4UPLOAD_SRC = Regex("""src\s*:\s*"(https?://[^"]+\.mp4[^"]*)"""")
        private val MAP_URI = Regex("""#EXT-X-MAP:.*?URI="([^"]+)"""")

        /** Primera línea que no es comentario: un trozo de video o, en una lista maestra, una calidad. */
        private fun firstUri(playlist: String) = playlist.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }

        private fun resolve(base: String, ref: String) = runCatching { URI(base).resolve(ref.trim()).toString() }.getOrNull()

        fun typeOf(url: String): StreamType = if (url.substringBefore('?').endsWith(".mp4")) StreamType.MP4 else StreamType.HLS

        private fun isVideo(url: String) = url.substringBefore('?').let { it.endsWith(".m3u8") || it.endsWith(".mp4") }

        private fun decode(base64: String): String? =
            runCatching { String(Base64.getDecoder().decode(base64), Charsets.UTF_8).trim() }.getOrNull()

        private fun host(url: String) = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    }
}
