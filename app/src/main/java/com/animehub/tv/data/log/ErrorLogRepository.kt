package com.animehub.tv.data.log

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ErrorEntry(val timestamp: Long, val source: String, val message: String)

/** Últimos 50 errores, visibles en Ajustes para saber cuándo un sitio cambió. */
@Singleton
class ErrorLogRepository @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(context.filesDir, "registro_errores.json")
    private val serializer = ListSerializer(ErrorEntry.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<ErrorEntry>> = _entries.asStateFlow()

    fun log(source: String, message: String) {
        // El mismo error no se repite: solo se cuenta una vez cada 12 horas.
        val now = System.currentTimeMillis()
        if (_entries.value.any { it.source == source && it.message == message && now - it.timestamp < REPEAT_MS }) return
        _entries.update { (listOf(ErrorEntry(now, source, message)) + it).take(MAX) }
        save()
    }

    /**
     * Guarda solo lo que hay que arreglar en el código (el sitio cambió su página, un dato dejó de
     * leerse…). Nada de red caída, sitio lento, verificación de Cloudflare o "no cargó, se probó otro".
     */
    fun logIssue(source: String, what: String, error: Throwable) {
        if (!isActionable(error)) return
        log(source, "$what: ${error::class.simpleName}: ${error.message?.take(160)}")
    }

    fun clear() {
        _entries.value = emptyList()
        save()
    }

    private fun load(): List<ErrorEntry> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    private fun save() {
        runCatching { file.writeText(json.encodeToString(serializer, _entries.value)) }
    }

    companion object {
        private const val MAX = 50
        private const val REPEAT_MS = 12 * 60 * 60_000L

        /** Un fallo de lectura o de código sí; un fallo de red, tiempo o protección no. */
        fun isActionable(error: Throwable): Boolean = when (error) {
            is java.io.IOException, is kotlinx.coroutines.TimeoutCancellationException,
            is kotlinx.coroutines.CancellationException, is com.animehub.tv.data.net.SiteProtectedException -> false
            else -> true
        }
    }
}
