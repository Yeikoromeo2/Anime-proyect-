package com.animehub.tv.vod.cache

import java.io.File

/**
 * Caché en disco de listados, categorías y fichas (texto de las respuestas) con expiración de 6 horas.
 * Las direcciones finales de reproducción NO pasan por aquí nunca (pueden caducar).
 */
class VodCache(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {

    /** La copia si tiene menos de [ttlMs]; si no, [fetch] (y si falla, la copia vieja si existe). */
    suspend fun text(key: String, ttlMs: Long = TTL_MS, fetch: suspend () -> String?): String? {
        dir.mkdirs()
        val file = File(dir, name(key))
        if (file.exists() && clock() - file.lastModified() < ttlMs) return file.readText()
        val fresh = fetch()
        if (fresh != null) {
            file.writeText(fresh)
            file.setLastModified(clock())
            return fresh
        }
        return if (file.exists()) file.readText() else null
    }

    /** Actualización manual: borra todo lo guardado. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun name(key: String) = Integer.toHexString(key.hashCode()) + "-" + key.filter { it.isLetterOrDigit() }.takeLast(24) + ".json"

    companion object {
        const val TTL_MS = 6 * 60 * 60_000L
    }
}
