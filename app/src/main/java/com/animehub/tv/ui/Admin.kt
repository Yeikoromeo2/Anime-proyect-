package com.animehub.tv.ui

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modo administrador: sin él la app solo muestra "AnimeHub TV" (nada de sitios, servidores ni
 * direcciones). Con él se ven los sitios, los servidores, la configuración y el registro de
 * errores. Se abre con un PIN desde Ajustes → "Versión de la app" y se cierra solo al salir de la app.
 */
@Singleton
class AdminMode @Inject constructor() {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun unlock(pin: String): Boolean {
        if (pin.trim() != PIN) return false
        _enabled.value = true
        return true
    }

    fun lock() {
        _enabled.value = false
    }

    companion object {
        const val PIN = "041992"
    }
}

/** ¿Está activo el modo administrador? Las pantallas lo leen para mostrar u ocultar lo técnico. */
val LocalAdmin = compositionLocalOf { false }
