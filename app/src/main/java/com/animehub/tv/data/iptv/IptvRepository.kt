package com.animehub.tv.data.iptv

import android.content.Context
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.IptvListConfig
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.domain.model.IptvList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TV en vivo: las listas M3U de la configuración se bajan solo cuando se abren (las generales pesan
 * unos 3 MB), con copia en disco (si no hay red se usa la última) y se leen sin repetidos.
 * En memoria quedan las últimas listas abiertas.
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
    private val memory = object : LinkedHashMap<String, IptvList>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IptvList>?) = size > MEMORY_LISTS
    }

    /** Las listas del menú, en el orden de la configuración (sin descargar nada). */
    suspend fun menu(): List<IptvListConfig> = configRepository.loadLocal().config.iptvLists

    /** Una lista del menú, ya leída (la primera vez la baja). */
    suspend fun list(id: String): IptvList? = mutex.withLock {
        memory[id] ?: menu().firstOrNull { it.id == id }?.let { cfg -> build(cfg).also { memory[id] = it } }
    }

    /** La lista con la que arranca la TV la primera vez (Perú), que no sale en el menú. */
    suspend fun startList(): IptvList? = mutex.withLock {
        memory[START_ID] ?: configRepository.loadLocal().config.iptvStartUrl?.let { url ->
            build(IptvListConfig(START_ID, "Perú", url)).also { memory[START_ID] = it }
        }
    }

    private suspend fun build(cfg: IptvListConfig): IptvList = withContext(Dispatchers.IO) {
        val entries = runCatching { M3uParser.parseEntries(text(cfg)) }.getOrDefault(emptyList())
        M3uParser.buildList(cfg.id, cfg.name, entries, cfg.kind, cfg.order, cfg.translate)
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
        memory.clear()
        dir.listFiles()?.forEach { it.setLastModified(0) }
    }

    companion object {
        const val START_ID = "inicio"
        private const val FRESH_MS = 12 * 60 * 60_000L
        private const val MEMORY_LISTS = 3
    }
}
