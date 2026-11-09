package com.animehub.tv.providers

import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterGroup
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.domain.model.PagedResult
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.VideoServer

/**
 * Interfaz común de cada sitio. Los selectores y dominios vienen de la configuración remota;
 * cada implementación solo contiene la lógica que no cabe en selectores.
 */
interface SiteProvider {
    val id: String
    val name: String
    val baseUrl: String

    /** Novedades (últimos episodios) con su imagen. */
    suspend fun latestEpisodes(): List<EpisodeCard>

    /** Lo que el sitio destaca en su portada: recomendados (con reseña si la trae) y series recién agregadas. */
    suspend fun showcase(): List<ShowcaseItem>

    /** Sin texto ni filtros devuelve el catálogo completo del sitio, página a página. */
    suspend fun search(query: String, filters: FilterSelection, page: Int): PagedResult<AnimeCard>

    /** Filtros reales del sitio (leídos de su HTML o de la configuración). */
    suspend fun availableFilters(): List<FilterGroup>

    suspend fun animeDetail(url: String): AnimeDetail

    suspend fun servers(episodeUrl: String): List<VideoServer>
}
