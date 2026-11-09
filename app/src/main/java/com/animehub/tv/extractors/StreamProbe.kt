package com.animehub.tv.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Comprueba que el video directo llegue de verdad: pide el primer KB del primer trozo con las
 * cabeceras del servidor. Algunos servidores entregan la lista HLS pero bloquean los trozos
 * (Cloudflare responde 403 con una página): ahí el reproductor nativo fallaría, así que ese
 * servidor se abre directamente en el reproductor web.
 */
class StreamProbe(baseClient: OkHttpClient) {

    private val client = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    suspend fun reachable(url: String, headers: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=0-1023")
                .build()
            client.newCall(request).execute().use { response ->
                looksLikeVideo(response.code, response.peekBody(FIRST_BYTES).bytes())
            }
        }.getOrDefault(false)
    }

    companion object {
        private const val FIRST_BYTES = 64L

        /** Respuesta correcta y no es una página (las de bloqueo empiezan con "<"). */
        fun looksLikeVideo(code: Int, firstBytes: ByteArray): Boolean {
            if (code !in 200..299) return false
            val start = firstBytes.dropWhile { it == ' '.code.toByte() || it == '\n'.code.toByte() || it == '\r'.code.toByte() || it == '\t'.code.toByte() }
            return start.isNotEmpty() && start.first() != '<'.code.toByte()
        }
    }
}
