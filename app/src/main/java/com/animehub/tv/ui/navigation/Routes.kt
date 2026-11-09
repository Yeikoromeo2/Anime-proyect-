package com.animehub.tv.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
/** @param voice abre el dictado al entrar (atajo de voz desde Inicio). */
@Serializable data class SearchRoute(val voice: Boolean = false)
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
