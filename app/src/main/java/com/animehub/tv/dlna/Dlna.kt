package com.animehub.tv.dlna

import android.content.Context
import android.net.wifi.WifiManager
import com.animehub.tv.remote.DeviceDiscovery
import com.animehub.tv.remote.DlnaDevice
import com.animehub.tv.remote.MediaRenderer
import com.animehub.tv.remote.PlayTarget
import com.animehub.tv.remote.RemoteResult
import com.animehub.tv.remote.RendererStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DLNA / UPnP: así reciben video de la red las TVs Samsung, LG y muchas otras. La tablet busca
 * los "renderizadores" con SSDP, lee su descripción y los maneja con mensajes SOAP (AVTransport).
 */
object Upnp {
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val SSDP_HOST = "239.255.255.250"
    private const val SSDP_PORT = 1900

    fun searchMessage(): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_HOST:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $AV_TRANSPORT\r\n\r\n"

    /** Dirección de la descripción del dispositivo en una respuesta SSDP. */
    fun locationOf(response: String): String? = response.lineSequence()
        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http") }

    /** Envía la búsqueda a la red y junta las direcciones de las descripciones que respondan. */
    fun search(timeoutMs: Int = 3_000): Set<String> {
        val found = mutableSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 500
            val bytes = searchMessage().toByteArray()
            val group = InetAddress.getByName(SSDP_HOST)
            repeat(2) { socket.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT)) }
            val end = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    locationOf(String(packet.data, 0, packet.length))?.let(found::add)
                } catch (e: SocketTimeoutException) {
                    // sigue esperando hasta el final
                }
            }
        }
        return found
    }

    /** Lee la descripción: nombre y dirección de control de AVTransport (absoluta). */
    fun parseDescription(xml: String, location: String): DlnaDevice? {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val name = doc.selectFirst("device > friendlyName")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val service = doc.select("service").firstOrNull { it.selectFirst("serviceType")?.text()?.contains(":AVTransport:") == true }
            ?: return null
        val control = service.selectFirst("controlURL")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val base = doc.selectFirst("URLBase")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: location
        val controlUrl = runCatching { URI(base).resolve(control).toString() }.getOrNull() ?: return null
        val id = doc.selectFirst("device > UDN")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: location
        return DlnaDevice(id, name, controlUrl)
    }

    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Metadatos DIDL-Lite del video (título y formato) que las TVs piden junto con la dirección. */
    fun didl(title: String, url: String, mime: String): String =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="0" parentID="-1" restricted="1"><dc:title>${escape(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class>""" +
            """<res protocolInfo="http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000">${escape(url)}</res></item></DIDL-Lite>"""

    fun soap(action: String, args: List<Pair<String, String>>): String =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="$AV_TRANSPORT">""" +
            args.joinToString("") { (k, v) -> "<$k>${escape(v)}</$k>" } +
            """</u:$action></s:Body></s:Envelope>"""

    /** Valor de un campo de la respuesta SOAP (p. ej. RelTime, CurrentTransportState). */
    fun field(xml: String, name: String): String? =
        Regex("<(?:[A-Za-z0-9]+:)?$name>([^<]*)</(?:[A-Za-z0-9]+:)?$name>").find(xml)?.groupValues?.get(1)?.trim()

    /** "0:12:34" o "00:12:34.000" → milisegundos. */
    fun parseTime(value: String?): Long {
        val parts = value?.substringBefore('.')?.split(':')?.mapNotNull { it.toLongOrNull() } ?: return 0
        if (parts.size != 3) return 0
        return ((parts[0] * 3600) + (parts[1] * 60) + parts[2]) * 1000
    }

    fun formatTime(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60)
    }
}

/** Maneja una TV DLNA con AVTransport: cargar, reproducir/pausar, saltar, detener y leer el minuto. */
class DlnaRenderer(
    private val controlUrl: String,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
) : MediaRenderer {

    private suspend fun call(action: String, vararg args: Pair<String, String>): String? = withContext(Dispatchers.IO) {
        val body = Upnp.soap(action, listOf("InstanceID" to "0") + args)
        val request = Request.Builder().url(controlUrl)
            .header("SOAPACTION", "\"${Upnp.AV_TRANSPORT}#$action\"")
            .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        runCatching { http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body.string() else null } }.getOrNull()
    }

    override suspend fun load(
        url: String, mime: String, title: String, subtitle: String, imageUrl: String?, startMs: Long, fragmentedMp4: Boolean,
    ): RemoteResult {
        // Las TVs suelen esperar el tipo HLS como "application/vnd.apple.mpegurl".
        val dlnaMime = if (mime.contains("mpegurl", ignoreCase = true)) "application/vnd.apple.mpegurl" else mime
        call("Stop")
        call("SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to Upnp.didl("$title · $subtitle", url, dlnaMime))
            ?: return RemoteResult.Failed("La TV no aceptó el video (quizá no reproduce este formato)")
        call("Play", "Speed" to "1") ?: return RemoteResult.Failed("La TV no empezó a reproducir")
        if (startMs > 0) {
            delay(1_500)
            call("Seek", "Unit" to "REL_TIME", "Target" to Upnp.formatTime(startMs))
        }
        return RemoteResult.Ok
    }

    override suspend fun toggle(): Boolean {
        val state = call("GetTransportInfo")?.let { Upnp.field(it, "CurrentTransportState") }
        return if (state == "PLAYING") call("Pause") != null else call("Play", "Speed" to "1") != null
    }

    override suspend fun seekBy(seconds: Int): Boolean {
        val position = call("GetPositionInfo")?.let { Upnp.parseTime(Upnp.field(it, "RelTime")) } ?: return false
        return call("Seek", "Unit" to "REL_TIME", "Target" to Upnp.formatTime(position + seconds * 1000L)) != null
    }

    override suspend fun stop() {
        call("Stop")
    }

    override suspend fun status(): RendererStatus? {
        val transport = call("GetTransportInfo") ?: return null
        val info = call("GetPositionInfo")
        val state = when (Upnp.field(transport, "CurrentTransportState")) {
            "PLAYING" -> "playing"
            "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> "paused"
            "TRANSITIONING" -> "loading"
            else -> "idle"
        }
        return RendererStatus(state, Upnp.parseTime(info?.let { Upnp.field(it, "RelTime") }), Upnp.parseTime(info?.let { Upnp.field(it, "TrackDuration") }))
    }
}

/** Busca TVs DLNA en la red cada 30 s mientras la app está abierta (desde la tablet). */
@Singleton
class DlnaDiscovery @Inject constructor(@ApplicationContext private val context: Context) : DeviceDiscovery {
    private val _devices = MutableStateFlow<List<PlayTarget>>(emptyList())
    override val devices: StateFlow<List<PlayTarget>> = _devices.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val http = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                _devices.value = discover()
                delay(30_000)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private fun discover(): List<PlayTarget> {
        // Algunas redes Wi-Fi descartan la multidifusión si la app no la pide.
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = runCatching { wifi?.createMulticastLock("animehub-dlna")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        return try {
            runCatching { Upnp.search() }.getOrDefault(emptySet()).mapNotNull { location ->
                runCatching {
                    http.newCall(Request.Builder().url(location).build()).execute().use { r ->
                        if (r.isSuccessful) Upnp.parseDescription(r.body.string(), location) else null
                    }
                }.getOrNull()
            }.distinctBy { it.id }
        } finally {
            runCatching { lock?.release() }
        }
    }
}
