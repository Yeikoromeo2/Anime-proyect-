package com.animehub.tv.data.update

import com.animehub.tv.fixtures.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actualización desde la app con la respuesta real de GitHub (Release v0.6.0, guardada el 2026-11-09). */
class UpdateCheckerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val apkBytes = ByteArray(200_000) { (it % 251).toByte() }
    private val api = "https://api.github.com/repos/Yeikoromeo2/Anime-proyect-/releases/latest"

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val url = chain.request().url.toString()
        val body = when {
            url == api -> Fixtures.read("github/release_latest.json").toByteArray()
            url.endsWith(".apk") -> apkBytes
            else -> ByteArray(0)
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(if (body.isEmpty()) 404 else 200).message("OK").body(body.toResponseBody()).build()
    }.build()

    @Test
    fun `comparacion de versiones`() {
        assertTrue(Versions.isNewer("0.6.0", "0.5.0"))
        assertTrue(Versions.isNewer("v0.10.0", "0.9.9"))
        assertTrue(Versions.isNewer("1.0", "0.99.0"))
        assertTrue(Versions.isNewer("0.6.0", "0.1.0-debug"))
        assertFalse(Versions.isNewer("0.6.0", "0.6.0"))
        assertFalse(Versions.isNewer("0.5.9", "0.6.0"))
    }

    @Test
    fun `hay version nueva - toma el APK de la Release`() = runTest {
        val update = UpdateChecker(client, api).check(installed = "0.5.0")!!
        assertEquals("0.6.0", update.version)
        assertEquals("https://github.com/Yeikoromeo2/Anime-proyect-/releases/download/v0.6.0/AnimeHubTV-0.6.0.apk", update.apkUrl)
        assertEquals(12_447_546L, update.sizeBytes)
    }

    @Test
    fun `ya esta al dia`() = runTest {
        assertNull(UpdateChecker(client, api).check(installed = "0.6.0"))
        assertNull(UpdateChecker(client, api).check(installed = "0.7.1"))
    }

    @Test
    fun `descarga el APK completo con su avance`() = runTest {
        val checker = UpdateChecker(client, api)
        val update = checker.check(installed = "0.5.0")!!
        val progress = mutableListOf<Float>()
        val file = checker.download(update, tmp.root.resolve("updates/AnimeHubTV.apk")) { progress += it }
        assertArrayEquals(apkBytes, file.readBytes())
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertFalse(tmp.root.resolve("updates/AnimeHubTV.apk.part").exists())
    }

    @Test
    fun `notas de la Release en texto simple`() {
        assertNull(com.animehub.tv.ui.update.releaseNotes("**Full Changelog**: https://github.com/x/y/compare/v0.5.0...v0.6.0"))
        assertEquals("Novedades\n• Reproductor nativo\n• Firma fija", com.animehub.tv.ui.update.releaseNotes("## Novedades\n- **Reproductor nativo**\n* Firma fija\nhttps://ejemplo.com"))
    }
}
