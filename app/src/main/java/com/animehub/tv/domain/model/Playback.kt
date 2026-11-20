package com.animehub.tv.domain.model

/** Resultado de comprobar si un servidor responde. */
enum class ServerHealth(val etiqueta: String) {
    FUNCIONA("Funciona"),
    SIN_VERIFICAR("Sin verificar"),
    NO_DISPONIBLE("No disponible"),
}

/** Cómo se reproduce: en el reproductor nativo (Fase 5) o en la página del servidor. */
enum class PlayerKind(val etiqueta: String) { NATIVO("Reproductor nativo"), WEB("Reproductor web") }

/** Formato del video directo. */
enum class StreamType(val etiqueta: String) { HLS("HLS"), MP4("MP4") }

/**
 * Enlace directo al video (sin la página del servidor) para el reproductor nativo.
 * @param headers cabeceras que pide el servidor del video (Referer, Origin…).
 */
data class DirectStream(
    val url: String,
    val type: StreamType,
    val headers: Map<String, String> = emptyMap(),
)

/** Servidor listo para elegir en pantalla. */
data class ServerOption(
    val server: VideoServer,
    val health: ServerHealth,
    val preferred: Boolean = false,
    /** Video directo si el servidor lo entrega: se reproduce en el reproductor nativo. */
    val stream: DirectStream? = null,
    /** Motivo si no está disponible ("Video borrado", "No responde"…). */
    val detail: String? = null,
) {
    val kind: PlayerKind get() = if (stream != null) PlayerKind.NATIVO else PlayerKind.WEB
}
