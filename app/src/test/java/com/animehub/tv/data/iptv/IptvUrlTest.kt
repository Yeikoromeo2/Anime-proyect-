package com.animehub.tv.data.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvUrlTest {
    private val account = IptvAccount("http://servidor.test", "usuario", "clave", "ts")

    @Test
    fun `arma el enlace de la lista M3U simple`() {
        assertEquals(
            "http://servidor.test/get.php?username=usuario&password=clave&type=m3u&output=ts",
            IptvUrl.list(account).toString(),
        )
        assertEquals("m3u8", IptvUrl.list(account, "m3u8")!!.queryParameter("output"))
    }

    @Test
    fun `el servidor sin esquema usa http y se puede forzar https`() {
        val bare = account.copy(host = "servidor.test/")
        assertEquals("http://servidor.test/get.php?username=usuario&password=clave&type=m3u&output=ts", IptvUrl.list(bare).toString())
        assertEquals("https", IptvUrl.list(bare, scheme = "https")!!.scheme)
        assertNull(IptvUrl.list(account.copy(host = "")))
    }

    @Test
    fun `los caracteres raros de la contrasena se codifican`() {
        val url = IptvUrl.list(account.copy(pass = "a b&c=d"))!!
        assertEquals("a b&c=d", url.queryParameter("password"))
        assertFalse(url.toString().contains("a b"))
    }

    @Test
    fun `el formato alternativo es el otro y una senal cambia de ts a m3u8`() {
        assertEquals("m3u8", IptvUrl.alternate("ts"))
        assertEquals("ts", IptvUrl.alternate("m3u8"))
        assertEquals("http://s/live/u/p/1.m3u8", IptvUrl.swapStream("http://s/live/u/p/1.ts"))
        assertEquals("http://s/live/u/p/1.ts?x=1", IptvUrl.swapStream("http://s/live/u/p/1.m3u8?x=1"))
        assertNull(IptvUrl.swapStream("http://s/video.mp4"))
    }

    @Test
    fun `la cuenta esta lista solo con servidor usuario y contrasena`() {
        assertTrue(account.ready)
        assertFalse(account.copy(user = "").ready)
        assertFalse(account.copy(host = " ").ready)
    }
}
