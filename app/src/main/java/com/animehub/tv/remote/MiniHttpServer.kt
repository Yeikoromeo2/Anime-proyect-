package com.animehub.tv.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/** Petición HTTP recibida (cabeceras con el nombre en minúsculas). */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
    val remoteAddress: String? = null,
) {
    fun header(name: String): String? = headers[name.lowercase()]
    val bodyText: String get() = String(body, Charsets.UTF_8)
}

/**
 * Respuesta: un cuerpo fijo o un flujo (para reenviar video sin cargarlo entero en memoria).
 * @param stream escribe el cuerpo; [contentLength] debe ser su tamaño exacto, o null si no se sabe.
 */
class HttpResponse(
    val code: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentLength: Long? = body?.size?.toLong(),
    val stream: (suspend (OutputStream) -> Unit)? = null,
) {
    companion object {
        fun json(code: Int, json: String) =
            HttpResponse(code, mapOf("Content-Type" to "application/json; charset=utf-8"), json.toByteArray(Charsets.UTF_8))

        fun text(code: Int, text: String) =
            HttpResponse(code, mapOf("Content-Type" to "text/plain; charset=utf-8"), text.toByteArray(Charsets.UTF_8))
    }
}

/**
 * Servidor HTTP/1.1 mínimo para la red de casa: las órdenes de la tablet a la TV ("Enviar a mi TV")
 * y, más adelante, el reenvío de video para Chromecast/DLNA. Una conexión por petición
 * (`Connection: close`), cuerpos de hasta 64 KB y respuestas fijas o en flujo.
 * @param port 0 = cualquier puerto libre (se lee en [port] tras [start]).
 */
class MiniHttpServer(
    private val handler: suspend (HttpRequest) -> HttpResponse,
    private val requestedPort: Int = 0,
) {
    private var socket: ServerSocket? = null
    private var job: Job? = null

    val port: Int get() = socket?.localPort ?: -1
    val isRunning: Boolean get() = socket?.isClosed == false

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(requestedPort))
        }
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { serve(client) }
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
    }

    private suspend fun serve(client: Socket) {
        client.use { s ->
            s.soTimeout = READ_TIMEOUT_MS
            val response = try {
                val request = read(BufferedInputStream(s.getInputStream()), s.inetAddress?.hostAddress)
                    ?: return
                runCatching { handler(request) }.getOrElse { HttpResponse.text(500, "Error interno") }
                    .let { if (request.method == "HEAD") it.withoutBody() else it }
            } catch (e: RequestTooLarge) {
                HttpResponse.text(413, "Petición demasiado grande")
            } catch (e: Exception) {
                return
            }
            runCatching { write(s.getOutputStream(), response) }
        }
    }

    private fun HttpResponse.withoutBody() = HttpResponse(code, headers, null, contentLength, null)

    private class RequestTooLarge : Exception()

    private fun read(input: InputStream, remote: String?): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            if (headers.size > MAX_HEADERS) throw RequestTooLarge()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length > MAX_BODY) throw RequestTooLarge()
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { pair ->
            decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
        }
        return HttpRequest(parts[0].uppercase(), decode(path), query, headers, body, remote)
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
            if (buffer.size() > MAX_LINE) throw RequestTooLarge()
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun write(output: OutputStream, response: HttpResponse) {
        val head = StringBuilder("HTTP/1.1 ${response.code} ${reason(response.code)}\r\n")
        response.headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
        response.contentLength?.let { head.append("Content-Length: ").append(it).append("\r\n") }
        head.append("Connection: close\r\n\r\n")
        output.write(head.toString().toByteArray(Charsets.UTF_8))
        response.body?.let { output.write(it) }
        response.stream?.let { writeBody -> runBlocking { writeBody(output) } }
        output.flush()
    }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun reason(code: Int) = when (code) {
        200 -> "OK"
        202 -> "Accepted"
        206 -> "Partial Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        413 -> "Payload Too Large"
        416 -> "Range Not Satisfiable"
        502 -> "Bad Gateway"
        else -> if (code < 400) "OK" else "Error"
    }

    private companion object {
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_LINE = 8 * 1024
        const val MAX_HEADERS = 100
        const val MAX_BODY = 64 * 1024
    }
}
