package com.animehub.tv.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
/** @param voice abre el dictado al entrar (atajo de voz desde Inicio). */
@Serializable data class SearchRoute(val voice: Boolean = false)
/**
 * Catálogo completo por categorías.
 * @param genre género elegido en Inicio (valor común entre sitios, p. ej. "accion"); null = todo.
 */
@Serializable data class ExploreRoute(val genre: String? = null, val voice: Boolean = false)
@Serializable object HistoryRoute
@Serializable object SettingsRoute

/**
 * Ficha de un anime.
 * @param episodeUrl capítulo a destacar (al venir de "Últimos capítulos"): se marca como nuevo y recibe el foco.
 */
@Serializable
data class AnimeRoute(
    val siteId: String,
    val url: String,
    val title: String,
    val coverUrl: String? = null,
    val episodeUrl: String? = null,
)

/** Reproducir un episodio: elige servidor (o usa el preferido), reproduce y sigue con el siguiente. */
@Serializable
data class PlayerRoute(
    val siteId: String,
    val animeUrl: String,
    val animeTitle: String,
    val coverUrl: String? = null,
    val episodeUrl: String,
    val episodeNumber: Float = 1f,
    /** Minuto donde empezar (lo envía la tablet con "Enviar a mi TV"); 0 = el guardado. */
    val startMs: Long = 0,
)

/**
 * Control remoto de la TV desde la tablet ("Enviar a mi TV"): envía el episodio a la TV y lo
 * maneja desde aquí (pausa, saltos, siguiente).
 */
@Serializable
data class RemoteRoute(
    /** "animehub" (TV con AnimeHub), "cast" (Chromecast) o "dlna" (Samsung, LG…). */
    val kind: String = "animehub",
    /** Solo DLNA: dirección de control del reproductor de la TV. */
    val controlUrl: String? = null,
    val tvId: String,
    val tvName: String,
    val tvHost: String,
    val tvPort: Int,
    val siteId: String,
    val animeUrl: String,
    val animeTitle: String,
    val coverUrl: String? = null,
    val episodeUrl: String,
    val episodeNumber: Float = 1f,
    val startMs: Long = 0,
)
