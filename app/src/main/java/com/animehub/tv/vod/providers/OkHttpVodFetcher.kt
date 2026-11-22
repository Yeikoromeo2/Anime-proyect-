package com.animehub.tv.vod.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** GET con OkHttp (sigue redirecciones, tiempos límite de 15/60/90 s). */
class OkHttpVodFetcher @Inject constructor(client: OkHttpClient) : VodFetcher {
    private val http = client.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()

    override suspend fun get(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body.string() else null }
        }.getOrNull()
    }
}
