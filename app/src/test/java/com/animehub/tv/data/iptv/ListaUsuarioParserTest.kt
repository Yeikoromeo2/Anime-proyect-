package com.animehub.tv.data.iptv

import com.animehub.tv.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La lista M3U simple del usuario: nombre, logo, categoría y dirección de cada canal. */
class ListaUsuarioParserTest {
    private val entries = M3uParser.parseEntries(Fixtures.read("iptv/lista_usuario.m3u"))

    @Test
    fun `lee nombre logo categoria y direccion`() {
        assertEquals(4, entries.size)
        val uno = entries.first()
        assertEquals("Canal Uno HD", uno.name)
        assertEquals("http://servidor.test/logos/uno.png", uno.logo)
        assertEquals("Noticias", uno.group)
        assertEquals("http://servidor.test/live/prueba/prueba/101.ts", uno.url)
        assertEquals(null, entries.last().group)
    }

    @Test
    fun `agrupa por categoria y deja los sin categoria en Otros`() {
        val list = M3uParser.buildList("tv", "TV en vivo", entries, kind = "live")
        val groups = list.groups.map { it.first }
        assertTrue(groups.containsAll(listOf("Noticias", "Deportes")))
        assertEquals(2, list.groups.first { it.first == "Noticias" }.second.size)
        assertTrue("Otros" in groups)
    }
}
