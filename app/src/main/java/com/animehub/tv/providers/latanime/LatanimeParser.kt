package com.animehub.tv.providers.latanime

import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.AnimeStatus
import com.animehub.tv.domain.model.Canonical
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterOption
import com.animehub.tv.domain.model.PagedResult
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.providers.common.SiteSupport
import com.animehub.tv.providers.common.SiteSupport.Companion.clean
import org.jsoup.nodes.Document
import java.util.Base64

/** Lectura del HTML de Latanime (selectores en la configuración; ver docs/sites/latanime.md). */
class LatanimeParser(private val s: SiteSupport) {

    private val siteId get() = s.site.id
    private val latestCfg get() = s.site.config.latest
    private val detailCfg get() = s.site.config.detail

    /** Inicio → "Añadidos recientemente". El título trae el número: "Episodio 12 - Nombre". */
    fun latest(doc: Document): List<EpisodeCard> {
        val titleRegex = s.optional(latestCfg, "titleRegex")?.let(::Regex)
        val pattern = s.optional(latestCfg, "animeUrlPattern")?.let(::Regex)
        val replacement = s.optional(latestCfg, "animeUrlReplacement")
        return doc.select(s.latest("card")).mapNotNull { card ->
            val episodeUrl = s.absolute(card.attr("href").ifBlank { return@mapNotNull null })
            val rawTitle = clean(card.selectFirst(s.latest("title"))?.text())
            val match = titleRegex?.find(rawTitle)
            EpisodeCard(
                siteId = siteId,
                animeTitle = match?.groupValues?.get(2) ?: rawTitle,
                episodeNumber = match?.groupValues?.get(1)?.toFloatOrNull(),
                episodeUrl = episodeUrl,
                imageUrl = s.cover(card.selectFirst(s.latest("image"))),
                animeUrl = if (pattern != null && replacement != null && pattern.containsMatchIn(episodeUrl)) {
                    episodeUrl.replace(pattern, replacement)
                } else null,
                language = card.selectFirst(s.latest("language"))?.text()?.let(::clean)?.ifEmpty { null },
                dateLabel = s.optional(latestCfg, "date")?.let { card.selectFirst(it)?.text() }?.let(::clean),
            )
        }
    }

    /** Directorio `/animes`, `/emision` y (si se puede ver) `/buscar`: misma tarjeta. */
    fun list(doc: Document, page: Int): PagedResult<AnimeCard> {
        val items = doc.select(s.list("card")).mapNotNull { card ->
            val href = card.attr("href").ifBlank { return@mapNotNull null }
            AnimeCard(
                siteId = siteId,
                title = clean(card.selectFirst(s.list("title"))?.text()),
                url = s.absolute(href),
                coverUrl = s.cover(card.selectFirst(s.list("cover"))),
                year = SiteSupport.year(card.selectFirst(s.list("year"))?.text()),
                language = card.selectFirst(s.list("language"))?.text()?.let(::clean)?.ifEmpty { null },
            )
        }
        val next = Regex("""[?&]p=(\d+)""")
        val hasNext = doc.select(s.list("nextPage")).any { a ->
            next.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()?.let { it > page } == true
        }
        return PagedResult(items, page, hasNext)
    }

    fun detail(doc: Document, url: String): AnimeDetail {
        val numberRegex = s.optional(detailCfg, "episodeNumberRegex")?.let(::Regex)
        val episodes = doc.select(s.detail("episodes")).mapNotNull { a ->
            val epUrl = s.absolute(a.attr("href").ifBlank { return@mapNotNull null })
            val number = numberRegex?.find(epUrl)?.groupValues?.get(1)?.toFloatOrNull()
                ?: Regex("""(\d+(?:\.\d+)?)\s*$""").find(clean(a.text()))?.groupValues?.get(1)?.toFloatOrNull()
                ?: return@mapNotNull null
            Episode(
                number = number,
                title = clean(a.text()),
                url = epUrl,
                imageUrl = s.optional(detailCfg, "episodeImage")?.let { s.cover(a.selectFirst(it)) },
            )
        }
        val statusEl = doc.selectFirst(s.detail("status"))
        val status = when {
            statusEl == null -> AnimeStatus.DESCONOCIDO
            statusEl.hasClass(detailCfg["statusAiring"] ?: "estreno") -> AnimeStatus.EMISION
            statusEl.hasClass(detailCfg["statusFinished"] ?: "finalizado") -> AnimeStatus.FINALIZADO
            else -> AnimeStatus.DESCONOCIDO
        }
        return AnimeDetail(
            siteId = siteId,
            url = url,
            title = clean(doc.selectFirst(s.detail("title"))?.text()),
            coverUrl = s.cover(doc.selectFirst(s.detail("cover"))),
            synopsis = doc.selectFirst(s.detail("synopsis"))?.text()?.let(::clean)?.ifEmpty { null },
            alternativeTitle = s.optional(detailCfg, "altTitle")?.let { doc.selectFirst(it)?.text() }?.let(::clean)?.ifEmpty { null },
            genres = doc.select(s.detail("genres")).map { clean(it.text()) }.filter { it.isNotEmpty() }.distinct(),
            status = status,
            year = SiteSupport.year(s.optional(detailCfg, "date")?.let { doc.selectFirst(it)?.text() }),
            episodes = episodes.sortedBy { it.number },
        )
    }

    /** Servidores: enlaces con la URL del reproductor en Base64 (`data-player`). */
    fun servers(doc: Document): List<VideoServer> {
        val attr = s.server("urlAttr")
        return doc.select(s.server("item")).mapNotNull { a ->
            val encoded = a.attr(attr).ifBlank { return@mapNotNull null }
            val url = runCatching { String(Base64.getDecoder().decode(encoded.trim()), Charsets.UTF_8) }.getOrNull()
                ?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            VideoServer(name = clean(a.text()), embedUrl = url)
        }
    }

    /** Opciones de un `<select>` de filtros (sin la opción "Seleccionar"). */
    fun selectOptions(doc: Document, selector: String, emptyValue: String?, canonicalMap: Map<String, String>): List<FilterOption> =
        doc.selectFirst(selector)?.select("option").orEmpty().mapNotNull { o ->
            val value = o.attr("value")
            if (value.isBlank() || value == emptyValue) return@mapNotNull null
            val label = clean(o.text())
            FilterOption(value, label, canonicalMap[value] ?: Canonical.of(label))
        }
}
