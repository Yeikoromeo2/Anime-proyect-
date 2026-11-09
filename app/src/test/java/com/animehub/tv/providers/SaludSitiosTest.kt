package com.animehub.tv.providers

import com.animehub.tv.data.net.Http
import com.animehub.tv.data.net.HtmlFetcher
import com.animehub.tv.data.net.UserAgentInterceptor
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.dooplay.DooPlayProvider
import com.animehub.tv.providers.latanime.LatanimeProvider
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
        checkShowcase("Anime Ninja", provider.showcase())
        val detail = provider.animeDetail(results.first().url)
        println("Anime Ninja · ficha: ${detail.title}, ${detail.episodes.size} episodios")
        assertTrue("La ficha no tiene episodios", detail.episodes.isNotEmpty())
        val servers = provider.servers(detail.episodes.first().url)
        println("Anime Ninja · servidores del primer episodio: ${servers.map { it.name + " → " + it.embedUrl }}")
        assertTrue("No se detectaron servidores", servers.isNotEmpty())
        // El "MULTISERVER" se abre en sus servidores reales (darkanimes / animeav1).
        val fetcher = HtmlFetcher(client)
        val real = servers.flatMap { s ->
            val html = fetcher.get("agregador", s.embedUrl, referer = "https://veranime.ninja/")
            when {
                "darkanimes" in s.embedUrl -> com.animehub.tv.extractors.AggregatorParsers.mspXor(html)
                "animeav1" in s.embedUrl -> com.animehub.tv.extractors.AggregatorParsers.embedsObject(html)
                else -> listOf(s)
            }
        }
        println("Anime Ninja · servidores reales: ${real.map { "${it.name} (${it.language}) → ${it.embedUrl}" }}")
        assertTrue("El multiservidor no se pudo abrir", real.size > servers.size)
        // Y de un episodio reciente (suele venir de animeav1).
        val recent = provider.latestEpisodes().first()
        val recentServers = provider.servers(recent.episodeUrl)
        println("Anime Ninja · ${recent.animeTitle} ${recent.episodeNumber}: ${recentServers.map { it.name + " → " + it.embedUrl }}")
    }

    @Test
    fun latanimeResponde() = runBlocking {
        assumeTrue("Solo con -Psalud", System.getProperty("animehub.salud") == "true")
        val provider = LatanimeProvider(TestSites.site("latanime"), HtmlFetcher(client))
        val latest = provider.latestEpisodes()
        println("Latanime · novedades: ${latest.size} → ${latest.take(3).map { "${it.animeTitle} ${it.episodeNumber}" }}")
        assertTrue("No hay novedades", latest.isNotEmpty())
        checkShowcase("Latanime", provider.showcase())
        // La búsqueda por texto pide verificación de Cloudflare: se comprueba el directorio con filtros.
        val year = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1).toString()
        val filtered = provider.search("", FilterSelection(mapOf("genre" to setOf("accion"), "year" to setOf(year))), 1).items
        println("Latanime · acción + $year: ${filtered.size} → ${filtered.take(3).map { "${it.title} (${it.year})" }}")
        assertTrue("El filtro no devolvió resultados", filtered.isNotEmpty())
        assertTrue("Hay resultados de otro año", filtered.all { it.year.toString() == year })
        val detail = provider.animeDetail(filtered.first().url)
        println("Latanime · ficha: ${detail.title}, ${detail.episodes.size} episodios, ${detail.status}")
        assertTrue(detail.episodes.isNotEmpty())
        val servers = provider.servers(detail.episodes.first().url)
        println("Latanime · servidores: ${servers.map { it.name }}")
        assertTrue(servers.isNotEmpty())
    }

    @Test
    fun jkanimeResponde() = runBlocking {
        assumeTrue("Solo con -Psalud", System.getProperty("animehub.salud") == "true")
        val provider = com.animehub.tv.providers.jkanime.JkanimeProvider(TestSites.site("jkanime"), HtmlFetcher(client))
        val results = provider.search("one piece", FilterSelection.NONE, 1).items
        println("JKAnime · búsqueda: ${results.size} resultados → ${results.take(3).map { it.title }}")
        assertTrue("La búsqueda no devolvió resultados", results.isNotEmpty())
        val latest = provider.latestEpisodes()
        println("JKAnime · novedades: ${latest.size} → ${latest.take(3).map { "${it.animeTitle} ${it.episodeNumber}" }}")
        assertTrue("No hay novedades", latest.isNotEmpty())
        checkShowcase("JKAnime", provider.showcase())
        val detail = provider.animeDetail(latest.first().animeUrl!!)
        println("JKAnime · ficha: ${detail.title}, ${detail.episodes.size} episodios, géneros ${detail.genres}")
        assertTrue("La ficha no tiene episodios", detail.episodes.isNotEmpty())
        val servers = provider.servers(detail.episodes.last().url)
        println("JKAnime · servidores: ${servers.map { "${it.name} (${it.language})" }}")
        assertTrue("Sin servidores", servers.isNotEmpty())
        val filters = provider.availableFilters()
        println("JKAnime · filtros: ${filters.map { "${it.label} (${it.options.size})" }}")
        val genre = filters.first { it.id == "genre" }.options.first { it.value == "accion" }
        val year = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1).toString()
        val page = provider.search("", FilterSelection(mapOf("genre" to setOf(genre.value), "year" to setOf(year))), 1)
        println("JKAnime · acción + $year: ${page.items.size} → ${page.items.take(3).map { it.title }}, más páginas: ${page.hasNextPage}")
        assertTrue("El directorio con filtros vino vacío", page.items.isNotEmpty())
    }

    /** Recomendados y series nuevas de la portada (lo que muestra Inicio). */
    private fun checkShowcase(site: String, items: List<com.animehub.tv.domain.model.ShowcaseItem>) {
        val byKind = items.groupBy { it.kind }
        byKind.forEach { (kind, list) -> println("$site · $kind: ${list.size} → ${list.take(3).map { it.card.title }}") }
        assertTrue("$site: faltan recomendados", byKind[com.animehub.tv.domain.model.ShowcaseKind.RECOMENDADO].orEmpty().isNotEmpty())
        assertTrue("$site: faltan series nuevas", byKind[com.animehub.tv.domain.model.ShowcaseKind.NUEVO].orEmpty().isNotEmpty())
        assertTrue("$site: faltan portadas", items.all { it.card.coverUrl != null })
    }
}
