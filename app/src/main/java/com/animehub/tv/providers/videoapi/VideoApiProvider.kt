package com.animehub.tv.providers.videoapi

import com.animehub.tv.data.config.ConfigException
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.AnimeStatus
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Proveedor de los sitios hechos con la plataforma "videoapi" (SeriesLan, engine = "videoapi"):
 * la web es una aplicación que lee una API JSON pública del mismo dominio.
 * - Catálogo `/api/rest/v1/listing?kind=serie&page=N`; por género o año `/api/rest/v1/taxonomies/{tipo}/{valor}`.
 * - Búsqueda `/api/rest/v1/search?q=`; ficha `/api/rest/v1/content/{tipo}/{slug}`.
 * - Episodios `/api/rest/v1/episodes?item_id=&season=` (cada uno trae la URL de su reproductor).
 * Configuración: `selectors.kind` ("serie"), `selectors.imageBase` (portadas de TMDB).
 */
class VideoApiProvider(site: EffectiveSite, private val fetcher: HtmlFetcher) : SiteProvider {

    private val s = SiteSupport(site)
    private val config = site.config
    private val kind = config.selectors["kind"] ?: "serie"
    private val imageBase = config.selectors["imageBase"] ?: "https://image.tmdb.org/t/p"

    override val id: String = site.id
    override val name: String = site.name
    override val baseUrl: String = site.baseUrl

    override suspend fun latestEpisodes(): List<EpisodeCard> = emptyList()

    override suspend fun showcase(): List<ShowcaseItem> = emptyList()

    override suspend fun search(query: String, filters: FilterSelection, page: Int): PagedResult<AnimeCard> {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val root = api("search?q=" + URLEncoder.encode(q, "UTF-8"), SEARCH_CACHE)
            val items = root["items"]?.jsonArray.orEmpty().mapNotNull(::card).filter { it.second == kind }.map { it.first }
            return PagedResult(items, page, hasNextPage = false)
        }
        // Género o año: la API los sirve como "taxonomías"; sin filtros, el catálogo por fecha.
        val taxonomy = config.filters.firstNotNullOfOrNull { f -> filters.valuesOf(f.id).firstOrNull()?.let { f.param to it } }
        val path = if (taxonomy != null) "taxonomies/${taxonomy.first}/${URLEncoder.encode(taxonomy.second, "UTF-8")}?kind=$kind&page=$page"
        else "listing?kind=$kind&order=recent&page=$page"
        val root = api(path, SEARCH_CACHE)
        val items = root["items"]?.jsonArray.orEmpty().mapNotNull(::card).map { it.first }
        val hasNext = root["pagination"]?.jsonObject?.get("has_next")?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
        return PagedResult(items, page, hasNextPage = hasNext)
    }

    override suspend fun availableFilters(): List<FilterGroup> = config.filters.mapNotNull { f ->
        val options = f.options.map { FilterOption(it.value, it.label, it.canonical ?: Canonical.of(it.label)) }
        options.takeIf { it.isNotEmpty() }?.let { FilterGroup(f.id, f.label, f.multi, it) }
    }

    private fun card(element: JsonElement): Pair<AnimeCard, String>? {
        val o = element as? JsonObject ?: return null
        val title = o.str("title")?.takeIf { it.isNotBlank() } ?: return null
        val url = o.str("url") ?: return null
        val itemKind = o.str("kind") ?: kind
        val card = AnimeCard(
            siteId = id,
            title = title,
            url = s.absolute(url),
            coverUrl = o.str("poster_path")?.let { "$imageBase/w342$it" },
            type = if (itemKind == "movie") AnimeType.PELICULA else AnimeType.TV,
            year = o["year"]?.let { (it as? JsonPrimitive)?.intOrNull },
        )
        return card to itemKind
    }

    override suspend fun animeDetail(url: String): AnimeDetail {
        // /serie/{slug}
        val path = s.rebase(url).substringAfter(baseUrl).trim('/').split('/')
        val itemKind = path.getOrNull(0) ?: kind
        val slug = path.getOrNull(1) ?: throw ConfigException("Dirección de ficha no válida: $url")
        val root = api("content/$itemKind/$slug", DETAIL_CACHE)
        val item = root["item"]?.jsonObject ?: throw ConfigException("${config.name} no devolvió la ficha")
        val itemId = item["catalog_item_id"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: throw ConfigException("Ficha sin identificador")
        val seasons = root["episode_seasons"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.get("season")?.let { n -> (n as? JsonPrimitive)?.intOrNull } }
        val episodes = if (itemKind == "movie") emptyList() else loadEpisodes(itemId, seasons)
        val title = item.str("title").orEmpty()
        return AnimeDetail(
            siteId = id,
            url = url,
            title = title,
            coverUrl = item.str("poster_path")?.let { "$imageBase/w500$it" },
            synopsis = (root["overview"] as? JsonPrimitive)?.contentOrNull ?: item.str("overview"),
            alternativeTitle = item.str("original_title")?.takeIf { it != title },
            genres = item["genres"]?.let { it as? JsonArray }.orEmpty().mapNotNull { g -> (g as? JsonObject)?.str("title") ?: (g as? JsonObject)?.str("name") ?: (g as? JsonPrimitive)?.contentOrNull },
            status = when (item.str("status")) {
                "Returning Series" -> AnimeStatus.EMISION
                "Ended", "Canceled" -> AnimeStatus.FINALIZADO
                else -> AnimeStatus.DESCONOCIDO
            },
            type = if (itemKind == "movie") AnimeType.PELICULA else AnimeType.TV,
            year = item["year"]?.let { (it as? JsonPrimitive)?.intOrNull } ?: SiteSupport.year(item.str("first_air_date")),
            // Película: un solo "episodio", el reproductor de la película.
            episodes = if (itemKind == "movie") listOf(Episode(1f, title.ifEmpty { "Película" }, item.str("player_url") ?: s.absolute(item.str("url").orEmpty()))) else episodes,
        )
    }

    /** Los episodios de todas las temporadas, pidiéndolas de a pocas a la vez. */
    private suspend fun loadEpisodes(itemId: String, seasons: List<Int>): List<Episode> {
        val gate = Semaphore(3)
        val all = coroutineScope {
            seasons.map { season ->
                async { gate.withPermit { runCatching { seasonEpisodes(itemId, season) }.getOrDefault(emptyList()) } }
            }.awaitAll().flatten()
        }
        return all.sortedWith(compareBy({ it.season ?: 0 }, { it.number }))
    }

    private suspend fun seasonEpisodes(itemId: String, season: Int): List<Episode> {
        val result = mutableListOf<Episode>()
        var page = 1
        while (page <= MAX_PAGES) {
            val root = api("episodes?item_id=$itemId&season=$season&limit=60&page=$page", DETAIL_CACHE)
            root["episodes"]?.jsonArray.orEmpty().forEach { e ->
                val o = e as? JsonObject ?: return@forEach
                if ((o["playable"] as? JsonPrimitive)?.booleanOrNull == false) return@forEach
                val number = (o["episode"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull() ?: return@forEach
                val player = o.str("player_url") ?: return@forEach
                result += Episode(
                    number = number,
                    title = o.str("title")?.takeIf { it.isNotBlank() } ?: "Episodio ${number.toInt()}",
                    url = player,
                    imageUrl = o.str("still_path")?.let { "$imageBase/w300$it" },
                    season = season,
                )
            }
            val more = root["pagination"]?.jsonObject?.get("has_next")?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
            if (!more) break
            page++
        }
        return result
    }

    /** El reproductor de cada episodio ya es su dirección (la API la entrega completa). */
    override suspend fun servers(episodeUrl: String): List<VideoServer> =
        listOf(VideoServer(name = "Reproductor", embedUrl = episodeUrl, language = config.servers["defaultLanguage"]))

    private suspend fun api(path: String, cacheMs: Long): JsonObject =
        json.parseToJsonElement(fetcher.get(id, "$baseUrl/api/rest/v1/$path", referer = "$baseUrl/", cacheMs = cacheMs)).jsonObject

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        const val SEARCH_CACHE = 2 * 60_000L
        const val DETAIL_CACHE = 10 * 60_000L
        const val MAX_PAGES = 6
    }
}
