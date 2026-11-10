package com.animehub.tv.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.animehub.tv.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Una TV con AnimeHub encontrada en la red de casa. */
data class RemoteTv(val id: String, val name: String, val host: String, val port: Int)

sealed interface RemoteResult {
    data object Ok : RemoteResult
    /** La TV dijo que no (o nadie respondió la pregunta en la TV). */
    data object Rejected : RemoteResult
    /** La TV no está reproduciendo nada. */
    data object NothingPlaying : RemoteResult
    data class Failed(val message: String) : RemoteResult
}

/** Órdenes de la tablet a la TV (ver [RemoteProtocol]). Es una interfaz para simularla en las pruebas. */
interface RemoteClient {
    suspend fun play(tv: RemoteTv, request: PlayRequest): RemoteResult
    suspend fun command(tv: RemoteTv, command: RemoteCommand): RemoteResult
    suspend fun status(tv: RemoteTv): RemoteStatus?
}

/** Nombre e identificador de este aparato (los ve la TV al pedir permiso). */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsRepository,
) {
    val name: String = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Android"

    suspend fun id(): String = settings.deviceId()
}

class OkHttpRemoteClient @Inject constructor(private val identity: DeviceIdentity) : RemoteClient {

    /**
     * Cliente propio para la red local (sin la caché, las cookies ni los reintentos del de la app).
     * /play puede tardar: espera a que se acepte el dispositivo en la TV.
     */
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(65, TimeUnit.SECONDS)
        .build()

    private val json = RemoteProtocol.json
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun play(tv: RemoteTv, request: PlayRequest): RemoteResult =
        post(tv, "/play", json.encodeToString(PlayRequest.serializer(), request))

    override suspend fun command(tv: RemoteTv, command: RemoteCommand): RemoteResult =
        post(tv, "/command", json.encodeToString(RemoteCommand.serializer(), command))

    override suspend fun status(tv: RemoteTv): RemoteStatus? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(request(tv, "/status").get().build()).execute().use { r ->
                if (!r.isSuccessful) null else json.decodeFromString(RemoteStatus.serializer(), r.body.string())
            }
        }.getOrNull()
    }

    private suspend fun post(tv: RemoteTv, path: String, body: String): RemoteResult = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(request(tv, path).post(body.toRequestBody(jsonType)).build()).execute().use { r ->
                when (r.code) {
                    in 200..299 -> RemoteResult.Ok
                    403 -> RemoteResult.Rejected
                    409 -> RemoteResult.NothingPlaying
                    else -> RemoteResult.Failed("La TV respondió ${r.code}")
                }
            }
        }.getOrElse { RemoteResult.Failed("No se pudo conectar con ${tv.name}") }
    }

    private suspend fun request(tv: RemoteTv, path: String) = Request.Builder()
        .url("http://${hostForUrl(tv.host)}:${tv.port}$path")
        .header(RemoteProtocol.HEADER_ID, identity.id())
        .header(RemoteProtocol.HEADER_NAME, RemoteProtocol.encodeName(identity.name))

    /** Las direcciones IPv6 van entre corchetes en una URL. */
    private fun hostForUrl(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host
}
