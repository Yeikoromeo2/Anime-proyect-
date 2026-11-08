package com.animehub.tv.domain.model

/** Tipo de contenido tal como lo clasifican los sitios. */
enum class AnimeType(val etiqueta: String) {
    TV("TV"), PELICULA("Película"), OVA("OVA"), ONA("ONA"), ESPECIAL("Especial"), CORTO("Corto"), OTRO("Otro")
}

/** Estado de emisión. */
enum class AnimeStatus(val etiqueta: String) {
    EMISION("En emisión"), FINALIZADO("Finalizado"), PROXIMAMENTE("Próximamente"), DESCONOCIDO("Desconocido")
}

/** Tarjeta de anime (resultado de búsqueda o de directorio). Siempre lleva portada. */
data class AnimeCard(
    val siteId: String,
    val title: String,
    val url: String,
    val coverUrl: String?,
    val type: AnimeType? = null,
    val year: Int? = null,
    /** Idioma o etiqueta que muestra el sitio ("Latino", "Castellano", "Sub"…). */
    val language: String? = null,
)

/** Tarjeta de episodio reciente (novedades). */
data class EpisodeCard(
    val siteId: String,
    val animeTitle: String,
    val episodeNumber: Float?,
    val episodeUrl: String,
    val imageUrl: String?,
    val animeUrl: String? = null,
    val language: String? = null,
    /** Texto de fecha tal como lo muestra el sitio ("08 Nov 2026", "hace 14 minutos"). */
    val dateLabel: String? = null,
)

data class Episode(
    val number: Float,
    val title: String,
    val url: String,
    val imageUrl: String? = null,
    val season: Int? = null,
)

data class AnimeDetail(
    val siteId: String,
    val url: String,
    val title: String,
    val coverUrl: String?,
    val synopsis: String? = null,
    val alternativeTitle: String? = null,
    val genres: List<String> = emptyList(),
    val status: AnimeStatus = AnimeStatus.DESCONOCIDO,
    val type: AnimeType? = null,
    val year: Int? = null,
    val episodes: List<Episode> = emptyList(),
)

/** Servidor/reproductor detectado en la página de un episodio. */
data class VideoServer(
    val name: String,
    val embedUrl: String,
    /** "SUB", "DUB", "Latino"… si el sitio lo indica. */
    val language: String? = null,
)

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val hasNextPage: Boolean,
    /** Aviso si el sitio no pudo aplicar algún filtro (p. ej. no combina género y tipo). */
    val note: String? = null,
)

/** Progreso de reproducción de un episodio. */
data class WatchProgress(
    val siteId: String,
    val animeUrl: String,
    val animeTitle: String,
    val coverUrl: String?,
    val episodeNumber: Float,
    val episodeUrl: String,
    val serverName: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean,
) {
    /** Fracción vista entre 0 y 1 (para la barra de progreso). */
    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        /** Se considera visto al llegar al 90 %. */
        const val COMPLETED_THRESHOLD = 0.9f
        fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && positionMs.toFloat() / durationMs >= COMPLETED_THRESHOLD
    }
}
