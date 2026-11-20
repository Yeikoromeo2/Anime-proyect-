package com.animehub.tv.providers

import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.data.net.Http
import com.animehub.tv.data.net.UserAgentInterceptor
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.latanime.LatanimeProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Diagnóstico en vivo de Latanime: abre la ficha desde cada sección (solo con -Psalud). */
class LatanimeVivoTest {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor { Http.DEFAULT_USER_AGENT })
        .build()

    @Test
    fun fichasDesdeCadaSeccion() = runBlocking {
        assumeTrue(System.getProperty("animehub.salud") == "true")
        val p = LatanimeProvider(TestSites.site("latanime"), HtmlFetcher(client))
        val urls = linkedMapOf<String, String>()
        p.latestEpisodes().take(8).forEach { e -> urls["novedad: ${e.animeTitle}"] = e.animeUrl ?: "SIN URL de anime (${e.episodeUrl})" }
        p.showcase().take(10).forEach { s -> urls["${s.kind}: ${s.card.title}"] = s.card.url }
        p.search("", FilterSelection.NONE, 1).items.take(6).forEach { c -> urls["catálogo: ${c.title}"] = c.url }
        for ((label, url) in urls) {
            val start = System.currentTimeMillis()
            val result = runCatching { p.animeDetail(url) }
            val ms = System.currentTimeMillis() - start
            result.fold(
                onSuccess = { d -> println("OK   ${ms}ms  $label → ${d.episodes.size} eps, img=${d.episodes.firstOrNull()?.imageUrl?.take(60)}  $url") },
                onFailure = { e -> println("FALLA ${ms}ms $label → ${e::class.simpleName}: ${e.message}  $url") },
            )
        }
        val ep = p.latestEpisodes().first()
        val t = System.currentTimeMillis()
        val servers = p.servers(ep.episodeUrl)
        println("servidores ${System.currentTimeMillis() - t}ms ${ep.episodeUrl}: " + servers.joinToString { "${it.name}=${it.embedUrl.take(50)}" })
    }
}
