package com.animehub.tv.extractors

import com.animehub.tv.domain.model.VideoServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.util.Base64

/**
 * Páginas "multiservidor" (Anime Ninja las usa como único reproductor): se abren en sus
 * servidores reales para poder elegir y comprobar cada uno. Ver docs/sites/animeninja.md.
 */
object AggregatorParsers {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * darkanimes.com: botones `button[data-url]` con la URL en Base64 cifrada con XOR usando la
     * clave `pk` del JSON de configuración de la página. Nombre en `.msp-server-name`.
     */
    fun mspXor(html: String): List<VideoServer> {
        val doc = Jsoup.parse(html)
        val config = doc.select("script").map { it.data().trim() }.firstOrNull { it.startsWith("{") && it.contains("\"pk\"") }
            ?: return emptyList()
        val key = runCatching { json.parseToJsonElement(config).jsonObject["pk"]?.jsonPrimitive?.content }.getOrNull()
            ?.toByteArray() ?: return emptyList()
        if (key.isEmpty()) return emptyList()
        return doc.select("button[data-url]").mapNotNull { button ->
            val raw = runCatching { Base64.getDecoder().decode(button.attr("data-url").trim()) }.getOrNull() ?: return@mapNotNull null
            val url = String(ByteArray(raw.size) { i -> (raw[i].toInt() xor key[i % key.size].toInt()).toByte() }, Charsets.UTF_8)
            if (!url.startsWith("http")) return@mapNotNull null
            val name = button.selectFirst(".msp-server-name")?.text()?.trim()?.ifEmpty { null } ?: button.text().trim()
            val desc = button.selectFirst(".msp-server-desc")?.text()?.lowercase().orEmpty()
            VideoServer(name = name, embedUrl = url, language = languageFrom(desc))
        }
    }

    /** animeav1.com: objeto JS `embeds:{SUB:[{server:"Voe",url:"…"}],DUB:[…]}`. */
    fun embedsObject(html: String): List<VideoServer> {
        val start = html.indexOf("embeds:{")
        if (start < 0) return emptyList()
        val end = html.indexOf("downloads:", start).takeIf { it > 0 } ?: (start + 20_000).coerceAtMost(html.length)
        val block = html.substring(start, end)
        val sections = Regex("""(SUB|DUB):\[(.*?)\]""").findAll(block)
        val item = Regex("""\{server:"([^"]+)",url:"([^"]+)"\}""")
        return sections.flatMap { section ->
            val lang = if (section.groupValues[1] == "DUB") "Doblado" else "Subtitulado"
            item.findAll(section.groupValues[2]).map { m ->
                VideoServer(name = m.groupValues[1], embedUrl = m.groupValues[2].replace("\\/", "/"), language = lang)
            }
        }.toList()
    }

    private fun languageFrom(description: String): String? = when {
        "latino" in description -> "Latino"
        "castellano" in description -> "Castellano"
        "sub" in description -> "Subtitulado"
        else -> null
    }
}
