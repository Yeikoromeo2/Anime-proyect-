package com.animehub.tv.domain.model

/** Resultado de comprobar si un servidor responde. */
enum class ServerHealth(val etiqueta: String) {
    FUNCIONA("Funciona"),
    SIN_VERIFICAR("Sin verificar"),
    NO_DISPONIBLE("No disponible"),
}

/** Cómo se reproduce: en el reproductor nativo (Fase 5) o en la página del servidor. */
enum class PlayerKind(val etiqueta: String) { NATIVO("Reproductor nativo"), WEB("Reproductor web") }

/** Servidor listo para elegir en pantalla. */
data class ServerOption(
    val server: VideoServer,
    val health: ServerHealth,
    val preferred: Boolean = false,
    val kind: PlayerKind = PlayerKind.WEB,
    /** Motivo si no está disponible ("Video borrado", "No responde"…). */
    val detail: String? = null,
)
