package com.animehub.tv.providers.jkanime

import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.AnimeStatus
import com.animehub.tv.domain.model.AnimeType
import com.animehub.tv.domain.model.Canonical
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterOption
import com.animehub.tv.domain.model.PagedResult
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.providers.common.SiteSupport
import com.animehub.tv.providers.common.SiteSupport.Companion.clean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.util.Base64

/** Lectura del HTML de JKAnime (selectores en la configuración; ver docs/sites/jkanime.md). */
class JkanimeParser(private val s: SiteSupport) {

    /** Lo necesario para pedir las miniaturas de los episodios: id del anime, clave de la sesión y carpeta del CDN. */
    data class EpisodesApi(val animeId: String, val token: String, val thumbBase: String)

    /**
     * La ficha carga los episodios aparte (POST `/ajax/episodes/{id}/{página}` con la clave
     * `csrf-token`). Las miniaturas están en el CDN, en la carpeta de la portada con
     * `/animes/video/image_thumb/` en lugar de `/animes/image/`, como hace la web.
     */
    fun episodesApi(doc: Document): EpisodesApi? {
        val html = doc.html()
        val id = Regex("""ajax/episodes/(\d+)/""").find(html)?.groupValues?.get(1) ?: return null
        val token = doc.selectFirst("meta[name=csrf-token]")?.attr("content")?.takeIf { it.isNotBlank() } ?: return null
        val cover = doc.selectFirst(".anime_pic img")?.attr("abs:src")?.takeIf { "/animes/image/" in it } ?: return null
        val base = cover.replace("/animes/image/", "/animes/video/image_thumb/").substringBeforeLast('/') + "/"
        return EpisodesApi(id, token, base)
    }

    /** Respuesta de `/ajax/episodes`: número de episodio → miniatura. */
    fun thumbnails(json: String, thumbBase: String): Map<Float, String> =
        runCatching {
            Json.parseToJsonElement(json).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val number = obj["number"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return@mapNotNull null
                val image = obj["image"]?.jsonPrimitive?.content?.takeIf { it.length > 10 } ?: return@mapNotNull null
                number to (if (image.startsWith("http")) image else thumbBase + image)
            }.toMap()
        }.getOrDefault(emptyMap())

    private val siteId get() = s.site.id
    private val config get() = s.site.config

    /** Inicio → pestaña "Animes" de "Programación" (no se leen las de OVAs ni las de adultos). */
    fun latest(doc: Document): List<EpisodeCard> {
        val pattern = s.optional(config.latest, "animeUrlPattern")?.let(::Regex)
        val replacement = s.optional(config.latest, "animeUrlReplacement")
        return doc.select(s.latest("card")).mapNotNull { a ->
            val episodeUrl = s.absolute(a.attr("href").ifBlank { return@mapNotNull null })
            val badge = s.optional(config.latest, "episode")?.let { a.selectFirst(it)?.text() }
            EpisodeCard(
                siteId = siteId,
                animeTitle = clean(a.selectFirst(s.latest("title"))?.text()).ifEmpty { return@mapNotNull null },
                episodeNumber = NUMBER.find(badge.orEmpty())?.value?.toFloatOrNull() ?: LAST_NUMBER.find(episodeUrl)?.groupValues?.get(1)?.toFloatOrNull(),
                episodeUrl = episodeUrl,
                imageUrl = s.cover(a.selectFirst(s.latest("image"))),
                animeUrl = if (pattern != null && replacement != null && pattern.containsMatchIn(episodeUrl)) episodeUrl.replace(pattern, replacement) else null,
                dateLabel = s.optional(config.latest, "date")?.let { a.selectFirst(it)?.text() }?.let(::clean)?.ifEmpty { null },
            )
        }
    }

    /** Resultados de `/buscar/{texto}/` (hasta 30, sin más páginas). */
    fun search(doc: Document, page: Int): PagedResult<AnimeCard> {
        val items = doc.select(s.list("card")).mapNotNull { card ->
            val link = card.selectFirst(s.list("link")) ?: return@mapNotNull null
            AnimeCard(
                siteId = siteId,
                title = clean(card.selectFirst(s.list("title"))?.text()).ifEmpty { return@mapNotNull null },
                url = s.absolute(link.attr("href")),
                coverUrl = s.cover(card.selectFirst(s.list("cover"))),
                type = typeOf(card.selectFirst(s.list("type"))?.text()),
            )
        }
        return PagedResult(items, page, hasNextPage = false)
    }

    /**
     * Directorio con filtros: la página trae los resultados como JSON en `var animes = {…}`
     * (título, portada, tipo y sinopsis) con `current_page` y `last_page`.
     */
    fun directory(html: String, page: Int): PagedResult<AnimeCard> {
        val marker = s.list("listJson")
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
            ?: return PagedResult(emptyList(), page, hasNextPage = false)
        val end = html.indexOf('\n', start).takeIf { it > 0 } ?: html.length
        val data = json.parseToJsonElement(html.substring(start, end).trim().removeSuffix(";")).jsonObject
        val items = (data["data"] as? JsonArray).orEmpty().mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val url = o.string("url") ?: return@mapNotNull null
            AnimeCard(
                siteId = siteId,
                title = clean(o.string("title")).ifEmpty { return@mapNotNull null },
                url = s.absolute(url),
                coverUrl = o.string("image")?.let { s.absolute(it) },
                type = typeOf(o.string("tipo")),
            )
        }
        val current = data["current_page"]?.jsonPrimitive?.intOrNull ?: page
        val last = data["last_page"]?.jsonPrimitive?.intOrNull ?: current
        return PagedResult(items, page, hasNextPage = current < last)
    }

    /**
     * Ficha. JKAnime carga la lista de episodios con JavaScript; como cada episodio es
     * `{ficha}/{número}/`, se arma del 1 al último ("Último episodio" o "Episodios: N").
     */
    fun detail(doc: Document, url: String): AnimeDetail {
        val fields = doc.select(s.detail("fields")).map { clean(it.text()) }
        fun field(name: String) = fields.firstOrNull { it.startsWith(name) }?.removePrefix(name)?.trim()
        val lastLink = s.optional(config.detail, "lastEpisode")?.let { doc.selectFirst(it)?.attr("href") }
        val last = listOfNotNull(
            lastLink?.let { LAST_NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() },
            s.optional(config.detail, "episodesField")?.let { field(it) }?.let { NUMBER.find(it)?.value?.toIntOrNull() },
        ).maxOrNull() ?: 0
        val base = s.absolute(url).trimEnd('/') + "/"
        val statusEl = doc.selectFirst(s.detail("status"))
        val status = when {
            statusEl == null -> AnimeStatus.DESCONOCIDO
            statusEl.hasClass(config.detail["statusAiring"] ?: "currently") -> AnimeStatus.EMISION
            statusEl.hasClass(config.detail["statusFinished"] ?: "finished") -> AnimeStatus.FINALIZADO
            else -> AnimeStatus.DESCONOCIDO
        }
        return AnimeDetail(
            siteId = siteId,
            url = url,
            title = clean(doc.selectFirst(s.detail("title"))?.text()),
            coverUrl = s.cover(doc.selectFirst(s.detail("cover"))),
            synopsis = doc.selectFirst(s.detail("synopsis"))?.text()?.let(::clean)?.ifEmpty { null },
            genres = doc.select(s.detail("genres")).map { clean(it.text()) }.filter { it.isNotEmpty() }.distinct(),
            status = status,
            type = typeOf(s.optional(config.detail, "type")?.let { doc.selectFirst(it)?.text() }?.substringAfter(':')),
            year = SiteSupport.year(s.optional(config.detail, "dateField")?.let { field(it) }),
            episodes = (1..last).map { n -> Episode(number = n.toFloat(), title = "Episodio $n", url = "$base$n/") },
        )
    }

    /**
     * Servidores del episodio, como los arma la propia página:
     * - `video[i] = '<iframe src="…">'` → reproductores de JKAnime (Desu, Magi…), con su nombre en el botón `data-id=i`;
     * - `var servers = [{remote: Base64, server, lang}]` → servidores externos (se omiten los de descarga).
     */
    fun servers(doc: Document): List<VideoServer> {
        val script = doc.select("script").joinToString("\n") { it.data() }
        val names = doc.select(s.server("names")).associate { it.attr("data-id") to clean(it.text()) }
        val languages = s.optional(config.servers, "languages")?.let { sel ->
            doc.select(sel).associate { it.attr("value") to languageLabel(it.text()) }
        }.orEmpty()
        val defaultLanguage = languages.values.firstOrNull()
        val own = OWN_PLAYER.findAll(script).mapNotNull { m ->
            val name = names[m.groupValues[1]] ?: return@mapNotNull null
            VideoServer(name = name, embedUrl = s.absolute(m.groupValues[2]), language = defaultLanguage)
        }.toList()
        val skip = s.optional(config.servers, "skip")?.split(',')?.map { it.trim().lowercase() }.orEmpty()
        val external = SERVERS.find(script)?.groupValues?.get(1)?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull()
        }.orEmpty().mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val name = o.string("server") ?: return@mapNotNull null
            if (name.lowercase() in skip) return@mapNotNull null
            val url = o.string("remote")?.let { decode(it) }?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val lang = o["lang"]?.jsonPrimitive?.let { runCatching { it.int.toString() }.getOrNull() }
            VideoServer(name = name, embedUrl = url, language = languages[lang] ?: defaultLanguage)
        }
        return own + external
    }

    /** Opciones de un `<select>` del directorio (los años no traen `value`: se usa el texto). */
    fun selectOptions(doc: Document, selector: String, canonicalMap: Map<String, String>): List<FilterOption> =
        doc.selectFirst(selector)?.select("option").orEmpty().mapNotNull { o ->
            val value = (if (o.hasAttr("value")) o.attr("value") else o.text()).trim()
            if (value.isEmpty()) return@mapNotNull null
            val label = clean(o.text())
            FilterOption(value, label, canonicalMap[value] ?: Canonical.of(label))
        }

    private fun decode(base64: String): String? =
        runCatching { String(Base64.getDecoder().decode(base64.trim()), Charsets.UTF_8).trim() }.getOrNull()

    private fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val NUMBER = Regex("""\d+(?:\.\d+)?""")
        val LAST_NUMBER = Regex("""/(\d+(?:\.\d+)?)/?$""")
        val OWN_PLAYER = Regex("""video\[(\d+)\]\s*=\s*'<iframe[^>]*?src="([^"]+)"""")
        val SERVERS = Regex("""var servers = (\[.*?\]);""")

        fun typeOf(text: String?): AnimeType? = when (Canonical.of(text.orEmpty())) {
            "" -> null
            "serie", "tv", "animes" -> AnimeType.TV
            "pelicula", "peliculas", "movie" -> AnimeType.PELICULA
            "ova", "ovas" -> AnimeType.OVA
            "ona", "onas" -> AnimeType.ONA
            "especial", "especiales", "special" -> AnimeType.ESPECIAL
            else -> AnimeType.OTRO
        }

        /** "Japones Sub. Español" → "Subtitulado"; "Espanol latino" → "Latino". */
        fun languageLabel(text: String): String {
            val t = Canonical.of(text)
            return when {
                "latino" in t -> "Latino"
                "castellano" in t -> "Castellano"
                "sub" in t -> "Subtitulado"
                else -> clean(text)
            }
        }
    }
}
