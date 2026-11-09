package com.animehub.tv.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.data.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * Reproducción con el control: capítulo nuevo → servidores que funcionan → reproduce →
 * progreso guardado → al terminar, cuenta atrás y siguiente episodio.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionReproductorTest : TvUiTest() {

    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
        // Los ajustes se guardan en disco: cada prueba empieza sin servidor preferido.
        runBlocking { settings.clearPreferredServers() }
        // Tank Chair 6 también está en JKAnime y AnimeFLV: estas pruebas usan los servidores de Anime Ninja.
        runBlocking { OTHERS.forEach { settings.setSiteEnabled(it, false) } }
    }

    @After
    fun tearDown() = runBlocking { OTHERS.forEach { settings.setSiteEnabled(it, true) } }

    private companion object {
        val OTHERS = listOf("jkanime", "animeflv")
    }

    private fun webView(): WebView? {
        fun find(v: View): WebView? = when (v) {
            is WebView -> v
            is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
            else -> null
        }
        return find(compose.activity.window.decorView)
    }

    /** Lo que enviaría el script inyectado desde la página del servidor. */
    private fun jsMessage(json: String) {
        val bridge = shadowOf(webView()!!).getJavascriptInterface("AnimeHub")!!
        bridge.javaClass.getMethod("postMessage", String::class.java).apply { isAccessible = true }.invoke(bridge, json)
        compose.waitForIdle()
    }

    private fun openTankChairEpisode6() {
        waitForFocusedDescription(LATEST_FIRST)
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasContentDescription(LATEST_FIRST)) and hasAnyDescendant(hasText("Últimos capítulos")).not())
            .onFirst().performScrollToNode(hasContentDescription("Tank Chair"))
        activateDescription("Tank Chair")
        waitForFocus("Ver episodio 6 · nuevo")
        key(Key.DirectionCenter)
    }

    @Test
    fun play_muestra_los_servidores_que_funcionan_y_reproduce_directo() {
        openTankChairEpisode6()
        waitForText("Elige un servidor", substring = true)
        // HLS y MP4Upload (sub y doblado) funcionan; Voe y Byse quedan aparte como no disponibles.
        waitForFocus("▶  HLS · Subtitulado")
        waitForText("Ver no disponibles (4)")
        capture("20_servidores")
        key(Key.DirectionCenter)
        compose.waitUntil(10_000) { webView()?.let { shadowOf(it).lastLoadedUrl } == "https://player.zilla-networks.com/play/fa543590d3399084aa0f1dc73ee1ccac" }

        // Progreso del video: se guarda el minuto exacto.
        jsMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false}""")
        jsMessage("""{"type":"progress","pos":312.5,"dur":1440,"paused":false}""")
        waitForText("5:12 / 24:00")
        capture("21_reproduciendo")
        compose.waitUntil(5_000) {
            runBlocking { library.history.first() }.any { it.episodeNumber == 6f && it.positionMs == 312_500L && it.serverName == "HLS" }
        }

        // Al terminar: cuenta atrás para el siguiente (Tank Chair tiene 6, así que este es el último).
        jsMessage("""{"type":"ended"}""")
        waitForText("Has visto el último episodio disponible")
        assertTrue(runBlocking { library.history.first() }.first { it.episodeNumber == 6f }.completed)
    }

    @Test
    fun con_servidor_preferido_reproduce_sin_preguntar_y_pasa_al_siguiente() {
        openTankChairEpisode6()
        waitForFocus("▶  HLS · Subtitulado")
        key(Key.DirectionCenter) // elige HLS: queda como preferido
        compose.waitUntil(10_000) { webView()?.let { shadowOf(it).lastLoadedUrl }?.contains("zilla-networks") == true }
        back() // salir del reproductor
        waitForFocus("Ver episodio 6 · nuevo")
        // Episodio 5: ahora reproduce directo con el preferido, sin lista.
        activate("Ep. 5")
        waitForText("Servidor preferido: HLS · ↑ para cambiar")
        jsMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false}""")
        jsMessage("""{"type":"progress","pos":1439,"dur":1440,"paused":false}""")
        // Cerca del final: siguiente episodio en 10 s.
        waitForText("Siguiente: Episodio 6 en", substring = true)
        capture("22_siguiente_episodio")
        activate("Ver ahora")
        waitForText("Tank Chair · Episodio 6", substring = true)
        // Los servidores del episodio 6 se buscan en segundo plano: se espera a que cargue el preferido
        // (entre episodios el reproductor pasa por about:blank, así que no basta con mirar al instante).
        compose.waitUntil(10_000) { webView()?.let { shadowOf(it).lastLoadedUrl }?.contains("zilla-networks") == true }
    }
}
