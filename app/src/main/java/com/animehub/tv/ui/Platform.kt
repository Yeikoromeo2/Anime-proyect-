package com.animehub.tv.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * ¿Es un televisor (control remoto) o una tablet/teléfono (táctil)? Cambia los textos de ayuda
 * ("OK pausa" o "toca la pantalla") y muestra los controles táctiles del reproductor.
 */
fun isTv(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTv(context) }
}
