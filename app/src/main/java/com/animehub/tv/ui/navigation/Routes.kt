package com.animehub.tv.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
/** @param voice abre el dictado al entrar (atajo de voz desde Inicio). */
@Serializable data class SearchRoute(val voice: Boolean = false)
@Serializable object HistoryRoute
@Serializable object SettingsRoute

/** Ficha de un anime (la pantalla completa llega con el primer proveedor, Fase 2). */
@Serializable
data class AnimeRoute(val siteId: String, val url: String, val title: String, val coverUrl: String? = null)
