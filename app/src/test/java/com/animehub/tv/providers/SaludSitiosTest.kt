package com.animehub.tv.providers

import com.animehub.tv.data.net.Http
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.data.net.UserAgentInterceptor
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.dooplay.DooPlayProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * "Salud de sitios": comprueba EN VIVO que cada sitio sigue funcionando (búsqueda de "one piece").
 * No corre en los tests normales; se lanza con:
 *   ./gradlew testDebugUnitTest -Psalud --tests '*SaludSitiosTest*'
 */
class SaludSitiosTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor { Http.DEFAULT_USER_AGENT })
        .build()

    @Test
    fun animeNinjaResponde() = runBlocking {
        assumeTrue("Solo con -Psalud", System.getProperty("animehub.salud") == "true")
        val provider = DooPlayProvider(TestSites.site("animeninja"), HtmlFetcher(client))
        val results = provider.search("one piece", FilterSelection.NONE, 1).items
        println("Anime Ninja · búsqueda: ${results.size} resultados → ${results.take(3).map { it.title }}")
        assertTrue("La búsqueda no devolvió resultados", results.isNotEmpty())
        assertTrue("Faltan portadas", results.all { it.coverUrl != null })
        val latest = provider.latestEpisodes()
        println("Anime Ninja · novedades: ${latest.size} → ${latest.take(3).map { "${it.animeTitle} ${it.episodeNumber}" }}")
        assertTrue("No hay novedades", latest.isNotEmpty())
        val detail = provider.animeDetail(results.first().url)
        println("Anime Ninja · ficha: ${detail.title}, ${detail.episodes.size} episodios")
        assertTrue("La ficha no tiene episodios", detail.episodes.isNotEmpty())
        val servers = provider.servers(detail.episodes.first().url)
        println("Anime Ninja · servidores del primer episodio: ${servers.map { it.name + " → " + it.embedUrl }}")
        assertTrue("No se detectaron servidores", servers.isNotEmpty())
    }
}
