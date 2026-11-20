package com.animehub.tv.playback

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servidores de video (por host del video directo) donde el reproductor nativo falló hace poco.
 * Durante un rato se abren directamente en el reproductor web: así no se prueba el nativo, se
 * espera el error y se cambia (con su aviso) en cada episodio.
 */
@Singleton
class NativeFailures(
    private val ttlMs: Long = TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor() : this(TTL_MS, System::currentTimeMillis)

    private val failedAt = mutableMapOf<String, Long>()

    @Synchronized
    fun record(streamUrl: String) {
        host(streamUrl)?.let { failedAt[it] = clock() }
    }

    @Synchronized
    fun isBroken(streamUrl: String): Boolean {
        val host = host(streamUrl) ?: return false
        val at = failedAt[host] ?: return false
        if (clock() - at > ttlMs) {
            failedAt.remove(host)
            return false
        }
        return true
    }

    private fun host(url: String) = runCatching { URI(url).host?.lowercase() }.getOrNull()

    companion object {
        /** 30 minutos: suficiente para una tanda de episodios; después se vuelve a intentar. */
        const val TTL_MS = 30 * 60_000L
    }
}
