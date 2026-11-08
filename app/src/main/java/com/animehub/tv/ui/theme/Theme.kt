package com.animehub.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object AppColors {
    val Fondo = Color(0xFF0F0D18)
    val Superficie = Color(0xFF1B1730)
    val SuperficieFoco = Color(0xFF3A2D73)
    val Acento = Color(0xFF7C5CFF)
    val Acento2 = Color(0xFF00D4BE)
    val Foco = Color(0xFFFFFFFF)
    val TextoSecundario = Color(0xFFB9B4CC)
    val Error = Color(0xFFFF6B6B)
}

@Composable
fun AnimeHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Acento,
            onPrimary = Color.White,
            secondary = AppColors.Acento2,
            background = AppColors.Fondo,
            onBackground = Color.White,
            surface = AppColors.Superficie,
            onSurface = Color.White,
            onSurfaceVariant = AppColors.TextoSecundario,
            error = AppColors.Error,
        ),
    ) {
        // Texto blanco por defecto en toda la app (los textos fuera de un Surface salían en negro).
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
