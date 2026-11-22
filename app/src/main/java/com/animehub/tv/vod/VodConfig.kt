package com.animehub.tv.vod

import com.animehub.tv.BuildConfig
import com.animehub.tv.data.iptv.IptvAccount
import com.animehub.tv.data.iptv.IptvRepository
import com.animehub.tv.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuración del módulo VOD. Se lee de Ajustes y, si falta, de `local.properties` (solo compilación debug):
 * `IPTV_HOST/USER/PASS`, `COMMUNITY_CATALOG_URL` y `COMMUNITY_STREAM_ADDONS` (direcciones base de addons con
 * protocolo Stremio, separadas por coma). Nada de esto va en el repositorio.
 */
data class VodConfig(
    val communityCatalogUrl: String,
    val communityAddons: List<String>,
    val xtream: IptvAccount,
) {
    val communityReady get() = communityCatalogUrl.isNotBlank()
    val xtreamReady get() = xtream.ready

    companion object {
        const val DEFAULT_COMMUNITY_CATALOG = "https://v3-cinemeta.strem.io"

        /** "a, b\nc" → ["a", "b", "c"] sin barra final ni `/manifest.json`. */
        fun parseAddons(raw: String): List<String> =
            raw.split(',', '\n', ';', ' ').map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }
                .map { it.removeSuffix("/manifest.json").trimEnd('/') }.distinct()
    }
}

/** De dónde sale la configuración (Ajustes en la app; un valor fijo en las pruebas). */
fun interface VodConfigProvider {
    suspend fun current(): VodConfig
}

@Singleton
class SettingsVodConfigProvider @Inject constructor(private val settings: SettingsRepository, private val iptv: IptvRepository) : VodConfigProvider {
    override suspend fun current(): VodConfig {
        val s = settings.current()
        return VodConfig(
            communityCatalogUrl = s.communityCatalogUrl.ifBlank { BuildConfig.COMMUNITY_CATALOG_URL }.trim().trimEnd('/'),
            communityAddons = VodConfig.parseAddons(s.communityAddons.ifBlank { BuildConfig.COMMUNITY_STREAM_ADDONS }),
            xtream = iptv.account(),
        )
    }
}
