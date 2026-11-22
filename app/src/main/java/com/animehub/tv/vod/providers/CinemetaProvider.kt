package com.animehub.tv.vod.providers

import com.animehub.tv.vod.VodConfigProvider
import com.animehub.tv.vod.cache.VodCache
import com.animehub.tv.vod.models.Episode
import com.animehub.tv.vod.models.MediaDetail
import com.animehub.tv.vod.models.MediaItem
import com.animehub.tv.vod.models.MediaType
import com.animehub.tv.vod.models.ProviderId
import com.animehub.tv.vod.models.Season
import com.animehub.tv.vod.models.StreamRequest
import com.animehub.tv.vod.models.StreamSource
import com.animehub.tv.vod.models.VodCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proveedor A: catálogo comunitario abierto (formato de catálogo de Stremio, p. ej. Cinemeta).
 * - Populares: `/catalog/{movie|series}/top.json` (con `genre=` y `skip=`); búsqueda: `/catalog/{tipo}/top/search={texto}.json`.
 * - Detalle, temporadas y episodios: `/meta/{tipo}/{id}.json`.
 * - Fuentes: se piden a cada addon configurado al reproducir (`/stream/movie/{id}.json` o `/stream/series/{id}:{t}:{e}.json`).
 */
@Singleton
class CinemetaProvider @Inject constructor(
    private val fetcher: VodFetcher,
    private val cache: VodCache,
    private val config: VodConfigProvider,
) : VodProvider {
    override val id = ProviderId.COMMUNITY

    override suspend fun isConfigured() = config.current().communityReady

    override suspend fun categories(type: MediaType): List<VodCategory> = GENRES.map { VodCategory(it.first, it.second) }

    override suspend fun catalog(type: MediaType, categoryId: String?, skip: Int): List<MediaItem> {
        val base = config.current().communityCatalogUrl
        val extras = listOfNotNull(
            categoryId?.takeIf { it.isNotBlank() }?.let { "genre=${enc(it)}" },
            skip.takeIf { it > 0 }?.let { "skip=$it" },
        )
        val url = "$base/catalog/${type.key}/top" + (if (extras.isEmpty()) "" else "/" + extras.joinToString("&")) + ".json"
        val text = cache.text(url) { fetcher.get(url, HEADERS) } ?: return emptyList()
        return CinemetaParser.parseMetas(text, type)
    }

    override suspend fun search(type: MediaType, query: String): List<MediaItem> {
        val base = config.current().communityCatalogUrl
        // Las búsquedas no se guardan (cada texto es distinto).
        val text = fetcher.get("$base/catalog/${type.key}/top/search=${enc(query.trim())}.json", HEADERS) ?: return emptyList()
        return CinemetaParser.parseMetas(text, type)
    }

    override suspend fun detail(type: MediaType, id: String): MediaDetail? {
        val base = config.current().communityCatalogUrl
        val url = "$base/meta/${type.key}/${enc(id)}.json"
        val text = cache.text(url) { fetcher.get(url, HEADERS) } ?: return null
        return CinemetaParser.parseMeta(text, type)
    }

    override suspend fun streams(request: StreamRequest): List<StreamSource> {
        val imdb = request.item.imdbId ?: request.item.id
        val path = when (request.item.type) {
            MediaType.MOVIE -> "/stream/movie/${enc(imdb)}.json"
            MediaType.SERIES -> {
                val e = request.episode ?: return emptyList()
                "/stream/series/${enc("$imdb:${e.seasonNumber}:${e.episodeNumber}")}.json"
            }
        }
        // Sin guardar: las direcciones pueden caducar. Cada addon se consulta por separado y uno caído no afecta a los demás.
        return config.current().communityAddons.flatMap { addon ->
            val text = fetcher.get(addon + path, HEADERS) ?: return@flatMap emptyList()
            runCatching { CinemetaParser.parseStreams(text) }.getOrDefault(emptyList())
        }.sortedByDescending { qualityRank(it.quality) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        private val HEADERS = mapOf("User-Agent" to VLC_USER_AGENT, "Accept" to "application/json")

        /** Géneros del catálogo comunitario (valor del servidor, nombre en español). */
        val GENRES = listOf(
            "Action" to "Acción", "Adventure" to "Aventura", "Animation" to "Animación", "Comedy" to "Comedia", "Crime" to "Crimen",
            "Documentary" to "Documental", "Drama" to "Drama", "Family" to "Familia", "Fantasy" to "Fantasía", "History" to "Historia",
            "Horror" to "Terror", "Mystery" to "Misterio", "Romance" to "Romance", "Sci-Fi" to "Ciencia ficción", "Thriller" to "Suspenso",
            "War" to "Guerra", "Western" to "Western",
        )

        fun qualityRank(q: String) = when (q) { "4K" -> 5; "1440p" -> 4; "1080p" -> 3; "720p" -> 2; "480p" -> 1; else -> 0 }
    }
}

/** Lectura de las respuestas del catálogo comunitario (sin red). */
object CinemetaParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val QUALITY = Regex("""(2160p|4k|1440p|1080p|720p|480p|360p)""", RegexOption.IGNORE_CASE)

    fun parseMetas(text: String, type: MediaType): List<MediaItem> {
        val metas = (json.parseToJsonElement(text) as? JsonObject)?.get("metas") as? JsonArray ?: return emptyList()
        return metas.mapNotNull { (it as? JsonObject)?.let { o -> item(o, type) } }
    }

    fun parseMeta(text: String, type: MediaType): MediaDetail? {
        val meta = (json.parseToJsonElement(text) as? JsonObject)?.get("meta") as? JsonObject ?: return null
        val item = item(meta, type) ?: return null
        val videos = (meta["videos"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val episodes = videos.mapNotNull { v ->
            val season = v.str("season")?.toIntOrNull() ?: return@mapNotNull null
            val number = (v.str("episode") ?: v.str("number"))?.toIntOrNull() ?: return@mapNotNull null
            Episode(
                seasonNumber = season, episodeNumber = number,
                title = v.str("name") ?: v.str("title") ?: "Episodio $number",
                overview = (v.str("overview") ?: v.str("description"))?.takeIf { it.isNotBlank() },
                thumbnail = v.str("thumbnail"), streamId = v.str("id"), containerExtension = null,
            )
        }
        // La temporada 0 (especiales) va al final.
        val seasons = episodes.groupBy { it.seasonNumber }.toSortedMap(compareBy { if (it == 0) Int.MAX_VALUE else it })
            .map { (n, list) -> Season(n, list.sortedBy { it.episodeNumber }) }
        return MediaDetail(item, if (type == MediaType.SERIES) seasons else emptyList())
    }

    /** Solo las fuentes con dirección http(s) directa: los torrents (`infoHash`) no se pueden reproducir aquí. */
    fun parseStreams(text: String): List<StreamSource> {
        val streams = (json.parseToJsonElement(text) as? JsonObject)?.get("streams") as? JsonArray ?: return emptyList()
        return streams.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = o.str("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return@mapNotNull null
            val label = listOfNotNull(o.str("name"), o.str("title")).joinToString(" ")
            val quality = QUALITY.find(label)?.value?.let { if (it.equals("4k", true)) "4K" else it.lowercase() } ?: "Auto"
            val hints = o["behaviorHints"] as? JsonObject
            val extra = ((hints?.get("proxyHeaders") as? JsonObject)?.get("request") as? JsonObject)
                ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.orEmpty().toMap()
            StreamSource(
                name = o.str("name")?.lineSequence()?.firstOrNull()?.trim()?.ifEmpty { null } ?: "Fuente",
                quality = quality, url = url, headers = mapOf("User-Agent" to VLC_USER_AGENT) + extra, isDirect = true,
            )
        }
    }

    private fun item(o: JsonObject, type: MediaType): MediaItem? {
        val id = o.str("id") ?: return null
        val title = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val year = (o.str("year") ?: o.str("releaseInfo"))?.take(4)?.takeIf { y -> y.length == 4 && y.all { it.isDigit() } }
        return MediaItem(
            id = id, imdbId = o.str("imdb_id") ?: id.takeIf { it.startsWith("tt") }, title = title, type = type,
            poster = o.str("poster"), backdrop = o.str("background"), overview = o.str("description")?.trim()?.takeIf { it.isNotEmpty() },
            year = year, genres = (o["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            rating = o.str("imdbRating")?.toFloatOrNull(), provider = ProviderId.COMMUNITY,
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
}
