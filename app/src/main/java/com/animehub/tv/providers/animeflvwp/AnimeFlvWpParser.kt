package com.animehub.tv.providers.animeflvwp

import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.PagedResult
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.providers.common.SiteSupport
import com.animehub.tv.providers.common.SiteSupport.Companion.clean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Base64

/**
 * Lectura de animeflv.or.at (WordPress con un tema propio que imita a AnimeFLV).
 * Selectores en la configuración; ver docs/sites/animeflv.md.
 */
class AnimeFlvWpParser(private val s: SiteSupport) {

    private val siteId get() = s.site.id
    private val config get() = s.site.config

    /**
     * Inicio → "Últimos episodios". El título trae el número ("Tank Chair Episodio 6"). La ficha
     * sale de la clase `category-{serie}` de la tarjeta; si no está, de la URL del episodio.
     */
    fun latest(doc: Document): List<EpisodeCard> {
        val titleRegex = s.optional(config.latest, "titleRegex")?.let(::Regex)
        val prefix = s.optional(config.latest, "categoryPrefix")
        val ignore = s.optional(config.latest, "categoryIgnore")
        val animePath = s.optional(config.latest, "animePath")
        val pattern = s.optional(config.latest, "animeUrlPattern")?.let(::Regex)
        val replacement = s.optional(config.latest, "animeUrlReplacement")
        return doc.select(s.latest("card")).mapNotNull { card ->
            val link = card.selectFirst(s.latest("link")) ?: return@mapNotNull null
            val episodeUrl = s.absolute(link.attr("href").ifBlank { return@mapNotNull null })
            val rawTitle = clean(card.selectFirst(s.latest("title"))?.text())
            val match = titleRegex?.find(rawTitle)
            val badge = s.optional(config.latest, "episode")?.let { card.selectFirst(it)?.text() }
            val slug = if (prefix != null) card.classNames().firstOrNull { it.startsWith(prefix) && it != ignore }?.removePrefix(prefix) else null
            val animeUrl = when {
                slug != null && animePath != null -> s.url(animePath.replace("{slug}", slug))
                pattern != null && replacement != null && pattern.containsMatchIn(episodeUrl) -> episodeUrl.replace(pattern, replacement)
                else -> null
            }
            EpisodeCard(
                siteId = siteId,
                animeTitle = (match?.groupValues?.get(1) ?: rawTitle).ifEmpty { return@mapNotNull null },
                episodeNumber = match?.groupValues?.get(2)?.toFloatOrNull() ?: NUMBER.find(badge.orEmpty())?.value?.toFloatOrNull(),
                episodeUrl = episodeUrl,
                imageUrl = s.cover(card.selectFirst(s.latest("image"))),
                animeUrl = animeUrl,
            )
        }.distinctBy { it.episodeUrl }
    }

    /** Búsqueda (`/?s=`): tarjetas de series, sin más páginas. */
    fun search(doc: Document, page: Int): PagedResult<AnimeCard> =
        PagedResult(cards(doc, s.list("searchCard")), page, hasNextPage = false)

    /** Catálogo completo (`/anime/`, `/anime/page/N/`), 18 por página. */
    fun catalog(doc: Document, page: Int): PagedResult<AnimeCard> =
        PagedResult(cards(doc, s.list("card")), page, hasNextPage = doc.selectFirst(s.list("nextPage")) != null)

    private fun cards(doc: Document, selector: String): List<AnimeCard> =
        doc.select(selector).mapNotNull { card ->
            val link = card.selectFirst(s.list("link")) ?: return@mapNotNull null
            AnimeCard(
                siteId = siteId,
                title = clean(card.selectFirst(s.list("title"))?.text()).ifEmpty { return@mapNotNull null },
                url = s.absolute(link.attr("href")),
                coverUrl = s.cover(card.selectFirst(s.list("cover"))),
            )
        }.distinctBy { it.url }

    /** Ficha: los episodios vienen en un JSON (`[{permalink, number}]`) dentro de la página. */
    fun detail(doc: Document, url: String): AnimeDetail {
        val episodes = s.optional(config.detail, "episodesJson")?.let { doc.selectFirst(it)?.data() }?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull()
        }.orEmpty().mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val link = (o["permalink"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val number = (o["number"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: return@mapNotNull null
            Episode(number = number, title = "Episodio ${number.toString().removeSuffix(".0")}", url = s.absolute(link))
        }
        return AnimeDetail(
            siteId = siteId,
            url = url,
            title = clean(doc.selectFirst(s.detail("title"))?.text()),
            coverUrl = s.cover(doc.selectFirst(s.detail("cover"))),
            synopsis = doc.select(s.detail("synopsis")).joinToString(" ") { it.text() }.let(::clean).ifEmpty { null },
            genres = doc.select(s.detail("genres")).map { clean(it.text()) }.filter { it.isNotEmpty() }.distinct(),
            episodes = episodes.distinctBy { it.number }.sortedBy { it.number },
        )
    }

    /** Servidores: un botón por opción con la URL en Base64 (`data-src`) y el nombre en su etiqueta. */
    fun servers(doc: Document): List<VideoServer> {
        val urlAttr = s.server("urlAttr")
        val languageAttr = s.optional(config.servers, "languageAttr")
        return doc.select(s.server("item")).mapNotNull { item: Element ->
            val button = item.selectFirst(s.server("button")) ?: return@mapNotNull null
            val url = decode(button.attr(urlAttr))?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val name = clean(item.selectFirst(s.server("name"))?.text()).ifEmpty { clean(button.text()) }
            VideoServer(name = name, embedUrl = url, language = languageAttr?.let { languageLabel(button.attr(it)) })
        }
    }

    private fun decode(base64: String): String? =
        runCatching { String(Base64.getDecoder().decode(base64.trim()), Charsets.UTF_8).trim() }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val NUMBER = Regex("""\d+(?:\.\d+)?""")

        fun languageLabel(raw: String): String? = when (raw.trim().lowercase()) {
            "" -> null
            "sub" -> "Subtitulado"
            "lat", "latino" -> "Latino"
            "cast", "castellano" -> "Castellano"
            else -> raw.trim()
        }
    }
}
