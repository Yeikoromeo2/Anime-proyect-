package com.animehub.tv.providers.common

import com.animehub.tv.data.config.ShowcaseConfig
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.ShowcaseKind
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Lee las secciones de la portada de un sitio (recomendados, series recientes…) solo con los
 * selectores de la configuración, así sirve para cualquier motor.
 */
class ShowcaseParser(private val s: SiteSupport) {

    /** Carga todas las secciones configuradas, pidiendo cada página una sola vez. */
    suspend fun load(configs: List<ShowcaseConfig>, siteId: String, page: suspend (path: String) -> Document): List<ShowcaseItem> =
        configs.groupBy { it.path }.flatMap { (path, sections) ->
            val doc = page(path)
            sections.flatMap { parse(doc, it, siteId) }
        }

    fun parse(doc: Document, config: ShowcaseConfig, siteId: String): List<ShowcaseItem> {
        val kind = ShowcaseKind.from(config.kind) ?: return emptyList()
        return doc.select(config.card).mapNotNull { card -> item(card, config, kind, siteId) }
            .distinctBy { it.card.url }
            .take(config.limit)
    }

    private fun item(card: Element, config: ShowcaseConfig, kind: ShowcaseKind, siteId: String): ShowcaseItem? {
        val link = if (config.link.isBlank()) {
            if (card.hasAttr("href")) card else card.selectFirst("a[href]")
        } else {
            card.selectFirst(config.link)
        }
        val href = link?.attr("href")?.takeIf { it.isNotBlank() } ?: return null
        val titleElement = card.selectFirst(config.title)
        val title = SiteSupport.clean(if (config.titleAttr.isBlank()) titleElement?.text() else titleElement?.attr(config.titleAttr))
            .takeIf { it.isNotEmpty() } ?: return null
        val date = text(card, config.date)
        val rating = text(card, config.rating)?.takeIf { (it.replace(',', '.').toFloatOrNull() ?: 0f) > 0f }
        return ShowcaseItem(
            card = AnimeCard(
                siteId = siteId,
                title = title,
                url = s.absolute(href),
                coverUrl = s.cover(card.selectFirst(config.image)),
                year = SiteSupport.year(text(card, config.year) ?: date),
            ),
            kind = kind,
            synopsis = text(card, config.synopsis),
            rating = rating,
            dateLabel = date,
        )
    }

    private fun text(card: Element, selector: String): String? =
        selector.takeIf { it.isNotBlank() }?.let { SiteSupport.clean(card.selectFirst(it)?.text()) }?.takeIf { it.isNotEmpty() }
}
