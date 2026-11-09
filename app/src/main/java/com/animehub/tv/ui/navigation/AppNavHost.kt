package com.animehub.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.animehub.tv.ui.anime.AnimeScreen
import com.animehub.tv.ui.history.HistoryScreen
import com.animehub.tv.ui.home.HomeScreen
import com.animehub.tv.ui.player.PlayerScreen
import com.animehub.tv.ui.search.SearchScreen
import com.animehub.tv.ui.settings.SettingsScreen

/**
 * Navegación de la app. "Atrás" cierra primero diálogos/paneles (lo gestionan los propios
 * diálogos), luego vuelve a la pantalla anterior y en Inicio pide confirmación para salir.
 */
@Composable
fun AppNavHost(onExit: () -> Unit) {
    val nav = rememberNavController()
    val openAnime: (String, String, String, String?) -> Unit = { siteId, url, title, cover ->
        nav.navigate(AnimeRoute(siteId, url, title, cover))
    }
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onSearch = { nav.navigate(SearchRoute()) },
                onVoiceSearch = { nav.navigate(SearchRoute(voice = true)) },
                onHistory = { nav.navigate(HistoryRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
                onOpenAnime = openAnime,
                onResume = { p -> nav.navigate(PlayerRoute(p.siteId, p.animeUrl, p.animeTitle, p.coverUrl, p.episodeUrl, p.episodeNumber)) },
                onOpenEpisode = { siteId, animeUrl, episodeUrl, title, image ->
                    nav.navigate(AnimeRoute(siteId, animeUrl, title, image, episodeUrl))
                },
                onExit = onExit,
            )
        }
        composable<SearchRoute> { entry -> SearchScreen(onOpenAnime = openAnime, startVoice = entry.toRoute<SearchRoute>().voice) }
        composable<HistoryRoute> {
            HistoryScreen(
                onOpenAnime = openAnime,
                onResume = { p -> nav.navigate(PlayerRoute(p.siteId, p.animeUrl, p.animeTitle, p.coverUrl, p.episodeUrl, p.episodeNumber)) },
            )
        }
        composable<SettingsRoute> { SettingsScreen() }
        composable<AnimeRoute> {
            AnimeScreen(
                onBack = { nav.popBackStack() },
                onPlay = { route -> nav.navigate(route) },
            )
        }
        composable<PlayerRoute> { PlayerScreen(onExit = { nav.popBackStack() }) }
    }
}
