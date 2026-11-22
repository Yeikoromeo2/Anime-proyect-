package com.animehub.tv.fixtures

import com.animehub.tv.data.config.ConfigParser
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.settings.AppSettings
import java.io.File

/** Sitios de la configuración publicada (config/sites.json), opcionalmente con otro dominio. */
object TestSites {
    val config by lazy { ConfigParser.parse(File(System.getProperty("animehub.configDir"), "sites.json").readText()) }

    fun site(id: String, baseUrl: String? = null): EffectiveSite {
        val settings = AppSettings(domainOverrides = baseUrl?.let { mapOf(id to it) } ?: emptyMap())
        return ConfigRepository.applySettings(config, settings).first { it.id == id }
    }
}
