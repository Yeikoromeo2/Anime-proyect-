package com.animehub.tv.extractors

import com.animehub.tv.data.net.CloudflareDetector
import com.animehub.tv.data.net.ProtectionState
import com.animehub.tv.domain.model.ServerHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Resultado de la comprobación con el motivo para mostrar en pantalla. */
data class HealthResult(val health: ServerHealth, val detail: String? = null)

/**
 * Comprobación rápida de un servidor de video: ¿responde y no dice "video borrado"?
 * No garantiza que reproduzca, pero descarta los que seguro fallan.
 */
class ServerChecker(baseClient: OkHttpClient, private val deadMarkers: List<String>) {

    private val client = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()

    suspend fun check(url: String, referer: String?): HealthResult = withContext(Dispatchers.IO) {
        if (UNVERIFIABLE_HOSTS.any { url.contains(it) }) return@withContext HealthResult(ServerHealth.SIN_VERIFICAR, "Se comprueba al reproducir")
        val request = runCatching {
            Request.Builder().url(url).apply { referer?.let { header("Referer", it) } }.build()
        }.getOrElse { return@withContext HealthResult(ServerHealth.NO_DISPONIBLE, "Enlace inválido") }
        try {
            client.newCall(request).execute().use { response ->
                val type = response.header("Content-Type").orEmpty().lowercase()
                if (response.isSuccessful && (type.startsWith("video/") || type.contains("mpegurl"))) {
                    return@use HealthResult(ServerHealth.FUNCIONA)
                }
                val body = response.peekBody(MAX_BYTES).string()
                val protection = CloudflareDetector.detect(response.code, response::header, body)
                when {
                    protection == ProtectionState.ORIGEN_CAIDO -> HealthResult(ServerHealth.NO_DISPONIBLE, "No responde")
                    protection.isChallenge -> HealthResult(ServerHealth.SIN_VERIFICAR, "Pide verificación")
                    response.code == 404 || response.code == 410 -> HealthResult(ServerHealth.NO_DISPONIBLE, "Video borrado")
                    response.code >= 500 -> HealthResult(ServerHealth.NO_DISPONIBLE, "Error del servidor")
                    !response.isSuccessful -> HealthResult(ServerHealth.SIN_VERIFICAR, "Respondió ${response.code}")
                    body.startsWith("#EXTM3U") -> HealthResult(ServerHealth.FUNCIONA)
                    isDead(body) -> HealthResult(ServerHealth.NO_DISPONIBLE, "Video borrado")
                    else -> HealthResult(ServerHealth.FUNCIONA)
                }
            }
        } catch (e: Exception) {
            HealthResult(ServerHealth.NO_DISPONIBLE, "No responde")
        }
    }

    private fun isDead(body: String): Boolean {
        val lower = body.lowercase()
        return deadMarkers.any { it.isNotBlank() && lower.contains(it.lowercase()) }
    }

    private companion object {
        const val MAX_BYTES = 256L * 1024
        /** Reproductores que solo funcionan con JavaScript (la página siempre carga). */
        val UNVERIFIABLE_HOSTS = listOf("mega.nz", "terabox.com")
    }
}
