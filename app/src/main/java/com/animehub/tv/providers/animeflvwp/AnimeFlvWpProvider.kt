package com.animehub.tv.providers.animeflvwp

import com.animehub.tv.data.config.ConfigException
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterGroup
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
 * Proveedor de animeflv.or.at (engine = "animeflv-wp"), copia no oficial de AnimeFLV.
 * - Búsqueda `/?s=texto` y catálogo completo `/anime/page/N/`.
 * - El sitio no tiene filtros que funcionen (sus páginas de género salen vacías): solo "Todo el catálogo".
 */
class AnimeFlvWpProvider(site: EffectiveSite, private val fetcher: HtmlFetcher) : SiteProvider {

    private val s = SiteSupport(site)
    private val parser = AnimeFlvWpParser(s)
    private val showcaseParser = ShowcaseParser(s)
    private val config = site.config

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
            val url = s.url(search.path) + "?" + search.queryParam + "=" + URLEncoder.encode(q, "UTF-8")
            val result = parser.search(document(url, SEARCH_CACHE), page)
            return if (filters.isEmpty) result else result.copy(note = "$name no tiene filtros: se buscó solo el texto")
        }
        val browse = config.browse ?: throw ConfigException("${config.name} no tiene catálogo configurado")
        val path = if (page > 1) browse.path.trimEnd('/') + (browse.pageParam ?: "/page/{page}/").replace("{page}", page.toString()) else browse.path
        val result = parser.catalog(document(s.url(path), SEARCH_CACHE), page)
        return if (filters.isEmpty) result else result.copy(note = "$name no tiene filtros: se muestra todo el catálogo")
    }

    override suspend fun availableFilters(): List<FilterGroup> = emptyList()

    override suspend fun animeDetail(url: String): AnimeDetail = parser.detail(document(s.rebase(url), DETAIL_CACHE), url)

    override suspend fun servers(episodeUrl: String): List<VideoServer> = parser.servers(document(s.rebase(episodeUrl)))

    private suspend fun document(url: String, cacheMs: Long = 0): Document = Jsoup.parse(fetcher.get(id, url, cacheMs = cacheMs), url)

    private companion object {
        const val LATEST_CACHE = 5 * 60_000L
        const val SEARCH_CACHE = 2 * 60_000L
        const val DETAIL_CACHE = 10 * 60_000L
    }
}
