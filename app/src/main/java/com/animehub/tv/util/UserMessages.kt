package com.animehub.tv.util

import com.animehub.tv.data.config.ConfigException
import com.animehub.tv.data.net.ProtectionState
import com.animehub.tv.data.net.SiteHttpException
import com.animehub.tv.data.net.SiteProtectedException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

/** Mensajes de error claros y en español para mostrar en pantalla. */
object UserMessages {
    fun forError(siteName: String, error: Throwable): String = when (error) {
        is SiteProtectedException -> when (error.state) {
            ProtectionState.ORIGEN_CAIDO -> "$siteName no responde (su servidor está caído)"
            ProtectionState.BLOQUEADO -> "$siteName bloqueó la conexión"
            else -> "$siteName pide una verificación de seguridad"
        }
        is TimeoutCancellationException -> "$siteName tardó demasiado en responder"
        is SiteHttpException -> "$siteName: ${error.message}"
        is ConfigException -> "$siteName: ${error.message}"
        is IOException -> "$siteName no responde. Revisa la conexión"
        else -> "$siteName: error inesperado (${error.message ?: error.javaClass.simpleName})"
    }
}
