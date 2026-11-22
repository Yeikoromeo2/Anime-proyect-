package com.animehub.tv.data.iptv

import android.content.Context
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.Catalog
import com.animehub.tv.domain.model.CatalogCategory
import com.animehub.tv.domain.model.CatalogItem
import com.animehub.tv.domain.model.CatalogKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Películas y series de la lista del usuario (API `player_api.php` del mismo servidor): categorías, portadas,
 * año, valoración y sinopsis. Se guarda en disco 6 horas (las listas pesan unos 3 MB) y se puede recargar a mano.
 * Aquí no hay direcciones de video: el servidor no las entrega en estas consultas.
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val iptv: IptvRepository,
    private val errorLog: ErrorLogRepository,
) {
    private val dir get() = File(context.filesDir, "iptv").apply { mkdirs() }
    private val mutex = Mutex()
    private val memory = mutableMapOf<CatalogKind, Pair<String, Catalog>>()

    /** El apartado ya leído (la primera vez lo baja); null si no hay datos de la lista o el servidor no responde. */
    suspend fun catalog(kind: CatalogKind): Catalog? = mutex.withLock {
        val account = iptv.account().takeIf { it.ready } ?: return@withLock null
        val key = IptvUrl.cacheKey(account)
        memory[kind]?.takeIf { it.first == key }?.second ?: withContext(Dispatchers.IO) { load(kind, account, key) }?.also { memory[kind] = key to it }
    }

    suspend fun item(kind: CatalogKind, id: String): CatalogItem? = catalog(kind)?.find(id)

    /** Recarga manual: borra lo guardado (también la lista de canales) y vuelve a bajar todo al abrir. */
    suspend fun refresh() {
        iptv.refresh()
        mutex.withLock { memory.clear() }
    }

    private fun load(kind: CatalogKind, account: IptvAccount, key: String): Catalog? {
        val (categoriesAction, itemsAction) = when (kind) {
            CatalogKind.MOVIES -> "get_vod_categories" to "get_vod_streams"
            CatalogKind.SERIES -> "get_series_categories" to "get_series"
        }
        val categoriesText = text(account, key, categoriesAction) ?: return null
        val itemsText = text(account, key, itemsAction) ?: return null
        val categories = runCatching { CatalogParser.parseCategories(categoriesText) }.getOrDefault(emptyList())
        val items = runCatching { if (kind == CatalogKind.MOVIES) CatalogParser.parseMovies(itemsText) else CatalogParser.parseSeries(itemsText) }.getOrDefault(emptyList())
        return Catalog(kind, categories, items).takeIf { it.items.isNotEmpty() }
    }

    /** Texto de una consulta: la copia de disco si tiene menos de 6 horas; si no se baja (y si falla, la vieja). */
    private fun text(account: IptvAccount, key: String, action: String): String? {
        val file = File(dir, "catalogo-$key-$action.json")
        dir.listFiles()?.filter { it.name.startsWith("catalogo-") && !it.name.startsWith("catalogo-$key-") }?.forEach { it.delete() }
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < IptvRepository.TTL_MS) return file.readText()
        val fresh = download(account, action)
        if (fresh != null) { file.writeText(fresh); return fresh }
        return if (file.exists()) file.readText() else null
    }

    private fun download(account: IptvAccount, action: String): String? {
        val http = client.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
        val base = IptvUrl.baseOf(account.host) ?: return null
        var last: Exception? = null
        for (scheme in listOf(base.scheme, if (base.scheme == "http") "https" else "http")) {
            val url = IptvUrl.api(account, action, scheme) ?: continue
            try {
                val request = Request.Builder().url(url).header("User-Agent", IptvRepository.USER_AGENT).build()
                http.newCall(request).execute().use { r ->
                    val body = r.body.string()
                    if (r.isSuccessful && body.trimStart().startsWith("[")) return body
                    last = IllegalStateException("HTTP ${r.code}")
                }
            } catch (e: Exception) {
                last = e
            }
        }
        last?.let { errorLog.logIssue("Catálogo", action, it) }
        return null
    }

}

/** Lectura de las respuestas de la lista (mezclan números y textos: todo se lee como texto). Sin red ni contexto. */
object CatalogParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseCategories(text: String): List<CatalogCategory> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = o.str("category_name")?.trim().orEmpty()
            // La categoría "land" (portada, sin nombre) y las sin nombre no son estantes.
            if (name.isEmpty() || o.str("type") == "land") null else o.str("category_id")?.let { CatalogCategory(it, name) }
        }

    fun parseMovies(text: String): List<CatalogItem> = (json.parseToJsonElement(text) as JsonArray).mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val id = o.str("stream_id") ?: return@mapNotNull null
        val title = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        CatalogItem(
            id = id, title = title, poster = image(o.str("stream_icon"), false), backdrop = image(o.str("backdrop"), true),
            year = o.str("release")?.take(4)?.takeIf { it.length == 4 }, rating = o.str("rating_5based")?.toFloatOrNull() ?: 0f,
            plot = null, categoryIds = o.str("categories_ids").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    fun parseSeries(text: String): List<CatalogItem> = (json.parseToJsonElement(text) as JsonArray).mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val id = o.str("series_id") ?: return@mapNotNull null
        val title = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        CatalogItem(
            id = id, title = title, poster = image(o.str("cover"), false), backdrop = image(o.str("backdrop_path"), true),
            year = o.str("releaseDate")?.take(4)?.takeIf { it.length == 4 }, rating = o.str("rating_5based")?.toFloatOrNull() ?: 0f,
            plot = o.str("plot")?.trim()?.takeIf { it.isNotEmpty() },
            categoryIds = (o.str("categories") ?: o.str("category_id")).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

    /** Las portadas vienen como ruta parcial (`/abc.jpg`, estilo TMDB); las completas (http…) se dejan igual. */
    fun image(path: String?, big: Boolean): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        else -> IMAGE_BASE + (if (big) "w780" else "w342") + (if (path.startsWith("/")) path else "/$path")
    }

    const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
}
