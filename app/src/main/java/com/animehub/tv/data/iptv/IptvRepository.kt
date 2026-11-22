package com.animehub.tv.data.iptv

import android.content.Context
import com.animehub.tv.BuildConfig
import com.animehub.tv.data.config.IptvListConfig
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.domain.model.IptvList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Datos de la lista de TV del usuario (servidor, usuario, contraseña y formato de transmisión). */
data class IptvAccount(val host: String, val user: String, val pass: String, val format: String = "ts") {
    val ready: Boolean get() = host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
}

/** Direcciones de la lista M3U simple y del formato alternativo de transmisión. */
object IptvUrl {
    /** `http://servidor/get.php?username=…&password=…&type=m3u&output=ts|m3u8`; null si el servidor no es válido. */
    fun list(account: IptvAccount, format: String = account.format, scheme: String? = null): HttpUrl? {
        val base = baseOf(account.host, scheme) ?: return null
        return base.newBuilder()
            .addPathSegment("get.php")
            .addQueryParameter("username", account.user)
            .addQueryParameter("password", account.pass)
            .addQueryParameter("type", "m3u")
            .addQueryParameter("output", if (format == "m3u8") "m3u8" else "ts")
            .build()
    }

    /** El servidor con `http://` si el usuario no escribió el esquema; [scheme] fuerza "http" o "https". */
    fun baseOf(host: String, scheme: String? = null): HttpUrl? {
        val raw = host.trim().trimEnd('/')
        if (raw.isEmpty()) return null
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        return if (scheme != null && scheme != url.scheme) url.newBuilder().scheme(scheme).build() else url
    }

    fun alternate(format: String) = if (format == "m3u8") "ts" else "m3u8"

    /** La misma señal en el otro formato (`…/123.ts` ↔ `…/123.m3u8`); null si la dirección no termina en uno de los dos. */
    fun swapStream(url: String): String? {
        val q = url.indexOf('?').let { if (it < 0) url.length else it }
        val path = url.substring(0, q)
        val tail = url.substring(q)
        return when {
            path.endsWith(".ts", ignoreCase = true) -> path.dropLast(3) + ".m3u8" + tail
            path.endsWith(".m3u8", ignoreCase = true) -> path.dropLast(5) + ".ts" + tail
            else -> null
        }
    }
}

/**
 * TV en vivo: baja la lista M3U del usuario (una sola, con sus categorías `group-title`), la guarda en
 * disco 6 horas para no repetir la petición a `get.php` y la lee sin repetidos. Si el formato elegido
 * falla se prueba el otro (`ts` ↔ `m3u8`) y, si el servidor no responde por `http`, por `https`.
 */
@Singleton
class IptvRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val errorLog: ErrorLogRepository,
) {
    private val dir get() = File(context.filesDir, "iptv").apply { mkdirs() }
    private val mutex = Mutex()
    private var memory: Pair<String, IptvList>? = null

    /** Servidor, usuario y contraseña: los de Ajustes o, en la compilación local de pruebas, los de local.properties. */
    suspend fun account(): IptvAccount {
        val s = settings.current()
        return IptvAccount(
            host = s.iptvHost.ifBlank { BuildConfig.IPTV_HOST },
            user = s.iptvUser.ifBlank { BuildConfig.IPTV_USER },
            pass = s.iptvPass.ifBlank { BuildConfig.IPTV_PASS },
            format = s.iptvFormat,
        )
    }

    /** El menú de TV: una sola lista (vacío mientras no estén los datos de la lista en Ajustes). */
    suspend fun menu(): List<IptvListConfig> =
        if (account().ready) listOf(IptvListConfig(LIST_ID, "TV en vivo", description = "Canales, películas y series")) else emptyList()

    suspend fun list(id: String): IptvList? = if (id == LIST_ID || id == START_ID) startList() else null

    /** La lista con la que arranca la TV (la primera vez, el primer canal). */
    suspend fun startList(): IptvList? = mutex.withLock {
        val account = account().takeIf { it.ready } ?: return@withLock null
        val key = cacheKey(account)
        memory?.takeIf { it.first == key }?.second ?: build(account, key).also { memory = key to it }.takeIf { it.channels.isNotEmpty() }
    }

    private suspend fun build(account: IptvAccount, key: String): IptvList = withContext(Dispatchers.IO) {
        val entries = runCatching { M3uParser.parseEntries(text(account, key)) }.getOrDefault(emptyList())
        M3uParser.buildList(LIST_ID, "TV en vivo", entries, kind = "live")
    }

    /** Texto de la lista: la copia en disco si tiene menos de 6 horas; si no, se baja (y si falla, la copia vieja). */
    private fun text(account: IptvAccount, key: String): String {
        val file = File(dir, "lista-$key.m3u")
        dir.listFiles()?.filter { it.name.startsWith("lista-") && it != file }?.forEach { it.delete() }
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < TTL_MS) return file.readText()
        val fresh = download(account)
        if (fresh != null) { file.writeText(fresh); return fresh }
        return if (file.exists()) file.readText() else ""
    }

    /** Baja la lista con el formato elegido y, si falla, con el otro; por `http` y luego `https`. */
    private fun download(account: IptvAccount): String? {
        val http = client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
            .build()
        var last: Exception? = null
        for (format in listOf(account.format, IptvUrl.alternate(account.format))) {
            val base = IptvUrl.baseOf(account.host) ?: break
            for (scheme in listOf(base.scheme, if (base.scheme == "http") "https" else "http")) {
                val url = IptvUrl.list(account, format, scheme) ?: continue
                try {
                    val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                    http.newCall(request).execute().use { r ->
                        val body = r.body.string()
                        if (r.isSuccessful && body.contains("#EXTINF")) return body
                        last = IllegalStateException("HTTP ${r.code}")
                    }
                } catch (e: Exception) {
                    last = e
                }
            }
        }
        last?.let { errorLog.logIssue("TV en vivo", "Lista M3U", it) }
        return null
    }

    /** Recarga manual: la próxima lectura vuelve a bajar la lista (sin esperar las 6 horas). */
    suspend fun refresh() = mutex.withLock {
        memory = null
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun cacheKey(a: IptvAccount) = Integer.toHexString("${a.host}|${a.user}|${a.pass}|${a.format}".hashCode())

    companion object {
        const val LIST_ID = "tv"
        const val START_ID = "inicio"
        const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
        const val TTL_MS = 6 * 60 * 60_000L
    }
}
