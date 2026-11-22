package com.animehub.tv.vod.providers

import com.animehub.tv.vod.models.MediaDetail
import com.animehub.tv.vod.models.MediaItem
import com.animehub.tv.vod.models.MediaType
import com.animehub.tv.vod.models.ProviderId
import com.animehub.tv.vod.models.StreamRequest
import com.animehub.tv.vod.models.StreamSource
import com.animehub.tv.vod.models.VodCategory

/** Una petición HTTP GET que devuelve el texto (o null si falla). Se separa para poder probar sin red. */
fun interface VodFetcher {
    suspend fun get(url: String, headers: Map<String, String>): String?
}

/** Una fuente de catálogo. Todas devuelven los mismos modelos unificados. */
interface VodProvider {
    val id: ProviderId

    /** ¿Tiene lo necesario (dirección, usuario…) para funcionar? */
    suspend fun isConfigured(): Boolean

    suspend fun categories(type: MediaType): List<VodCategory>

    /** Títulos de una categoría (null = todo), a partir de [skip] ya vistos. */
    suspend fun catalog(type: MediaType, categoryId: String?, skip: Int): List<MediaItem>

    suspend fun search(type: MediaType, query: String): List<MediaItem>

    suspend fun detail(type: MediaType, id: String): MediaDetail?

    /** Fuentes de reproducción de ahora mismo (se piden al pulsar reproducir; no se guardan). */
    suspend fun streams(request: StreamRequest): List<StreamSource>

    /** Suelta lo que haya en memoria (se llama en la actualización manual). */
    fun clearMemory() {}
}

internal const val VLC_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
