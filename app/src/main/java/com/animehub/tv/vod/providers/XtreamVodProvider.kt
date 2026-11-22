package com.animehub.tv.vod.providers

import com.animehub.tv.data.iptv.IptvAccount
import com.animehub.tv.data.iptv.IptvUrl
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proveedor B: catálogo VOD del servidor del usuario (API estilo Xtream, `player_api.php`).
 * - Películas: `get_vod_categories` + `get_vod_streams`. Series: `get_series_categories` + `get_series` y `get_series_info`.
 * - Las direcciones de video se arman SOLO al reproducir:
 *   película `…/movie/{usuario}/{clave}/{id}.{ext}` y episodio `…/series/{usuario}/{clave}/{idEpisodio}.{ext}`.
 */
@Singleton
class XtreamVodProvider @Inject constructor(
    private val fetcher: VodFetcher,
    private val cache: VodCache,
    private val config: VodConfigProvider,
) : VodProvider {
    override val id = ProviderId.XTREAM

    /** Listas ya leídas (pesan ~3 MB de texto): se conservan en memoria hasta la actualización manual. */
    private val memory = mutableMapOf<String, List<MediaItem>>()

    override suspend fun isConfigured() = config.current().xtreamReady

    override fun clearMemory() = memory.clear()

    override suspend fun categories(type: MediaType): List<VodCategory> {
        val account = account() ?: return emptyList()
        val action = if (type == MediaType.MOVIE) "get_vod_categories" else "get_series_categories"
        val text = api(account, action) ?: return emptyList()
        return XtreamParser.parseCategories(text)
    }

    override suspend fun catalog(type: MediaType, categoryId: String?, skip: Int): List<MediaItem> {
        val all = items(type) ?: return emptyList()
        val filtered = if (categoryId.isNullOrBlank()) all else all.filter { categoryId in (it.genres) }
        return filtered.drop(skip).take(PAGE)
    }

    override suspend fun search(type: MediaType, query: String): List<MediaItem> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        return (items(type) ?: return emptyList()).filter { fold(it.title).contains(q) }.take(PAGE)
    }

    override suspend fun detail(type: MediaType, id: String): MediaDetail? {
        val item = items(type)?.firstOrNull { it.id == id } ?: return null
        if (type == MediaType.MOVIE) return MediaDetail(item, emptyList())
        val account = account() ?: return MediaDetail(item, emptyList())
        val text = api(account, "get_series_info", "series_id" to id) ?: return MediaDetail(item, emptyList())
        return XtreamParser.parseSeriesInfo(text, item)
    }

    override suspend fun streams(request: StreamRequest): List<StreamSource> {
        val account = account() ?: return emptyList()
        val base = IptvUrl.baseOf(account.host) ?: return emptyList()
        val headers = mapOf("User-Agent" to VLC_USER_AGENT)
        return when (request.item.type) {
            MediaType.MOVIE -> listOf("mp4", "mkv", "m3u8").map { ext ->
                StreamSource("Directo ${ext.uppercase()}", "Auto", streamUrl(base, account, "movie", request.item.id, ext).toString(), headers, true)
            }
            MediaType.SERIES -> {
                val e = request.episode ?: return emptyList()
                val id = e.streamId ?: return emptyList()
                val first = e.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
                (listOf(first) + listOf("mp4", "mkv", "m3u8")).distinct().map { ext ->
                    StreamSource("Directo ${ext.uppercase()}", "Auto", streamUrl(base, account, "series", id, ext).toString(), headers, true)
                }
            }
        }
    }

    /** Dirección directa (se calcula en el momento; nunca se guarda). */
    internal fun streamUrl(base: HttpUrl, account: IptvAccount, kind: String, id: String, ext: String): HttpUrl =
        base.newBuilder().addPathSegment(kind).addPathSegment(account.user).addPathSegment(account.pass).addPathSegment("$id.$ext").build()

    private suspend fun account(): IptvAccount? = config.current().xtream.takeIf { it.ready }

    private suspend fun items(type: MediaType): List<MediaItem>? {
        val account = account() ?: return null
        val key = "${type.key}|${account.host}|${account.user}"
        memory[key]?.let { return it }
        val action = if (type == MediaType.MOVIE) "get_vod_streams" else "get_series"
        val text = api(account, action) ?: return null
        val list = runCatching { if (type == MediaType.MOVIE) XtreamParser.parseMovies(text) else XtreamParser.parseSeries(text) }.getOrNull() ?: return null
        memory[key] = list
        return list
    }

    private suspend fun api(account: IptvAccount, action: String, vararg extra: Pair<String, String>): String? {
        val base = IptvUrl.baseOf(account.host) ?: return null
        val url = IptvUrl.api(account, action)?.newBuilder()?.apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }?.build() ?: return null
        val key = "xt:${base.host}:${account.user}:$action:${extra.joinToString { it.second }}"
        return cache.text(key) { fetcher.get(url.toString(), mapOf("User-Agent" to VLC_USER_AGENT)) }
    }

    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

    companion object {
        const val PAGE = 60
    }
}

/** Lectura de las respuestas de la API Xtream (mezclan números y textos: todo se lee como texto). Sin red. */
object XtreamParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Las portadas vienen como ruta parcial (`/abc.jpg`, estilo TMDB); las completas (http…) se dejan igual. */
    const val IMAGE_BASE = "https://image.tmdb.org/t/p/"

    fun image(path: String?, big: Boolean): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        else -> IMAGE_BASE + (if (big) "w780" else "w342") + (if (path.startsWith("/")) path else "/$path")
    }

    fun parseCategories(text: String): List<VodCategory> = (json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val name = o.str("category_name")?.trim().orEmpty()
        // La categoría "land" (portada, sin nombre) y las sin nombre no sirven de filtro.
        if (name.isEmpty() || o.str("type") == "land") null else o.str("category_id")?.let { VodCategory(it, name) }
    }

    /** `genres` guarda los identificadores de categoría (así se filtra por categoría sin otra consulta). */
    fun parseMovies(text: String): List<MediaItem> = (json.parseToJsonElement(text) as JsonArray).mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val id = o.str("stream_id") ?: return@mapNotNull null
        val title = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        MediaItem(
            id = id, imdbId = null, title = title, type = MediaType.MOVIE, poster = image(o.str("stream_icon"), false),
            backdrop = image(o.str("backdrop"), true), overview = null, year = year(o.str("release")),
            genres = ids(o.str("categories_ids")), rating = o.str("rating_5based")?.toFloatOrNull()?.times(2)?.takeIf { it > 0f },
            provider = ProviderId.XTREAM,
        )
    }

    fun parseSeries(text: String): List<MediaItem> = (json.parseToJsonElement(text) as JsonArray).mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val id = o.str("series_id") ?: return@mapNotNull null
        val title = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        MediaItem(
            id = id, imdbId = null, title = title, type = MediaType.SERIES, poster = image(o.str("cover"), false),
            backdrop = image(o.str("backdrop_path"), true), overview = o.str("plot")?.trim()?.takeIf { it.isNotEmpty() },
            year = year(o.str("releaseDate")), genres = ids(o.str("categories") ?: o.str("category_id")),
            rating = o.str("rating_5based")?.toFloatOrNull()?.times(2)?.takeIf { it > 0f }, provider = ProviderId.XTREAM,
        )
    }

    /**
     * `get_series_info`: `episodes` es un objeto por temporada (`{"1":[{id, episode_num, title, container_extension, info{plot, movie_image}}]}`)
     * o, en algunos servidores, una lista de listas.
     */
    fun parseSeriesInfo(text: String, base: MediaItem): MediaDetail {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return MediaDetail(base, emptyList())
        val info = root["info"] as? JsonObject
        val item = base.copy(
            overview = base.overview ?: info?.str("plot")?.trim()?.takeIf { it.isNotEmpty() },
            backdrop = base.backdrop ?: image((info?.get("backdrop_path") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }, true),
        )
        val episodes = root["episodes"]
        val groups: List<Pair<Int?, JsonArray>> = when (episodes) {
            is JsonObject -> episodes.mapNotNull { (k, v) -> (v as? JsonArray)?.let { k.toIntOrNull() to it } }
            is JsonArray -> episodes.mapIndexedNotNull { i, v -> (v as? JsonArray)?.let { (i + 1) to it } }
            else -> emptyList()
        }
        val seasons = groups.map { (n, list) ->
            val eps = list.mapNotNull { e -> episode(e as? JsonObject ?: return@mapNotNull null, n) }
            Season(n ?: eps.firstOrNull()?.seasonNumber ?: 1, eps.sortedBy { it.episodeNumber })
        }.filter { it.episodes.isNotEmpty() }.sortedBy { if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber }
        return MediaDetail(item, seasons)
    }

    private fun episode(o: JsonObject, season: Int?): Episode? {
        val id = o.str("id") ?: return null
        val number = o.str("episode_num")?.toIntOrNull() ?: return null
        val info = o["info"] as? JsonObject
        return Episode(
            seasonNumber = o.str("season")?.toIntOrNull() ?: season ?: 1, episodeNumber = number,
            title = o.str("title")?.trim()?.takeIf { it.isNotEmpty() } ?: "Episodio $number",
            overview = info?.str("plot")?.trim()?.takeIf { it.isNotEmpty() }, thumbnail = image(info?.str("movie_image"), false),
            streamId = id, containerExtension = o.str("container_extension")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun year(raw: String?) = raw?.take(4)?.takeIf { y -> y.length == 4 && y.all { it.isDigit() } }
    private fun ids(raw: String?) = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
}
