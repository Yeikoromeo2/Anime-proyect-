package com.animehub.tv.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Error HTTP con mensaje en español para mostrar al usuario. */
class SiteHttpException(val code: Int, message: String) : IOException(message)

/**
 * Descarga HTML de los sitios con el cliente común (User-Agent, cookies, reintentos),
 * detecta Cloudflare y guarda una caché corta en memoria.
 */
@Singleton
class HtmlFetcher @Inject constructor(private val client: OkHttpClient) {

    private data class Entry(val time: Long, val body: String)
    private val cache = ConcurrentHashMap<String, Entry>()

    /**
     * @param cacheMs tiempo durante el que se reutiliza la respuesta (0 = sin caché).
     */
    suspend fun get(siteId: String, url: String, referer: String? = null, cacheMs: Long = 0): String {
        if (cacheMs > 0) {
            cache[url]?.let { if (System.currentTimeMillis() - it.time < cacheMs) return it.body }
        }
        val request = Request.Builder().url(url).apply { referer?.let { header("Referer", it) } }.build()
        val body = execute(siteId, request)
        if (cacheMs > 0) {
            if (cache.size > MAX_ENTRIES) cache.clear()
            cache[url] = Entry(System.currentTimeMillis(), body)
        }
        return body
    }

    /** POST de formulario (p. ej. admin-ajax.php de WordPress). */
    suspend fun postForm(siteId: String, url: String, fields: Map<String, String>, referer: String? = null): String {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).post(form)
            .header("X-Requested-With", "XMLHttpRequest")
            .apply { referer?.let { header("Referer", it) } }
            .build()
        return execute(siteId, request)
    }

    fun clearCache() = cache.clear()

    private suspend fun execute(siteId: String, request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            val state = CloudflareDetector.detect(response.code, response::header, text)
            if (state != ProtectionState.NINGUNA) throw SiteProtectedException(siteId, state)
            if (!response.isSuccessful) {
                throw SiteHttpException(
                    response.code,
                    if (response.code == 404) "No se encontró la página en el sitio" else "El sitio respondió con un error (${response.code})",
                )
            }
            text
        }
    }

    private companion object {
        const val MAX_ENTRIES = 60
    }
}
