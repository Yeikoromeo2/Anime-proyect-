package com.animehub.tv.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * "Enviar a mi TV": lo que la tablet le pide a la AnimeHub de la TV por la red de casa.
 *
 * - `GET /info` → [DeviceInfo] (abierto).
 * - `POST /play` [PlayRequest] → la TV abre ese episodio en su reproductor.
 * - `POST /command` [RemoteCommand] → pausa, saltos, siguiente, anterior, detener.
 * - `GET /status` → [RemoteStatus] (qué se está viendo y en qué minuto).
 *
 * Cada petición lleva el identificador y el nombre del dispositivo; la primera vez la TV pregunta
 * si se le permite controlarla.
 */
object RemoteProtocol {
    /** Tipo de servicio que la TV anuncia en la red (NSD / mDNS). */
    const val SERVICE_TYPE = "_animehub._tcp."
    const val HEADER_ID = "X-AnimeHub-Device"
    /** Nombre del dispositivo, codificado (las cabeceras HTTP no admiten acentos). */
    const val HEADER_NAME = "X-AnimeHub-Name"
    /** Atributo del anuncio NSD con el identificador de la TV (para no listarse a sí misma). */
    const val ATTR_ID = "id"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeName(name: String): String = URLEncoder.encode(name, "UTF-8")
    fun decodeName(value: String?): String? = value?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
}

@Serializable
data class DeviceInfo(val id: String, val name: String, val version: String)

@Serializable
data class PlayRequest(
    val siteId: String,
    val animeUrl: String,
    val animeTitle: String,
    val coverUrl: String? = null,
    val episodeUrl: String,
    val episodeNumber: Float = 1f,
    /** Minuto donde iba en la tablet (0 = el que tenga guardado la TV). */
    val startMs: Long = 0,
)

/** @param type "toggle", "seekBy" (con [seconds]), "next", "previous" o "stop". */
@Serializable
data class RemoteCommand(val type: String, val seconds: Int = 0)

/** @param state "idle" (nada), "loading" (buscando servidores), "playing", "paused" o "finished". */
@Serializable
data class RemoteStatus(
    val state: String = "idle",
    val title: String? = null,
    val episode: Float? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val server: String? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)
