package com.animehub.tv.data.config

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URI

class ConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Lee y valida el JSON de configuración. */
object ConfigParser {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val ID_REGEX = Regex("^[a-z0-9_-]+$")

    fun parse(text: String): RemoteConfig {
        val config = try {
            json.decodeFromString(RemoteConfig.serializer(), text)
        } catch (e: SerializationException) {
            throw ConfigException("El archivo de configuración no es válido: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ConfigException("El archivo de configuración no es válido: ${e.message}", e)
        }
        validate(config)
        return config
    }

    fun validate(config: RemoteConfig) {
        if (config.version < 1) throw ConfigException("Versión de configuración inválida: ${config.version}")
        if (config.sites.isEmpty()) throw ConfigException("La configuración no tiene sitios")
        val ids = mutableSetOf<String>()
        config.sites.forEach { site ->
            if (!ID_REGEX.matches(site.id)) throw ConfigException("Id de sitio inválido: '${site.id}'")
            if (!ids.add(site.id)) throw ConfigException("Sitio repetido: '${site.id}'")
            if (!isHttpUrl(site.baseUrl)) throw ConfigException("Dominio inválido en '${site.id}': ${site.baseUrl}")
        }
    }

    fun isHttpUrl(value: String): Boolean = try {
        val uri = URI(value.trim())
        (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }

    /** Quita la barra final para poder concatenar rutas. */
    fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')
}
