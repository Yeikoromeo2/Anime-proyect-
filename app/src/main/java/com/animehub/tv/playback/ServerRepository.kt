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
import com.animehub.tv.extractors.DirectStreamExtractor
import com.animehub.tv.extractors.ServerChecker
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.util.UserMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    /**
     * Todo en segundo plano: son muchas peticiones en paralelo y sus resultados no deben esperar
     * turno en el hilo principal (que también dibuja la pantalla).
     */
    suspend fun serversFor(provider: SiteProvider, episodeUrl: String): List<ServerOption> = withContext(Dispatchers.IO) {
        val config = configRepository.loadLocal().config
        val raw = provider.servers(episodeUrl)
        val expanded = expand(raw, config, referer = provider.baseUrl + "/")
            .distinctBy { it.embedUrl }
        val preferred = settings.current().preferredServers[provider.id]
        val checker = ServerChecker(client, config.deadVideoMarkers)
        val referer = provider.baseUrl + "/"
        val extractor = DirectStreamExtractor(config.directStreams) { url, ref -> fetcher.get("servidor", url, referer = ref) }
        val options = coroutineScope {
            expanded.map { server ->
                async {
                    // A la vez: ¿responde? y ¿se puede sacar el video directo para el reproductor nativo?
                    val check = async { withTimeoutOrNull(CHECK_TIMEOUT_MS) { checker.check(server.embedUrl, referer) } }
                    val direct = async { withTimeoutOrNull(EXTRACT_TIMEOUT_MS) { extractor.extract(server.embedUrl, referer) } }
                    val result = check.await()
                    val stream = direct.await()
                    ServerOption(
                        server = server,
                        // Si el video directo salió, el servidor funciona aunque su página no se pudiera comprobar.
                        health = if (stream != null) ServerHealth.FUNCIONA else result?.health ?: ServerHealth.SIN_VERIFICAR,
                        preferred = preferred != null && sameServer(server.name, preferred),
                        stream = stream,
                        detail = result?.detail ?: "Tardó demasiado",
                    )
                }
            }.awaitAll()
        }
        sort(options)
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
        const val EXTRACT_TIMEOUT_MS = 8_000L

        /**
         * Preferido primero, luego los que funcionan (los de reproductor nativo antes que los web),
         * sin verificar y al final los no disponibles.
         */
        fun sort(options: List<ServerOption>): List<ServerOption> = options.sortedWith(
            compareBy<ServerOption>(
                { it.health == ServerHealth.NO_DISPONIBLE },
                { !it.preferred },
                { it.health != ServerHealth.FUNCIONA },
                { it.stream == null },
            ),
        )

        fun sameServer(a: String, b: String) = Canonical.of(a) == Canonical.of(b)

        private fun host(url: String) = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()
    }
}
