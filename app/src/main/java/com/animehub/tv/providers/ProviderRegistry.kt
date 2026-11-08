package com.animehub.tv.providers

import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.providers.dooplay.DooPlayProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Crea el proveedor de cada sitio según su "engine" en la configuración remota. */
@Singleton
class ProviderRegistry @Inject constructor(
    private val configRepository: ConfigRepository,
    private val fetcher: HtmlFetcher,
) {
    fun create(site: EffectiveSite): SiteProvider? = when (site.config.engine) {
        ENGINE_DOOPLAY -> DooPlayProvider(site, fetcher)
        else -> null
    }

    /** Proveedores de los sitios activos (los sitios sin proveedor todavía se omiten). */
    val activeProviders: Flow<List<SiteProvider>> =
        configRepository.sites.map { sites -> sites.filter { it.enabled }.mapNotNull(::create) }

    /** Proveedor de un sitio aunque esté desactivado (para abrir favoritos o el historial). */
    suspend fun provider(siteId: String): SiteProvider? {
        configRepository.loadLocal()
        return configRepository.sites.first().firstOrNull { it.id == siteId }?.let(::create)
    }

    companion object {
        const val ENGINE_DOOPLAY = "dooplay"
    }
}
