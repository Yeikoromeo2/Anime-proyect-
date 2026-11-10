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

/**
 * Calidad del video. Automática: se adapta a la velocidad de internet. Alta calidad: la más alta
 * que tenga el video, aunque tarde más en cargar.
 */
enum class PreferredQuality(val etiqueta: String, val detalle: String) {
    AUTO("Automática", "se adapta a tu internet"),
    HIGH("Alta calidad", "siempre la más alta del video");

    fun next(): PreferredQuality = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Lee lo guardado; las versiones anteriores tenían 1080p/720p/480p. */
        fun fromStored(name: String?): PreferredQuality = when (name) {
            null -> AUTO
            "Q1080" -> HIGH
            else -> entries.firstOrNull { it.name == name } ?: AUTO
        }
    }
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
    /** Sitio que se está viendo en Inicio (null = todos mezclados); también es el de Buscar y Explorar al entrar. */
    val homeSite: String? = null,
    /** "Enviar a mi TV": dispositivos (tablets) que esta TV ya aceptó, por su identificador. */
    val allowedRemotes: Set<String> = emptySet(),
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
            preferredQuality = PreferredQuality.fromStored(p[QUALITY]),
            seekSeconds = p[SEEK] ?: 10,
            lastConfigUpdate = p[LAST_UPDATE] ?: 0L,
            homeSite = p[HOME_SITE]?.takeIf { it.isNotBlank() },
            allowedRemotes = p[ALLOWED_REMOTES].orEmpty(),
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

    suspend fun setHomeSite(siteId: String?) = store.edit { if (siteId == null) it.remove(HOME_SITE) else it[HOME_SITE] = siteId }

    /** Identificador de este aparato para "Enviar a mi TV" (se crea una vez, al azar). */
    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        val id = java.util.UUID.randomUUID().toString()
        store.edit { if (it[DEVICE_ID] == null) it[DEVICE_ID] = id }
        return store.data.first()[DEVICE_ID] ?: id
    }

    suspend fun allowRemote(deviceId: String) = store.edit { it[ALLOWED_REMOTES] = it[ALLOWED_REMOTES].orEmpty() + deviceId }

    suspend fun forgetRemotes() = store.edit { it.remove(ALLOWED_REMOTES) }

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

        /** Última versión publicada (Releases) para actualizar desde la app. */
        const val DEFAULT_RELEASES_API_URL =
            "https://api.github.com/repos/Yeikoromeo2/Anime-proyect-/releases/latest"

        private val REMOTE_URL = stringPreferencesKey("remote_config_url")
        private val SITE_ENABLED = stringSetPreferencesKey("site_enabled")
        private val DOMAINS = stringSetPreferencesKey("domain_overrides")
        private val SERVERS = stringSetPreferencesKey("preferred_servers")
        private val QUALITY = stringPreferencesKey("preferred_quality")
        private val SEEK = intPreferencesKey("seek_seconds")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val ALLOWED_REMOTES = stringSetPreferencesKey("allowed_remotes")
        private val LAST_UPDATE = longPreferencesKey("last_config_update")
        private val HOME_SITE = stringPreferencesKey("home_site")

        // Los mapas se guardan como conjuntos "clave=valor" (los ids de sitio no llevan '=').
        internal fun decodeMap(set: Set<String>?): Map<String, String> =
            set.orEmpty().mapNotNull { entry ->
                val i = entry.indexOf('=')
                if (i <= 0) null else entry.substring(0, i) to entry.substring(i + 1)
            }.toMap()

        internal fun encodeMap(map: Map<String, String>): Set<String> = map.map { "${it.key}=${it.value}" }.toSet()
    }
}
