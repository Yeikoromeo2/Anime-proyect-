package com.animehub.tv.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Versión publicada en Releases que se puede instalar encima de la actual. */
data class AppUpdate(val version: String, val apkUrl: String, val sizeBytes: Long, val notes: String?)

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
internal data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

/**
 * Busca la última versión en GitHub Releases y descarga su APK.
 * Como todas las versiones se firman con la misma clave, Android la instala encima de la actual.
 * @param latestUrl API de la última Release (`…/repos/{dueño}/{repo}/releases/latest`).
 */
class UpdateChecker(client: OkHttpClient, private val latestUrl: String) {

    private val client = client.newBuilder()
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // el APK pesa ~12 MB: sin límite total
        .cache(null)
        .build()

    /** La versión publicada si es más nueva que [installed]; null si no hay nada nuevo. */
    suspend fun check(installed: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(latestUrl).header("Accept", "application/vnd.github+json").build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub respondió ${response.code}")
            response.body.string()
        }
        val release = json.decodeFromString(GithubRelease.serializer(), body)
        if (release.draft || release.prerelease) return@withContext null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@withContext null
        val version = release.tagName.removePrefix("v")
        if (!Versions.isNewer(version, installed)) return@withContext null
        AppUpdate(version, apk.url, apk.size, release.body?.takeIf { it.isNotBlank() })
    }

    /** Descarga el APK en [target], informando el avance (0–1). */
    suspend fun download(update: AppUpdate, target: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("La descarga respondió ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            response.body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) throw IOException("No se pudo guardar la actualización")
        onProgress(1f)
        target
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Comparación de versiones "1.2.3" (sin la "v" ni sufijos como "-debug"). */
object Versions {
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
}
