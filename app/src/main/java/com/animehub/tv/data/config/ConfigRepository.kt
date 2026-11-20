package com.animehub.tv.data.config

import android.content.Context
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.data.settings.AppSettings
import com.animehub.tv.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ConfigSource(val etiqueta: String) { INCLUIDA("incluida en la app"), CACHE("guardada"), REMOTA("descargada") }

data class ConfigState(val config: RemoteConfig, val source: ConfigSource)

/** Sitio con los ajustes del usuario aplicados (dominio editado y activación). */
data class EffectiveSite(val config: SiteConfig, val baseUrl: String, val enabled: Boolean, val domainEdited: Boolean) {
    val id: String get() = config.id
    val name: String get() = config.name
}

/**
 * Carga la configuración: caché → copia incluida, y la actualiza desde la URL remota.
 */
@Singleton
class ConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val errorLog: ErrorLogRepository,
) {
    private val cacheFile = File(context.filesDir, "config/sites.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ConfigState?>(null)
    val state: StateFlow<ConfigState?> = _state.asStateFlow()

    val sites: Flow<List<EffectiveSite>> =
        combine(_state.filterNotNull(), settingsRepository.settings) { state, settings -> applySettings(state.config, settings) }

    /** Carga la configuración local (caché si es válida y no más vieja que la incluida). */
    suspend fun loadLocal(): ConfigState = mutex.withLock {
        _state.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            val bundled = ConfigParser.parse(context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() })
            val cached = runCatching { ConfigParser.parse(cacheFile.readText()) }.getOrNull()
            if (cached != null && cached.version >= bundled.version) ConfigState(cached, ConfigSource.CACHE)
            else ConfigState(bundled, ConfigSource.INCLUIDA)
        }
        _state.value = loaded
        publishPriorities(loaded.config)
        loaded
    }

    /** Descarga la configuración remota. Si falla, conserva la actual y devuelve el error. */
    suspend fun refresh(): Result<ConfigState> {
        loadLocal()
        val url = settingsRepository.current().remoteConfigUrl
        return runCatching {
            if (!ConfigParser.isHttpUrl(url)) throw ConfigException("La URL de configuración no es válida")
            val text = withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("La configuración respondió ${response.code}")
                    response.body.string()
                }
            }
            val config = ConfigParser.parse(text)
            withContext(Dispatchers.IO) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(text)
            }
            settingsRepository.setLastConfigUpdate(System.currentTimeMillis())
            ConfigState(config, ConfigSource.REMOTA).also { _state.value = it; publishPriorities(config) }
        }.onFailure { errorLog.log("Configuración", it.message ?: it.javaClass.simpleName) }
    }

    companion object {
        /** Ruta dentro de assets (la carpeta /config del repo se añade como assets al compilar). */
        const val BUNDLED_ASSET = "sites.json"

        fun applySettings(config: RemoteConfig, settings: AppSettings): List<EffectiveSite> =
            config.sites.map { site ->
                val override = settings.domainOverrides[site.id]?.takeIf { ConfigParser.isHttpUrl(it) }
                EffectiveSite(
                    config = site,
                    baseUrl = ConfigParser.normalizeBaseUrl(override ?: site.baseUrl),
                    enabled = !site.isPending && (settings.siteEnabled[site.id] ?: site.enabled),
                    domainEdited = override != null,
                )
            }
    }

    /** El orden de preferencia entre sitios, para elegir la versión principal de un anime repetido. */
    private fun publishPriorities(config: RemoteConfig) {
        com.animehub.tv.util.SameAnimeIndex.priorities = config.sites.associate { it.id to it.priority }
    }
}
