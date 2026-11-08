package com.animehub.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class PreferredQuality(val etiqueta: String) {
    AUTO("Automática"), Q1080("1080p"), Q720("720p"), Q480("480p");

    fun next(): PreferredQuality = entries[(ordinal + 1) % entries.size]
}

data class AppSettings(
    val remoteConfigUrl: String = SettingsRepository.DEFAULT_REMOTE_CONFIG_URL,
    /** Activación de sitios elegida por el usuario (si no está, se usa la de la configuración). */
    val siteEnabled: Map<String, Boolean> = emptyMap(),
    /** Dominios editados por el usuario. */
    val domainOverrides: Map<String, String> = emptyMap(),
    /** Servidor preferido por sitio. */
    val preferredServers: Map<String, String> = emptyMap(),
    val preferredQuality: PreferredQuality = PreferredQuality.AUTO,
    /** Segundos de avance/retroceso (10 o 30). */
    val seekSeconds: Int = 10,
    val lastConfigUpdate: Long = 0L,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "ajustes")

/** Ajustes de la app guardados con DataStore. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.settingsStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            remoteConfigUrl = p[REMOTE_URL] ?: DEFAULT_REMOTE_CONFIG_URL,
            siteEnabled = decodeMap(p[SITE_ENABLED]).mapValues { it.value.toBoolean() },
            domainOverrides = decodeMap(p[DOMAINS]),
            preferredServers = decodeMap(p[SERVERS]),
            preferredQuality = p[QUALITY]?.let { runCatching { PreferredQuality.valueOf(it) }.getOrNull() }
                ?: PreferredQuality.AUTO,
            seekSeconds = p[SEEK] ?: 10,
            lastConfigUpdate = p[LAST_UPDATE] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setRemoteConfigUrl(url: String) = store.edit { it[REMOTE_URL] = url.trim() }

    suspend fun resetRemoteConfigUrl() = store.edit { it.remove(REMOTE_URL) }

    suspend fun setSiteEnabled(siteId: String, enabled: Boolean) = updateMap(SITE_ENABLED, siteId, enabled.toString())

    suspend fun setDomainOverride(siteId: String, baseUrl: String?) = updateMap(DOMAINS, siteId, baseUrl?.trim())

    suspend fun setPreferredServer(siteId: String, serverName: String?) = updateMap(SERVERS, siteId, serverName)

    suspend fun clearPreferredServers() = store.edit { it.remove(SERVERS) }

    suspend fun setPreferredQuality(quality: PreferredQuality) = store.edit { it[QUALITY] = quality.name }

    suspend fun setSeekSeconds(seconds: Int) = store.edit { it[SEEK] = seconds }

    suspend fun setLastConfigUpdate(timestamp: Long) = store.edit { it[LAST_UPDATE] = timestamp }

    private suspend fun updateMap(key: Preferences.Key<Set<String>>, id: String, value: String?) {
        store.edit { prefs ->
            val map = decodeMap(prefs[key]).toMutableMap()
            if (value.isNullOrBlank()) map.remove(id) else map[id] = value
            prefs[key] = encodeMap(map)
        }
    }

    companion object {
        /** Configuración publicada en el repositorio (se puede cambiar en Ajustes). */
        const val DEFAULT_REMOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/Yeikoromeo2/Anime-proyect-/main/config/sites.json"

        private val REMOTE_URL = stringPreferencesKey("remote_config_url")
        private val SITE_ENABLED = stringSetPreferencesKey("site_enabled")
        private val DOMAINS = stringSetPreferencesKey("domain_overrides")
        private val SERVERS = stringSetPreferencesKey("preferred_servers")
        private val QUALITY = stringPreferencesKey("preferred_quality")
        private val SEEK = intPreferencesKey("seek_seconds")
        private val LAST_UPDATE = longPreferencesKey("last_config_update")

        // Los mapas se guardan como conjuntos "clave=valor" (los ids de sitio no llevan '=').
        internal fun decodeMap(set: Set<String>?): Map<String, String> =
            set.orEmpty().mapNotNull { entry ->
                val i = entry.indexOf('=')
                if (i <= 0) null else entry.substring(0, i) to entry.substring(i + 1)
            }.toMap()

        internal fun encodeMap(map: Map<String, String>): Set<String> = map.map { "${it.key}=${it.value}" }.toSet()
    }
}
