package com.animehub.tv.providers.jkanime

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * Proveedor de JKAnime (engine = "jkanime").
 * - Búsqueda `/buscar/{texto}/` (hasta 30 resultados).
 * - Directorio `/directorio?genero=…&fecha=…&p=N` con filtros combinables; los resultados vienen en JSON.
 * - Episodios `{ficha}/{número}/`; servidores en el script de la página.
 * jkanime.net pide la verificación de Cloudflare; se usa jkanime.org, que abre sin ella.
 */
class JkanimeProvider(site: EffectiveSite, private val fetcher: HtmlFetcher) : SiteProvider {

    private val s = SiteSupport(site)
    private val parser = JkanimeParser(s)
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
            val path = search.path.replace("{query}", URLEncoder.encode(q, "UTF-8").replace("+", "%20"))
            val result = parser.search(document(s.url(path), SEARCH_CACHE), page)
            return if (filters.isEmpty) result else result.copy(note = "$name no combina texto y filtros: se buscó solo el texto")
        }
        val browse = config.browse ?: throw ConfigException("${config.name} no tiene directorio configurado")
        val params = config.filters.filter { it.param != null }.mapNotNull { f ->
            filters.valuesOf(f.id).firstOrNull()?.let { f.param + "=" + URLEncoder.encode(it, "UTF-8") }
        } + listOfNotNull(if (page > 1) "${browse.pageParam ?: "p"}=$page" else null)
        val url = s.url(browse.path) + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return parser.directory(fetcher.get(id, url, cacheMs = SEARCH_CACHE), page)
    }

    override suspend fun availableFilters(): List<FilterGroup> {
        val pages = mutableMapOf<String, Document>()
        return config.filters.mapNotNull { f ->
            val options = when (f.source) {
                "html" -> {
                    val select = f.select ?: return@mapNotNull null
                    val page = f.page ?: config.browse?.path ?: "/"
                    val doc = pages.getOrPut(page) { document(s.url(page), FILTERS_CACHE) }
                    parser.selectOptions(doc, select, f.canonicalMap)
                }
                else -> f.options.map { FilterOption(it.value, it.label, it.canonical ?: Canonical.of(it.label)) }
            }
            options.takeIf { it.isNotEmpty() }?.let { FilterGroup(f.id, f.label, f.multi, it) }
        }
    }

    override suspend fun animeDetail(url: String): AnimeDetail = parser.detail(document(s.rebase(url), DETAIL_CACHE), url)

    override suspend fun servers(episodeUrl: String): List<VideoServer> = parser.servers(document(s.rebase(episodeUrl)))

    override suspend fun episodeThumbnails(animeUrl: String, numbers: List<Float>): Map<Float, String> {
        val url = s.rebase(animeUrl)
        val api = parser.episodesApi(document(url, DETAIL_CACHE)) ?: return emptyMap()
        // 16 episodios por página: solo las páginas de los episodios que se ven (como mucho 4 a la vez).
        val pages = numbers.filter { it >= 1f }.map { ((it.toInt() - 1) / EPISODES_PER_PAGE) + 1 }.distinct().take(MAX_THUMB_PAGES)
        return coroutineScope {
            pages.map { page ->
                async {
                    runCatching {
                        parser.thumbnails(fetcher.postForm(id, s.url("/ajax/episodes/${api.animeId}/$page"), mapOf("_token" to api.token), referer = url), api.thumbBase)
                    }.getOrDefault(emptyMap())
                }
            }.awaitAll().fold(emptyMap()) { all, page -> all + page }
        }
    }

    private suspend fun document(url: String, cacheMs: Long = 0): Document = Jsoup.parse(fetcher.get(id, url, cacheMs = cacheMs), url)

    private companion object {
        const val LATEST_CACHE = 5 * 60_000L
        const val SEARCH_CACHE = 2 * 60_000L
        const val DETAIL_CACHE = 10 * 60_000L
        const val FILTERS_CACHE = 60 * 60_000L
        const val EPISODES_PER_PAGE = 16
        const val MAX_THUMB_PAGES = 4
    }
}
