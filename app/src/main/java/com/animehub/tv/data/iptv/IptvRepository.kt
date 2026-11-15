package com.animehub.tv.data.iptv

import android.content.Context
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.IptvListConfig
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.IptvList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TV en vivo: baja las listas M3U de la configuración (con copia en disco: si no hay red se usa la
 * última), las lee y las junta sin repetidos. El resultado queda en memoria mientras la app esté abierta.
 */
@Singleton
class IptvRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val configRepository: ConfigRepository,
    private val errorLog: ErrorLogRepository,
) {
    private val dir get() = File(context.filesDir, "iptv").apply { mkdirs() }
    private val mutex = Mutex()
    private var cached: List<IptvList>? = null

    /** Todas las listas, listas para mostrar (la primera vez tarda; luego queda en memoria). */
    suspend fun lists(): List<IptvList> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    private suspend fun load(): List<IptvList> = coroutineScope {
        val configured = configRepository.loadLocal().config.iptvLists
        val sources = configured.map { cfg -> async { Triple(cfg.id, cfg.name, runCatching { M3uParser.parseEntries(text(cfg)) }.getOrDefault(emptyList())) } }.awaitAll()
        M3uParser.build(sources, onDemandIds = configured.filter { it.kind == "vod" }.map { it.id }.toSet())
    }

    /** Texto de la lista: la copia en disco si es reciente; si no, se baja (y si falla, la copia vieja). */
    private fun text(cfg: IptvListConfig): String {
        val file = File(dir, "${cfg.id}.m3u")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < FRESH_MS) return file.readText()
        return try {
            client.newCall(Request.Builder().url(cfg.url).build()).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                r.body.string().also { file.writeText(it) }
            }
        } catch (e: Exception) {
            if (file.exists()) file.readText() else { errorLog.logIssue("TV en vivo", cfg.name, e); "" }
        }
    }

    /** Fuerza una descarga nueva la próxima vez. */
    suspend fun refresh() = mutex.withLock {
        cached = null
        dir.listFiles()?.forEach { it.setLastModified(0) }
    }

    private companion object {
        const val FRESH_MS = 12 * 60 * 60_000L
    }
}
