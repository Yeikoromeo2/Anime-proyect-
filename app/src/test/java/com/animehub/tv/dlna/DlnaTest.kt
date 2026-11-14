package com.animehub.tv.dlna

import com.animehub.tv.remote.HttpResponse
import com.animehub.tv.remote.MiniHttpServer
import com.animehub.tv.remote.RemoteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** DLNA (Samsung, LG…): respuesta SSDP, descripción del dispositivo y mensajes SOAP con una TV simulada. */
class DlnaTest {

    @Test
    fun `ssdp - la direccion de la descripcion`() {
        val response = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.168.1.30:9197/dmr\r\n" +
            "ST: urn:schemas-upnp-org:service:AVTransport:1\r\nSERVER: Samsung-Linux/4.1, UPnP/1.0\r\n\r\n"
        assertEquals("http://192.168.1.30:9197/dmr", Upnp.locationOf(response))
        assertTrue("M-SEARCH" in Upnp.searchMessage() && "AVTransport" in Upnp.searchMessage())
    }

    @Test
    fun `descripcion - nombre y direccion de control absoluta`() {
        val xml = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType><friendlyName>[TV] Samsung Q60</friendlyName>
            <UDN>uuid:abc-123</UDN><serviceList>
            <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/upnp/control/RenderingControl1</controlURL></service>
            <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/upnp/control/AVTransport1</controlURL></service>
            </serviceList></device></root>"""
        val device = Upnp.parseDescription(xml, "http://192.168.1.30:9197/dmr")!!
        assertEquals("[TV] Samsung Q60", device.name)
        assertEquals("uuid:abc-123", device.id)
        assertEquals("http://192.168.1.30:9197/upnp/control/AVTransport1", device.controlUrl)
    }

    @Test
    fun `tiempos de DLNA`() {
        assertEquals(90_000L, Upnp.parseTime("0:01:30"))
        assertEquals(5_025_000L, Upnp.parseTime("01:23:45.000"))
        assertEquals(0L, Upnp.parseTime("NOT_IMPLEMENTED"))
        assertEquals("0:01:40", Upnp.formatTime(100_000))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actions = mutableListOf<Pair<String, String>>()
    private var transportState = "PLAYING"
    private val tv = MiniHttpServer({ request ->
        val action = request.header("soapaction").orEmpty().substringAfter('#').trim('"')
        actions += action to request.bodyText
        val body = when (action) {
            "GetTransportInfo" -> "<CurrentTransportState>$transportState</CurrentTransportState>"
            "GetPositionInfo" -> "<Track>1</Track><TrackDuration>0:24:00</TrackDuration><RelTime>0:01:30</RelTime>"
            else -> ""
        }
        HttpResponse(200, mapOf("Content-Type" to "text/xml"), """<s:Envelope><s:Body><u:${action}Response>$body</u:${action}Response></s:Body></s:Envelope>""".toByteArray())
    })

    @Before
    fun setUp() = tv.start(scope)

    @After
    fun tearDown() {
        tv.stop()
        scope.cancel()
    }

    @Test
    fun `soap - cargar, pausar, saltar y leer el minuto`() = runBlocking {
        val renderer = DlnaRenderer("http://127.0.0.1:${tv.port}/upnp/control/AVTransport1")
        val url = "http://192.168.1.10:41000/p/abc/v.mp4?u=https%3A%2F%2Fa4.mp4upload.com%2Fvideo.mp4&x=1"
        assertEquals(RemoteResult.Ok, renderer.load(url, "video/mp4", "Tank Chair", "Episodio 6", null, 0, false))
        assertEquals(listOf("Stop", "SetAVTransportURI", "Play"), actions.map { it.first })
        val set = actions[1].second
        // La dirección y los metadatos van escapados dentro del XML.
        assertTrue(set, "<CurrentURI>http://192.168.1.10:41000/p/abc/v.mp4?u=https%3A%2F%2Fa4.mp4upload.com%2Fvideo.mp4&amp;x=1</CurrentURI>" in set)
        assertTrue(set, "&lt;DIDL-Lite" in set && "Tank Chair · Episodio 6" in set && "http-get:*:video/mp4:" in set)

        actions.clear()
        assertTrue(renderer.toggle()) // reproduciendo → pausa
        assertEquals(listOf("GetTransportInfo", "Pause"), actions.map { it.first })
        actions.clear()
        assertTrue(renderer.seekBy(10))
        assertTrue(actions.last().second, "<Target>0:01:40</Target>" in actions.last().second && "REL_TIME" in actions.last().second)

        val status = renderer.status()!!
        assertEquals("playing", status.state)
        assertEquals(90_000L, status.positionMs)
        assertEquals(1_440_000L, status.durationMs)
    }
}
