package com.animehub.tv.domain.model

import com.animehub.tv.util.Format

/** Posición en la lista de episodios: temporada y tramo (bloques de [EpisodePager.CHUNK]). */
data class EpisodeSection(val season: Int?, val chunk: Int)

/**
 * Organiza listas largas (One Piece: 23 temporadas y 1180 episodios) en temporadas y tramos de
 * 50 para elegirlos con un desplegable y llegar directo al episodio.
 */
object EpisodePager {
    const val CHUNK = 50

    /** Temporadas en el orden del sitio (null = el sitio no usa temporadas). */
    fun seasons(episodes: List<Episode>): List<Int?> = episodes.map { it.season }.distinct()

    fun episodesOf(episodes: List<Episode>, season: Int?): List<Episode> = episodes.filter { it.season == season }

    fun chunks(seasonEpisodes: List<Episode>): List<List<Episode>> =
        if (seasonEpisodes.isEmpty()) listOf(emptyList()) else seasonEpisodes.chunked(CHUNK)

    /** "1–50", "51–100"… con los números reales de los episodios. */
    fun chunkLabel(chunk: List<Episode>): String =
        if (chunk.isEmpty()) "" else "${Format.episode(chunk.first().number)}–${Format.episode(chunk.last().number)}"

    /** Dónde está un episodio (por URL). */
    fun locate(episodes: List<Episode>, url: String?): EpisodeSection? {
        if (url == null) return null
        val episode = episodes.firstOrNull { sameUrl(it.url, url) } ?: return null
        return sectionOf(episodes, episode)
    }

    fun sectionOf(episodes: List<Episode>, episode: Episode): EpisodeSection {
        val inSeason = episodesOf(episodes, episode.season)
        return EpisodeSection(episode.season, inSeason.indexOf(episode).coerceAtLeast(0) / CHUNK)
    }

    /** Busca un número de episodio, primero en la temporada actual y luego en todas. */
    fun findByNumber(episodes: List<Episode>, number: Float, preferSeason: Int?): Episode? =
        episodes.firstOrNull { it.season == preferSeason && it.number == number } ?: episodes.firstOrNull { it.number == number }

    fun sameUrl(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')
}
