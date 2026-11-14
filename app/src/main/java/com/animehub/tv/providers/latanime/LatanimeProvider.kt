package com.animehub.tv.providers.latanime

import com.animehub.tv.data.config.ConfigException
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.Canonical
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterGroup
import com.animehub.tv.domain.model.FilterOption
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.domain.model.PagedResult
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.providers.common.ShowcaseParser
import com.animehub.tv.providers.common.SiteSupport
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * Proveedor de Latanime (engine = "latanime").
 * - Directorio `/animes` con filtros combinables (año, género, letra, categoría): sin Cloudflare.
 * - `/emision` para "En emisión".
 * - La búsqueda por texto (`/buscar`) está protegida por un desafío de Cloudflare: lanza
 *   SiteProtectedException hasta que el usuario pase la verificación (Fase 7).
 */
class LatanimeProvider(site: EffectiveSite, private val fetcher: HtmlFetcher) : SiteProvider {

    private val s = SiteSupport(site)
    private val parser = LatanimeParser(s)
    private val config = site.config
    private val showcaseParser = ShowcaseParser(s)

    override val id: String = site.id
    override val name: String = site.name
    override val baseUrl: String = site.baseUrl

    override suspend fun latestEpisodes(): List<EpisodeCard> =
        parser.latest(document(s.url(config.latest["path"] ?: "/"), LATEST_CACHE))

    override suspend fun showcase(): List<ShowcaseItem> =
        showcaseParser.load(config.showcase, id) { path -> document(s.url(path), LATEST_CACHE) }

    override suspend fun search(query: String, filters: FilterSelection, page: Int): PagedResult<AnimeCard> {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val search = config.search ?: throw ConfigException("${config.name} no tiene búsqueda configurada")
            val url = s.url(search.path) + "?" + search.queryParam + "=" + enc(q) + pageSuffix(page, "&")
            val result = parser.list(document(url, SEARCH_CACHE), page)
            return if (filters.isEmpty) result else result.copy(note = "$name no combina texto y filtros: se buscó solo el texto")
        }
        // "En emisión" es otra página; el resto de filtros van al directorio y se combinan.
        val status = filters.valuesOf("status").firstOrNull()
        if (status != null) {
            val result = parser.list(document(s.url(status) + pageSuffix(page, "?"), SEARCH_CACHE), page)
            val others = filters.values.keys - "status"
            return if (others.isEmpty()) result else result.copy(note = "$name no combina \"En emisión\" con otros filtros")
        }
        val browse = config.browse ?: throw ConfigException("${config.name} no tiene directorio configurado")
        val empty = browse.emptyValue ?: ""
        val params = config.filters.filter { it.param != null }.joinToString("&") { f ->
            f.param + "=" + enc(filters.valuesOf(f.id).firstOrNull() ?: empty)
        }
        val pageParam = browse.pageParam ?: "p"
        val url = s.url(browse.path) + "?" + params + (if (page > 1) "&$pageParam=$page" else "")
        return parser.list(document(url, SEARCH_CACHE), page)
    }

    override suspend fun availableFilters(): List<FilterGroup> {
        val pages = mutableMapOf<String, Document>()
        return config.filters.mapNotNull { f ->
            val options = when (f.source) {
                "html" -> {
                    val select = f.select ?: return@mapNotNull null
                    val page = f.page ?: config.browse?.path ?: "/"
                    val doc = pages.getOrPut(page) { document(s.url(page), FILTERS_CACHE) }
                    parser.selectOptions(doc, select, config.browse?.emptyValue, f.canonicalMap)
                }
                else -> f.options.map { FilterOption(it.value, it.label, it.canonical ?: Canonical.of(it.label)) }
            }
            options.takeIf { it.isNotEmpty() }?.let { FilterGroup(f.id, f.label, f.multi, it) }
        }
    }

    override suspend fun animeDetail(url: String): AnimeDetail = parser.detail(document(s.rebase(url), DETAIL_CACHE), url)

    override suspend fun servers(episodeUrl: String): List<VideoServer> = parser.servers(document(s.rebase(episodeUrl)))

    private fun pageSuffix(page: Int, sep: String) = if (page > 1) "${sep}p=$page" else ""
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private suspend fun document(url: String, cacheMs: Long = 0): Document = Jsoup.parse(fetcher.get(id, url, cacheMs = cacheMs), url)

    private companion object {
        const val LATEST_CACHE = 5 * 60_000L
        const val SEARCH_CACHE = 2 * 60_000L
        const val DETAIL_CACHE = 10 * 60_000L
        const val FILTERS_CACHE = 60 * 60_000L
    }
}
