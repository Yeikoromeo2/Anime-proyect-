package com.animehub.tv.data.config

import com.animehub.tv.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfigParserTest {

    private val publishedConfig: String
        get() = File(System.getProperty("animehub.configDir"), "sites.json").readText()

    @Test
    fun `la configuracion publicada es valida`() {
        val config = ConfigParser.parse(publishedConfig)
        assertTrue(config.version >= 1)
        assertEquals(listOf("latanime", "animeninja", "animeflv", "jkanime"), config.sites.map { it.id })
    }

    @Test
    fun `los sitios analizados tienen selectores y los pendientes no se activan`() {
        val config = ConfigParser.parse(publishedConfig)
        val latanime = config.sites.first { it.id == "latanime" }
        assertFalse(latanime.isPending)
        assertTrue(latanime.selectors.containsKey("card"))
        assertEquals(4, latanime.filters.size)
        val flv = config.sites.first { it.id == "animeflv" }
        assertTrue(flv.isPending)
    }

    @Test
    fun `ignora campos desconocidos para no romper versiones viejas`() {
        val config = ConfigParser.parse(
            """{"version":2,"campoNuevo":true,"sites":[{"id":"x","name":"X","baseUrl":"https://x.com","otro":1}]}""",
        )
        assertEquals("x", config.sites.single().id)
    }

    @Test(expected = ConfigException::class)
    fun `rechaza sitios repetidos`() {
        ConfigParser.parse(
            """{"version":1,"sites":[{"id":"a","name":"A","baseUrl":"https://a.com"},{"id":"a","name":"A","baseUrl":"https://a.com"}]}""",
        )
    }

    @Test(expected = ConfigException::class)
    fun `rechaza dominios invalidos`() {
        ConfigParser.parse("""{"version":1,"sites":[{"id":"a","name":"A","baseUrl":"latanime.org"}]}""")
    }

    @Test(expected = ConfigException::class)
    fun `rechaza JSON roto`() {
        ConfigParser.parse("{ esto no es json")
    }

    @Test
    fun `aplica dominio editado y activacion del usuario`() {
        val config = ConfigParser.parse(publishedConfig)
        val settings = AppSettings(
            siteEnabled = mapOf("latanime" to false, "animeflv" to true),
            domainOverrides = mapOf("animeninja" to "https://nuevo.ninja/", "latanime" to "no-es-url"),
        )
        val sites = ConfigRepository.applySettings(config, settings).associateBy { it.id }
        assertFalse(sites.getValue("latanime").enabled)
        assertEquals("https://latanime.org", sites.getValue("latanime").baseUrl)
        assertEquals("https://nuevo.ninja", sites.getValue("animeninja").baseUrl)
        assertTrue(sites.getValue("animeninja").domainEdited)
        // Un sitio pendiente de análisis no se puede activar.
        assertFalse(sites.getValue("animeflv").enabled)
    }
}
