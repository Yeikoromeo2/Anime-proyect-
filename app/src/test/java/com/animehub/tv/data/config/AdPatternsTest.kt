package com.animehub.tv.data.config

import com.animehub.tv.fixtures.TestSites
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los patrones de anuncios publicados compilan, frenan anuncios típicos y no tocan el video. */
class AdPatternsTest {
    private val patterns by lazy { TestSites.config.adBlockPatterns.map { Regex(it) } }
    private fun blocked(url: String) = patterns.any { it.containsMatchIn(url) }

    @Test
    fun `frenan anuncios y ventanas emergentes tipicas`() {
        assertTrue(blocked("https://cdn.example.net/ads/banner300.js"))
        assertTrue(blocked("https://x.example.com/popunder.js"))
        assertTrue(blocked("https://x.example.com/pop.php?zone=5"))
        assertTrue(blocked("https://x.example.com/serve?adzone=12&popunder=1"))
        assertTrue(blocked("https://x.example.com/pagead/show"))
    }

    @Test
    fun `no tocan el video ni el reproductor`() {
        assertFalse(blocked("https://cdn.example.com/hls/master.m3u8"))
        assertFalse(blocked("https://cdn.example.com/videos/pelicula/seg-12.ts"))
        assertFalse(blocked("https://player.example.com/embed/abc123"))
        assertFalse(blocked("https://cdn.example.com/v/video.mp4?token=abc"))
        assertFalse(blocked("https://cdn.example.com/assets/playlist-add-icon.svg"))
    }

    @Test
    fun `la lista de dominios incluye las redes de anuncios mas comunes`() {
        val hosts = TestSites.config.adBlockHosts
        assertTrue(hosts.containsAll(listOf("doubleclick.net", "exoclick.com", "propellerads.com", "adsterra.com", "monetag.com")))
    }
}
