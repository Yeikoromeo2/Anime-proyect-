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
        _entries.update { (listOf(ErrorEntry(System.currentTimeMillis(), source, message)) + it).take(MAX) }
        save()
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

    private companion object {
        const val MAX = 50
    }
}
