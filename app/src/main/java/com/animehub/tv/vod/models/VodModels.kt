package com.animehub.tv.vod.models

/** Película o serie. */
enum class MediaType(val key: String, val title: String) {
    MOVIE("movie", "Películas"),
    SERIES("series", "Series");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: MOVIE
    }
}

/** De dónde sale un título: catálogo comunitario abierto o el servidor del usuario (Xtream). */
enum class ProviderId(val key: String, val label: String) {
    COMMUNITY("community", "Comunidad"),
    XTREAM("xtream", "Servidor");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: COMMUNITY
    }
}

/** Título normalizado, igual sea cual sea la fuente. */
data class MediaItem(
    val id: String,
    val imdbId: String?,
    val title: String,
    val type: MediaType,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val year: String?,
    val genres: List<String>,
    /** Valoración de 0 a 10 (null = sin dato). */
    val rating: Float?,
    val provider: ProviderId,
)

data class Episode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String?,
    val thumbnail: String?,
    /** Identificador del episodio en el servidor (Xtream); null en el catálogo comunitario. */
    val streamId: String?,
    val containerExtension: String?,
)

data class Season(val seasonNumber: Int, val episodes: List<Episode>) {
    /** "Temporada 2" o "Especiales" (la 0). */
    val label: String get() = if (seasonNumber == 0) "Especiales" else "Temporada $seasonNumber"
}

/** Un título con sus temporadas (vacías en las películas). */
data class MediaDetail(val item: MediaItem, val seasons: List<Season>)

/** Una fuente de reproducción ya resuelta. Nunca se guarda en caché: las direcciones pueden caducar. */
data class StreamSource(
    val name: String,
    val quality: String,
    val url: String,
    val headers: Map<String, String>,
    val isDirect: Boolean,
)

data class VodCategory(val id: String, val name: String)

/** Qué reproducir: una película o un episodio de una serie. */
data class StreamRequest(val item: MediaItem, val episode: Episode?)
