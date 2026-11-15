package com.animehub.tv.providers.generic

import com.animehub.tv.data.config.ConfigException
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.AnimeType
import com.animehub.tv.domain.model.Canonical
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterGroup
import com.animehub.tv.domain.model.FilterOption
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.domain.model.PagedResult
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.providers.common.SiteSupport
import com.animehub.tv.util.TitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Proveedor genérico para sitios HTML sencillos (engine = "generic-html"): catálogo paginado,
 * búsqueda, ficha (con o sin temporadas) y reproductores incrustados. Todo sale de los selectores
 * de la configuración; sirve para Retro (lacartoons) y Pelis (gnula, cinehindi).
 *
 * Claves de la configuración (todas opcionales salvo las de tarjeta y ficha):
 * - `selectors`: card, title, titleAttr, cover, link, year ("@atributo" lee un atributo de la tarjeta),
 *   language, nextPageRegex (número de página en los enlaces de paginación), type ("pelicula"/"tv").
 * - `detail`: title, titleOwnText, cover, synopsis, genres, year, episodes, seasonPanel, seasonHeading,
 *   episodeNumberRegex. Sin `episodes` (o sin resultados) la ficha es una película de un solo "episodio".
 * - `servers`: mode ("iframe", "links" o "tabs"), item, urlAttr, nameRegex, defaultLanguage.
 * - `search.mode = "local"`: el sitio no deja buscar por texto; se filtra su catálogo (`localPages` páginas).
 */
class GenericHtmlProvider(site: EffectiveSite, private val fetcher: HtmlFetcher) : SiteProvider {

    private val s = SiteSupport(site)
    private val config = site.config

    override val id: String = site.id
    override val name: String = site.name
    override val baseUrl: String = site.baseUrl

    override suspend fun latestEpisodes(): List<EpisodeCard> = emptyList()

    override suspend fun showcase(): List<ShowcaseItem> = emptyList()

    // ---------------------------------------------------------------- búsqueda y catálogo

    override suspend fun search(query: String, filters: FilterSelection, page: Int): PagedResult<AnimeCard> {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val search = config.search ?: throw ConfigException("${config.name} no tiene búsqueda configurada")
            if (config.selectors["searchMode"] == "local") return localSearch(q)
            val base = s.url(search.path)
            val params = mutableListOf(search.queryParam + "=" + URLEncoder.encode(q, "UTF-8"))
            var path = base
            if (page > 1) {
                if (search.pagePath != null) path = base.trimEnd('/') + search.pagePath.replace("{page}", page.toString())
                else if (search.pageParam != null) params += "${search.pageParam}=$page"
            }
            return parseList(document(path + "?" + params.joinToString("&"), SEARCH_CACHE), page)
        }
        return catalog(filters, page)
    }

    private suspend fun catalog(filters: FilterSelection, page: Int): PagedResult<AnimeCard> {
        val browse = config.browse ?: throw ConfigException("${config.name} no tiene catálogo configurado")
        var path = browse.path
        val params = mutableListOf<String>()
        for (f in config.filters) {
            val value = filters.valuesOf(f.id).firstOrNull() ?: continue
            if (f.pathTemplate != null) path = f.pathTemplate.replace("{value}", value)
            else if (f.param != null) params += f.param + "=" + URLEncoder.encode(value, "UTF-8")
        }
        var url = s.url(path)
        if (page > 1) {
            if (browse.pagePath != null) url = url.trimEnd('/') + browse.pagePath.replace("{page}", page.toString())
            else if (browse.pageParam != null) params += "${browse.pageParam}=$page"
        }
        if (params.isNotEmpty()) url += (if ("?" in url) "&" else "?") + params.joinToString("&")
        return parseList(document(url, SEARCH_CACHE), page)
    }

    /** Sitios que no dejan buscar por texto: se recorre el catálogo (en paralelo, de a pocas páginas) y se filtra. */
    private suspend fun localSearch(query: String): PagedResult<AnimeCard> {
        val words = TitleMatcher.tokens(query).ifEmpty { listOf(Canonical.of(query)) }
        val all = localCatalog().filter { card -> val t = TitleMatcher.tokens(card.title); words.all { w -> t.any { it.startsWith(w) } } }
        return PagedResult(all, 1, hasNextPage = false)
    }

    private var localCache: Pair<Long, List<AnimeCard>>? = null

    private suspend fun localCatalog(): List<AnimeCard> {
        localCache?.takeIf { System.currentTimeMillis() - it.first < LOCAL_CACHE }?.let { return it.second }
        val pages = config.selectors["localPages"]?.toIntOrNull() ?: 10
        val gate = Semaphore(3)
        val cards = coroutineScope {
            (1..pages).map { p ->
                async {
                    gate.withPermit { runCatching { catalog(FilterSelection.NONE, p).items }.getOrDefault(emptyList()) }
                }
            }.awaitAll().flatten()
        }.distinctBy { it.url }
        localCache = System.currentTimeMillis() to cards
        return cards
    }

    override suspend fun availableFilters(): List<FilterGroup> = config.filters.mapNotNull { f ->
        val options = f.options.map { FilterOption(it.value, it.label, it.canonical ?: Canonical.of(it.label)) }
        options.takeIf { it.isNotEmpty() }?.let { FilterGroup(f.id, f.label, f.multi, it) }
    }

    // ---------------------------------------------------------------- listas

    private suspend fun parseList(doc: Document, page: Int): PagedResult<AnimeCard> = withContext(Dispatchers.Default) {
        val cardSel = s.list("card")
        val titleSel = s.optional(config.selectors, "title")
        val titleAttr = s.optional(config.selectors, "titleAttr")
        val coverSel = s.optional(config.selectors, "cover")
        val linkSel = s.optional(config.selectors, "link")
        val yearSel = s.optional(config.selectors, "year")
        val langSel = s.optional(config.selectors, "language")
        val type = typeOf(config.selectors["type"])
        val cards = doc.select(cardSel).mapNotNull { card ->
            val link = when {
                linkSel != null -> card.selectFirst(linkSel)
                card.tagName() == "a" -> card
                else -> card.selectFirst("a[href]")
            } ?: return@mapNotNull null
            val href = link.attr("href").takeIf { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("javascript") } ?: return@mapNotNull null
            val titleEl = titleSel?.let { card.selectFirst(it) } ?: card
            val title = SiteSupport.clean(if (titleAttr != null) titleEl.attr(titleAttr) else titleEl.ownTextOrText())
            if (title.isEmpty()) return@mapNotNull null
            AnimeCard(
                siteId = id,
                title = title,
                url = s.absolute(href),
                coverUrl = s.cover(coverSel?.let { card.selectFirst(it) } ?: card.selectFirst("img")),
                type = type,
                year = yearOf(card, yearSel),
                language = langSel?.let { sel -> card.select(sel).map { it.text().trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ").ifEmpty { null } },
            )
        }.distinctBy { it.url }
        PagedResult(cards, page, hasNextPage = hasNext(doc, page))
    }

    private fun yearOf(card: Element, sel: String?): Int? = when {
        sel == null -> null
        sel.startsWith("@") -> SiteSupport.year(card.attr(sel.removePrefix("@")))
        else -> SiteSupport.year(card.selectFirst(sel)?.text())
    }

    /** Hay página siguiente si algún enlace de paginación apunta a un número mayor que la actual. */
    private fun hasNext(doc: Document, page: Int): Boolean {
        val regex = s.optional(config.selectors, "nextPageRegex")?.let(::Regex) ?: return false
        return doc.select("a[href]").any { a -> regex.find(a.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { it > page } == true }
    }

    // ---------------------------------------------------------------- ficha

    override suspend fun animeDetail(url: String): AnimeDetail = withContext(Dispatchers.Default) {
        val pageUrl = s.rebase(url)
        val doc = document(pageUrl, DETAIL_CACHE)
        val d = config.detail
        val titleEl = s.optional(d, "title")?.let { doc.selectFirst(it) }
        val title = SiteSupport.clean(if (d["titleOwnText"] == "true") titleEl?.ownText() else titleEl?.text())
            .removePrefix("Ver ").removeSuffix(" online").trim().replace(TRAILING_YEAR, "")
        val genres = s.optional(d, "genres")?.let { doc.select(it) }.orEmpty().map { SiteSupport.clean(it.text()) }.filter { it.isNotEmpty() }.distinct()
        val episodes = episodes(doc, pageUrl)
        val single = episodes.isEmpty()
        AnimeDetail(
            siteId = id,
            url = url,
            title = title,
            coverUrl = s.cover(s.optional(d, "cover")?.let { doc.selectFirst(it) }),
            synopsis = s.optional(d, "synopsis")?.let { doc.select(it).joinToString(" ") { e -> e.text() } }?.let(SiteSupport::clean)?.ifEmpty { null },
            genres = genres,
            type = if (single) AnimeType.PELICULA else typeOf(config.selectors["type"]),
            year = s.optional(d, "year")?.let { SiteSupport.year(doc.selectFirst(it)?.text()) },
            // Una película es un solo "episodio": la propia página.
            episodes = if (single) listOf(Episode(1f, title.ifEmpty { "Película" }, pageUrl)) else episodes,
        )
    }

    private fun episodes(doc: Document, pageUrl: String): List<Episode> {
        val sel = s.optional(config.detail, "episodes") ?: return emptyList()
        val numberRegex = s.optional(config.detail, "episodeNumberRegex")?.let(::Regex)
        val panelSel = s.optional(config.detail, "seasonPanel")
        val seasonRegex = Regex("""(\d+)""")
        val seen = mutableSetOf<String>()
        var counter = 0f
        return doc.select(sel).mapNotNull { a ->
            val href = a.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = s.absolute(href)
            if (!seen.add(url)) return@mapNotNull null
            counter++
            val label = SiteSupport.clean(a.text())
            val number = numberRegex?.find(label)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: counter
            // La temporada es el número del título que precede al panel de episodios.
            val season = panelSel?.let { a.closest(it) }?.previousElementSibling()?.text()?.let { seasonRegex.find(it)?.value?.toIntOrNull() }
            Episode(number = number, title = label.ifEmpty { "Episodio ${number.toInt()}" }, url = url, season = season)
        }
    }

    // ---------------------------------------------------------------- servidores

    override suspend fun servers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.Default) {
        val doc = document(s.rebase(episodeUrl))
        val sv = config.servers
        val defaultLanguage = sv["defaultLanguage"]
        val urlAttrs = (sv["urlAttr"] ?: "data-src,data-lazy-src,src").split(',').map { it.trim() }
        fun urlOf(e: Element) = urlAttrs.firstNotNullOfOrNull { a -> e.attr(a).takeIf { it.startsWith("http") || it.startsWith("//") } }
            ?.let { s.absolute(it.replace("&#038;", "&")) }
        val nameRegex = sv["nameRegex"]?.let(::Regex)
        when (sv["mode"]) {
            // Un enlace por servidor: el texto trae "Idioma · Servidor" (nameRegex: grupo 1 idioma, 2 servidor).
            "links" -> doc.select(s.server("item")).mapNotNull { a ->
                val url = urlOf(a) ?: return@mapNotNull null
                val m = nameRegex?.find(SiteSupport.clean(a.text()))
                VideoServer(name = m?.groupValues?.getOrNull(2) ?: hostName(url), embedUrl = url, language = m?.groupValues?.getOrNull(1) ?: defaultLanguage)
            }
            // Pestañas "#options-N": el texto trae el servidor y el idioma; la URL está en el iframe de su panel.
            "tabs" -> doc.select(s.server("item")).mapNotNull { a ->
                val panel = doc.selectFirst(a.attr("href").takeIf { it.startsWith("#") } ?: return@mapNotNull null) ?: return@mapNotNull null
                val url = panel.select("iframe").firstNotNullOfOrNull { urlOf(it) } ?: return@mapNotNull null
                val m = nameRegex?.find(SiteSupport.clean(a.text()))
                VideoServer(name = m?.groupValues?.getOrNull(1) ?: hostName(url), embedUrl = url, language = m?.groupValues?.getOrNull(2) ?: defaultLanguage)
            }
            else -> doc.select(sv["item"] ?: "iframe").mapNotNull { f -> urlOf(f) }.distinct()
                .map { url -> VideoServer(name = hostName(url), embedUrl = url, language = defaultLanguage) }
        }.distinctBy { it.embedUrl }
    }

    // ---------------------------------------------------------------- utilidades

    private suspend fun document(url: String, cacheMs: Long = 0): Document = Jsoup.parse(fetcher.get(id, url, referer = baseUrl + "/", cacheMs = cacheMs), url)

    private fun Element.ownTextOrText(): String = ownText().ifBlank { text() }

    private fun typeOf(value: String?): AnimeType? = when (value) {
        "pelicula" -> AnimeType.PELICULA
        "tv" -> AnimeType.TV
        else -> null
    }

    private fun hostName(url: String): String = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.").substringBeforeLast('.') }
        .getOrDefault("").replaceFirstChar { it.uppercase() }.ifEmpty { "Servidor" }

    private companion object {
        const val SEARCH_CACHE = 2 * 60_000L
        const val DETAIL_CACHE = 10 * 60_000L
        const val LOCAL_CACHE = 20 * 60_000L
        val TRAILING_YEAR = Regex("""\s*\((19|20)\d\d\)\s*$""")
    }
}
