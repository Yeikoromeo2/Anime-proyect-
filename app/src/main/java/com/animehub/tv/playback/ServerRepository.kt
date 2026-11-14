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
import com.animehub.tv.extractors.StreamProbe
import com.animehub.tv.providers.SiteProvider
import com.animehub.tv.util.UserMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.animehub.tv.domain.model.DirectStream
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
     * Servidores de un episodio, en cuanto van respondiendo (no se espera a todos):
     * - se comprueban primero el preferido y luego los demás, de a [CONCURRENCY] a la vez (en la TV
     *   decenas de peticiones simultáneas gastan red y procesador);
     * - los que no responden o están caídos se descartan por completo: ni se ven ni se prueban;
     * - cada vez que uno responde se emite la lista ordenada; quien reproduce arranca con la primera.
     */
    fun serversFlow(provider: SiteProvider, episodeUrl: String): Flow<List<ServerOption>> = channelFlow {
        val config = configRepository.loadLocal().config
        val raw = provider.servers(episodeUrl)
        val referer = provider.baseUrl + "/"
        val preferred = settings.current().preferredServers[provider.id]?.let(::parsePreferred)
        val expanded = expand(raw, config, referer = referer).distinctBy { it.embedUrl }
        // El preferido (mismo servidor e idioma, o al menos el mismo idioma) se comprueba primero.
        val ordered = expanded.sortedBy { -preferenceScore(it, preferred) }
        val checker = ServerChecker(client, config.deadVideoMarkers)
        val probe = StreamProbe(client)
        val extractor = DirectStreamExtractor(config.directStreams, probe::reachable) { url, ref -> fetcher.get("servidor", url, referer = ref) }
        val gate = Semaphore(CONCURRENCY)
        // Con el mismo estado, manda el orden en que el sitio los lista (no el de quién responde antes).
        val kept = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, ServerOption>>())
        fun snapshot() = sort(kept.sortedBy { it.first }.map { it.second })
        val started = System.currentTimeMillis()
        // Los "favoritos" de arranque: el preferido o, si no hay, el primero de la lista. Se les da
        // unos segundos de ventaja antes de ofrecer otro.
        val leaders = ordered.filter { preferenceScore(it, preferred) > 0 }.ifEmpty { ordered.take(1) }.map { it.embedUrl }.toSet()
        val leadersPending = java.util.concurrent.atomic.AtomicInteger(leaders.size)
        coroutineScope {
            val jobs = ordered.mapIndexed { position, server ->
                async {
                    val isLeader = server.embedUrl in leaders
                    val option = gate.withPermit {
                        // A la vez: ¿responde? y ¿se puede sacar el video directo para el reproductor nativo?
                        val check = async { withTimeoutOrNull(CHECK_TIMEOUT_MS) { checker.check(server.embedUrl, referer) } }
                        val direct = async { withTimeoutOrNull(EXTRACT_TIMEOUT_MS) { extractor.extract(server.embedUrl, referer) } }
                        val result = check.await()
                        val stream = direct.await()
                        toOption(server, result?.health, stream, result?.detail, preferenceScore(server, preferred) > 1)
                    }
                    if (isLeader) leadersPending.decrementAndGet()
                    if (option != null) kept += position to option
                    // Se emite en cuanto hay algo; si el preferido aún se está comprobando se espera
                    // unos segundos a que responda para no quitarle el turno.
                    val waiting = leadersPending.get() > 0 && System.currentTimeMillis() - started < PREFERRED_GRACE_MS
                    if (kept.isNotEmpty() && !waiting) send(snapshot())
                }
            }
            // Pasado el plazo del preferido se ofrece lo que ya respondió, aunque nada más termine.
            val grace = launch {
                delay(PREFERRED_GRACE_MS)
                if (kept.isNotEmpty()) send(snapshot())
            }
            jobs.awaitAll()
            grace.cancel()
        }
        // Al final siempre queda la lista completa (también cuando no quedó ninguno).
        send(snapshot())
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    /** Todos los servidores ya comprobados (para quien no necesita ir mostrando avances). */
    suspend fun serversFor(provider: SiteProvider, episodeUrl: String): List<ServerOption> =
        serversFlow(provider, episodeUrl).lastOrNull().orEmpty()

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
        const val CHECK_TIMEOUT_MS = 6_000L
        const val EXTRACT_TIMEOUT_MS = 6_000L
        /** Comprobaciones simultáneas (la TV no aguanta decenas a la vez). */
        const val CONCURRENCY = 3
        /** Cuánto se espera al servidor preferido antes de ofrecer los demás. */
        const val PREFERRED_GRACE_MS = 3_000L

        /**
         * Qué hacer con un servidor ya comprobado. null = descartarlo: no respondió (se agotó el
         * tiempo sin video directo) o está caído. Sin verificar solo queda si responde pero no se pudo juzgar.
         */
        fun toOption(server: VideoServer, health: ServerHealth?, stream: DirectStream?, detail: String?, preferred: Boolean): ServerOption? {
            if (stream == null && (health == null || health == ServerHealth.NO_DISPONIBLE)) return null
            return ServerOption(
                server = server,
                // Si el video directo salió, el servidor funciona aunque su página no se pudiera comprobar.
                health = if (stream != null) ServerHealth.FUNCIONA else health ?: ServerHealth.SIN_VERIFICAR,
                preferred = preferred,
                stream = stream,
                detail = detail,
            )
        }

        /** La preferencia se guarda como "servidor" o "servidor\u001Fidioma". */
        data class Preferred(val name: String, val language: String?)

        fun parsePreferred(raw: String): Preferred =
            raw.substringBefore('\u001F').let { n -> Preferred(n, raw.substringAfter('\u001F', "").ifBlank { null }) }

        fun encodePreferred(name: String, language: String?) = if (language.isNullOrBlank()) name else "$name\u001F$language"

        /** 2 = mismo servidor e idioma; 1 = mismo idioma (o servidor, si no se guardó idioma); 0 = nada. */
        fun preferenceScore(server: VideoServer, preferred: Preferred?): Int {
            if (preferred == null) return 0
            val sameName = sameServer(server.name, preferred.name)
            val sameLang = preferred.language != null && server.language != null && Canonical.of(server.language) == Canonical.of(preferred.language)
            return when {
                preferred.language == null -> if (sameName) 2 else 0
                sameName && sameLang -> 2
                sameLang -> 1
                else -> 0
            }
        }

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
