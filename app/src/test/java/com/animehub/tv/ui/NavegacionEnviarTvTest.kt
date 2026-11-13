package com.animehub.tv.ui

import android.os.Looper
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.di.FakeNative
import com.animehub.tv.di.FakeRemote
import com.animehub.tv.remote.HttpRequest
import com.animehub.tv.remote.HttpResponse
import com.animehub.tv.remote.PlayRequest
import com.animehub.tv.remote.RemoteProtocol
import com.animehub.tv.remote.DlnaDevice
import com.animehub.tv.remote.RemoteTv
import okhttp3.OkHttpClient
import okhttp3.Request
import com.animehub.tv.remote.TvReceiver
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/** "Enviar a mi TV": la tablet envía el episodio y maneja la TV; la TV pregunta antes de obedecer. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionEnviarTvTest : TvUiTest() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var receiver: TvReceiver

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
        FakeNative.reset()
        FakeRemote.reset()
        runBlocking {
            settings.forgetRemotes()
            settings.clearPreferredServers()
            OTHERS.forEach { settings.setSiteEnabled(it, false) }
        }
    }

    @After
    fun tearDown() = runBlocking { OTHERS.forEach { settings.setSiteEnabled(it, true) } }

    private companion object {
        val OTHERS = listOf("jkanime", "animeflv")
    }

    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        compose.onAllNodes(matcher).onFirst().performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun openTankChair() {
        waitFor(hasContentDescription(LATEST_FIRST))
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasContentDescription(LATEST_FIRST)) and hasAnyDescendant(hasText("Últimos capítulos")).not())
            .onFirst().performScrollToNode(hasContentDescription("Tank Chair"))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun la_tablet_envia_el_episodio_a_la_tv_y_la_maneja() {
        FakeRemote.found.value = listOf(RemoteTv("tv-1", "TV de la sala", "192.168.1.20", 41234))
        openTankChair()
        tap(hasContentDescription("Tank Chair"))
        // Un solo botón "Ver en"; su popup lista solo los nombres de los aparatos de la red.
        tap(hasContentDescription("Ver en otra pantalla"))
        waitFor(hasText("✓ Este dispositivo"))
        tap(hasText("TV de la sala"))
        tap(hasText("Ver episodio 6 · nuevo"))
        waitFor(hasText("📺  En TV de la sala"))
        compose.waitUntil(5_000) { FakeRemote.played.isNotEmpty() }
        assertEquals(6f, FakeRemote.played.single().episodeNumber)
        waitFor(hasText("Reproduciendo en la TV"))
        capture("44_tablet_control_tv")
        assertTrue("No debe reproducirse en la tablet", FakeNative.played.isEmpty())

        tap(hasText("⏸  Pausa"))
        compose.waitUntil(5_000) { FakeRemote.commands.any { it.type == "toggle" } }
        waitFor(hasText("En pausa"))
        tap(hasText("+10 s"))
        compose.waitUntil(5_000) { FakeRemote.commands.any { it.type == "seekBy" && it.seconds == 10 } }
    }

    /** Llama al receptor de la TV como lo haría la tablet por la red (fuera del hilo principal). */
    private fun fromTablet(method: String, path: String, body: String = ""): Deferred<HttpResponse> =
        CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
            receiver.handler.handle(
                HttpRequest(
                    method = method,
                    path = path,
                    headers = mapOf(
                        RemoteProtocol.HEADER_ID.lowercase() to "tablet-prueba",
                        RemoteProtocol.HEADER_NAME.lowercase() to RemoteProtocol.encodeName("Tablet de prueba"),
                    ),
                    body = body.toByteArray(),
                ),
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Deferred<HttpResponse>.awaitHere(): HttpResponse {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            isCompleted
        }
        return getCompleted()
    }

    @Test
    fun la_tv_pregunta_si_permite_la_tablet_y_luego_obedece() {
        waitFor(hasContentDescription(LATEST_FIRST))
        val play = RemoteProtocol.json.encodeToString(
            PlayRequest.serializer(),
            PlayRequest(
                siteId = "animeninja", animeUrl = "https://veranime.ninja/anime/tank-chair/", animeTitle = "Tank Chair",
                episodeUrl = "https://veranime.ninja/ver/tank-chair-episodio-6/", episodeNumber = 6f,
            ),
        )
        val response = fromTablet("POST", "/play", play)
        // La primera vez, la TV pregunta.
        waitFor(hasText("¿Permitir “Tablet de prueba”?"))
        capture("45_tv_permitir")
        activate("Permitir")
        assertEquals(200, response.awaitHere().code)
        // Se abre el reproductor de la TV con ese episodio.
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            FakeNative.played.isNotEmpty()
        }

        // La tablet ya no necesita permiso: maneja el reproductor y lee el estado.
        assertEquals(200, fromTablet("POST", "/command", """{"type":"toggle"}""").awaitHere().code)
        assertTrue(FakeNative.commands.contains("""{"type":"toggle"}"""))
        val status = String(fromTablet("GET", "/status").awaitHere().body!!)
        assertTrue(status, "Tank Chair" in status && "\"episode\":6.0" in status)

        // "Detener en la TV": se cierra el reproductor y vuelve a Inicio.
        assertEquals(200, fromTablet("POST", "/command", """{"type":"stop"}""").awaitHere().code)
        waitFor(hasContentDescription(LATEST_FIRST))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun la_tablet_envia_a_una_tv_dlna_el_video_directo_por_el_intermediario() {
        FakeRemote.extra.value = listOf(DlnaDevice("uuid:samsung", "[TV] Samsung Q60", "http://192.168.1.30:9197/upnp/control/AVTransport1"))
        openTankChair()
        tap(hasContentDescription("Tank Chair"))
        tap(hasContentDescription("Ver en otra pantalla"))
        waitFor(hasText("✓ Este dispositivo"))
        tap(hasText("[TV] Samsung Q60"))
        tap(hasText("Ver episodio 6 · nuevo"))
        waitFor(hasText("🖥  En [TV] Samsung Q60"))
        // La tablet buscó el video directo del episodio (HLS) y lo publicó con su intermediario.
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            FakeRemote.loaded.isNotEmpty()
        }
        val loaded = FakeRemote.loaded.single()
        assertTrue(loaded.url, loaded.url.startsWith("http://127.0.0.1:") && "/p/" in loaded.url)
        assertEquals("application/x-mpegURL", loaded.mime)
        // Lo que pedirá la TV: la lista del servidor, reescrita para que los trozos pasen por la tablet.
        val playlist = OkHttpClient().newCall(Request.Builder().url(loaded.url).build()).execute().use { it.body.string() }
        assertTrue(playlist.take(200), playlist.startsWith("#EXTM3U") && playlist.lines().filter { it.startsWith("http") }.all { "/p/" in it })
        waitFor(hasText("Reproduciendo en la TV"))
        capture("46_tablet_dlna")

        tap(hasText("⏸  Pausa"))
        compose.waitUntil(5_000) { FakeRemote.toggles == 1 }
        waitFor(hasText("En pausa"))
    }
}
