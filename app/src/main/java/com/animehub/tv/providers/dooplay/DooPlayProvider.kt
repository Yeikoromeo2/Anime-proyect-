package com.animehub.tv.providers.dooplay

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * Proveedor para sitios WordPress con el tema DooPlay (Anime Ninja; AnimeLHD redirige ahí).
 * Las rutas y selectores salen de la configuración remota (engine = "dooplay").
 */
class DooPlayProvider(site: EffectiveSite, private val fetcher: HtmlFetcher) : SiteProvider {

    private val s = SiteSupport(site)
    private val parser = DooPlayParser(s)
    private val config = site.config
    private val showcaseParser = ShowcaseParser(s)

    override val id: String = site.id
    override val name: String = site.name
    override val baseUrl: String = site.baseUrl

    override suspend fun latestEpisodes(): List<EpisodeCard> =
        parser.latest(document(s.url(config.latest["path"] ?: "/"), cacheMs = LATEST_CACHE))

    override suspend fun showcase(): List<ShowcaseItem> =
        showcaseParser.load(config.showcase, id) { path -> document(s.url(path), cacheMs = LATEST_CACHE) }

    override suspend fun search(query: String, filters: FilterSelection, page: Int): PagedResult<AnimeCard> {
        val q = query.trim()
        if (q.isEmpty()) return browse(filters, page)
        val search = config.search ?: throw ConfigException("${config.name} no tiene búsqueda configurada")
        val path = if (page > 1 && search.pagePath != null) search.pagePath.replace("{page}", page.toString()) else search.path
        val url = s.url(path) + "?" + search.queryParam + "=" + URLEncoder.encode(q, "UTF-8")
        return parser.search(document(url, cacheMs = SEARCH_CACHE), page)
    }

    /** Sin texto: lista el archivo del género o del tipo elegido (DooPlay no combina filtros). */
    private suspend fun browse(filters: FilterSelection, page: Int): PagedResult<AnimeCard> {
        val genre = filters.valuesOf("genre").firstOrNull()
        val genreTemplate = config.filters.firstOrNull { it.id == "genre" }?.pathTemplate
        val type = filters.valuesOf("type").firstOrNull()
        val base = when {
            genre != null && genreTemplate != null -> genreTemplate.replace("{value}", genre)
            type != null -> type
            else -> config.filters.firstOrNull { it.id == "type" }?.options?.firstOrNull()?.value ?: "/anime/"
        }
        val path = if (page > 1) base.trimEnd('/') + "/page/$page/" else base
        val result = parser.archive(document(s.url(path), cacheMs = SEARCH_CACHE), page)
        // DooPlay no combina filtros: si se eligieron género y tipo, se usa solo el género.
        val ignored = genre != null && genreTemplate != null && type != null
        return if (ignored) result.copy(note = "$name no combina filtros: se usó solo el género") else result
    }

    override suspend fun availableFilters(): List<FilterGroup> = config.filters.mapNotNull { f ->
        val options = when (f.source) {
            "html" -> f.links?.let { links -> parser.genres(document(s.url(f.page ?: "/"), cacheMs = FILTERS_CACHE), links) }.orEmpty()
            else -> f.options.map { FilterOption(it.value, it.label, it.canonical ?: Canonical.of(it.label)) }
        }
        options.takeIf { it.isNotEmpty() }?.let { FilterGroup(f.id, f.label, f.multi, it) }
    }

    override suspend fun animeDetail(url: String): AnimeDetail =
        parser.detail(document(s.rebase(url), cacheMs = DETAIL_CACHE), url)

    override suspend fun servers(episodeUrl: String): List<VideoServer> {
        val pageUrl = s.rebase(episodeUrl)
        val options = parser.playerOptions(document(pageUrl))
        val ajax = s.url(s.server("ajaxPath"))
        val action = s.server("action")
        return options.mapNotNull { option ->
            val response = fetcher.postForm(
                id, ajax,
                mapOf("action" to action, "post" to option.post, "nume" to option.nume, "type" to option.type),
                referer = pageUrl,
            )
            val embed = runCatching { json.parseToJsonElement(response).jsonObject["embed_url"]?.jsonPrimitive?.content }
                .getOrNull()?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            VideoServer(name = option.label, embedUrl = embed)
        }
    }

    private suspend fun document(url: String, cacheMs: Long = 0): Document =
        Jsoup.parse(fetcher.get(id, url, cacheMs = cacheMs), url)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val LATEST_CACHE = 5 * 60_000L
        const val SEARCH_CACHE = 2 * 60_000L
        const val DETAIL_CACHE = 10 * 60_000L
        const val FILTERS_CACHE = 60 * 60_000L
    }
}
