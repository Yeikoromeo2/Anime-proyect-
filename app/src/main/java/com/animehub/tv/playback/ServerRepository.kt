package com.animehub.tv.playback

import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.RemoteConfig
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.domain.model.Canonical
import com.animehub.tv.domain.model.ServerHealth
import com.animehub.tv.domain.model.ServerOption
import com.animehub.tv.domain.model.VideoServer
import com.animehub.tv.extractors.AggregatorParsers
import com.animehub.tv.extractors.ServerChecker
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.util.UserMessages
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servidores de un episodio listos para reproducir: abre los "multiservidor", comprueba en
 * paralelo cuáles responden y los ordena (preferido y los que funcionan primero).
 */
@Singleton
class ServerRepository @Inject constructor(
    private val fetcher: HtmlFetcher,
    private val client: OkHttpClient,
    private val configRepository: ConfigRepository,
    private val settings: SettingsRepository,
    private val errorLog: ErrorLogRepository,
) {
    suspend fun serversFor(provider: SiteProvider, episodeUrl: String): List<ServerOption> {
        val config = configRepository.loadLocal().config
        val raw = provider.servers(episodeUrl)
        val expanded = expand(raw, config, referer = provider.baseUrl + "/")
            .distinctBy { it.embedUrl }
        val preferred = settings.current().preferredServers[provider.id]
        val checker = ServerChecker(client, config.deadVideoMarkers)
        val options = coroutineScope {
            expanded.map { server ->
                async {
                    val result = withTimeoutOrNull(CHECK_TIMEOUT_MS) { checker.check(server.embedUrl, provider.baseUrl + "/") }
                    ServerOption(
                        server = server,
                        health = result?.health ?: ServerHealth.SIN_VERIFICAR,
                        preferred = preferred != null && sameServer(server.name, preferred),
                        detail = result?.detail ?: "Tardó demasiado",
                    )
                }
            }.awaitAll()
        }
        return sort(options)
    }

    suspend fun rememberPreferred(siteId: String, serverName: String) = settings.setPreferredServer(siteId, serverName)

    /** Abre los multiservidor configurados; si uno falla se conserva tal cual (se puede reproducir su página). */
    private suspend fun expand(servers: List<VideoServer>, config: RemoteConfig, referer: String): List<VideoServer> =
        coroutineScope {
            servers.map { server ->
                async {
                    val aggregator = config.aggregators.firstOrNull { agg -> agg.hosts.any { host(server.embedUrl)?.endsWith(it) == true } }
                        ?: return@async listOf(server)
                    runCatching {
                        val html = fetcher.get("agregador", server.embedUrl, referer = referer, cacheMs = 5 * 60_000L)
                        when (aggregator.type) {
                            "msp-xor" -> AggregatorParsers.mspXor(html)
                            "embeds-object" -> AggregatorParsers.embedsObject(html)
                            else -> emptyList()
                        }
                    }.onFailure { errorLog.log("Servidores", "${server.name}: ${UserMessages.forError(server.name, it)}") }
                        .getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(server)
                }
            }.awaitAll().flatten()
        }

    companion object {
        const val CHECK_TIMEOUT_MS = 10_000L

        /** Preferido primero, luego los que funcionan, sin verificar y al final los no disponibles. */
        fun sort(options: List<ServerOption>): List<ServerOption> = options.sortedWith(
            compareBy<ServerOption>(
                { it.health == ServerHealth.NO_DISPONIBLE },
                { !it.preferred },
                { it.health != ServerHealth.FUNCIONA },
            ),
        )

        fun sameServer(a: String, b: String) = Canonical.of(a) == Canonical.of(b)

        private fun host(url: String) = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()
    }
}
