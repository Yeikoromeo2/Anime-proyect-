package com.animehub.tv.vod

import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.vod.cache.VodCache
import com.animehub.tv.vod.models.MediaDetail
import com.animehub.tv.vod.models.MediaItem
import com.animehub.tv.vod.models.MediaType
import com.animehub.tv.vod.models.ProviderId
import com.animehub.tv.vod.models.StreamRequest
import com.animehub.tv.vod.models.StreamSource
import com.animehub.tv.vod.models.VodCategory
import com.animehub.tv.vod.providers.CinemetaProvider
import com.animehub.tv.vod.providers.VodProvider
import com.animehub.tv.vod.providers.XtreamVodProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puerta única del módulo VOD: reparte cada petición al proveedor que toca y devuelve siempre los modelos unificados.
 * Un proveedor que falla no afecta a los demás (el error solo se guarda en el registro).
 */
@Singleton
class VodRepository @Inject constructor(
    community: CinemetaProvider,
    xtream: XtreamVodProvider,
    private val cache: VodCache,
    private val errorLog: ErrorLogRepository,
) {
    private val providers: Map<ProviderId, VodProvider> = listOf<VodProvider>(community, xtream).associateBy { it.id }

    /** Los proveedores que tienen lo necesario para funcionar (dirección, usuario…). */
    suspend fun available(): List<ProviderId> = providers.values.filter { it.isConfigured() }.map { it.id }

    suspend fun categories(provider: ProviderId, type: MediaType): List<VodCategory> = guard(provider, "categorías", emptyList()) { it.categories(type) }

    suspend fun catalog(provider: ProviderId, type: MediaType, categoryId: String?, skip: Int): List<MediaItem> =
        guard(provider, "catálogo", emptyList()) { it.catalog(type, categoryId, skip) }

    suspend fun search(provider: ProviderId, type: MediaType, query: String): List<MediaItem> =
        guard(provider, "búsqueda", emptyList()) { it.search(type, query) }

    suspend fun detail(provider: ProviderId, type: MediaType, id: String): MediaDetail? = guard(provider, "detalle", null) { it.detail(type, id) }

    /**
     * Fuentes de reproducción de ahora mismo para una película o un episodio. Se piden cada vez y no se guardan.
     * @param season/episode número de temporada y de episodio (series).
     */
    suspend fun streams(provider: ProviderId, type: MediaType, id: String, season: Int, episode: Int): List<StreamSource> {
        val detail = detail(provider, type, id) ?: return emptyList()
        val ep = if (type == MediaType.SERIES) detail.seasons.firstOrNull { it.seasonNumber == season }?.episodes?.firstOrNull { it.episodeNumber == episode } ?: return emptyList() else null
        return guard(provider, "fuentes", emptyList()) { it.streams(StreamRequest(detail.item, ep)) }
    }

    /** Actualización manual: borra la caché de 6 horas y lo que haya en memoria. */
    fun refresh() {
        cache.clear()
        providers.values.forEach { it.clearMemory() }
    }

    private suspend fun <T> guard(provider: ProviderId, what: String, fallback: T, block: suspend (VodProvider) -> T): T {
        val p = providers[provider] ?: return fallback
        return try {
            if (!p.isConfigured()) fallback else block(p)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorLog.logIssue("Catálogo", "${provider.key}: $what", e)
            fallback
        }
    }
}
