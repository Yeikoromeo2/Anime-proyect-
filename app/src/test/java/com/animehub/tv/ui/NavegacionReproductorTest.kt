package com.animehub.tv.ui

import android.os.Looper
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
import com.animehub.tv.di.FakeNative
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        FakeNative.reset()
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

    /** Lo que mandaría el reproductor nativo (ExoPlayer) a la app. */
    private fun nativeMessage(json: String) {
        compose.runOnUiThread { FakeNative.emit(json) }
        compose.waitForIdle()
    }

    /**
     * Los servidores se buscan en segundo plano y la respuesta vuelve al hilo principal: en
     * Robolectric ese hilo solo avanza cuando la prueba lo procesa (en la TV corre solo).
     */
    private fun waitForPlayer(condition: () -> Boolean) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun waitForDescription(description: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }

    private val zillaPlaylist = "https://player.zilla-networks.com/m3u8/fa543590d3399084aa0f1dc73ee1ccac"

    @Test
    fun play_arranca_solo_con_el_mejor_servidor_en_el_nativo_y_muestra_los_demas_en_cuadros() {
        openTankChairEpisode6()
        // Sin preguntar: el mejor servidor (HLS, que funciona y entrega el video directo) arranca solo.
        // Sin la página del servidor: la lista HLS va directo al reproductor nativo, con su Referer.
        waitForPlayer { FakeNative.played.any { it.url == zillaPlaylist } }
        waitForText("Servidor: HLS · ↑ para cambiar")
        assertEquals("https://player.zilla-networks.com/", FakeNative.played.last().headers["Referer"])
        assertNotEquals("https://player.zilla-networks.com/play/fa543590d3399084aa0f1dc73ee1ccac", webView()?.let { shadowOf(it).lastLoadedUrl })

        // Progreso del video: se guarda el minuto exacto.
        nativeMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false,"frame":"native"}""")
        nativeMessage("""{"type":"progress","pos":312.5,"dur":1440,"paused":false,"frame":"native"}""")
        waitForText("5:1", substring = true) // la barra de avance: transcurrido (sigue corriendo) y restante
        waitForText("−18:4", substring = true)
        waitForText("HLS · Anime Ninja · Reproductor nativo")
        capture("21_reproduciendo")
        compose.waitUntil(5_000) {
            runBlocking { library.history.first() }.any { it.episodeNumber == 6f && it.positionMs == 312_500L && it.serverName == "HLS" }
        }
        // OK pausa el reproductor nativo (no la página web), con el icono de pausa animado en el centro.
        key(Key.DirectionCenter)
        compose.waitUntil(5_000) { FakeNative.commands.contains("""{"type":"toggle"}""") }
        waitForDescription("Pausa")
        nativeMessage("""{"type":"pause","pos":312.5,"dur":1440,"paused":true,"frame":"native"}""")
        compose.mainClock.advanceTimeBy(1_000)
        waitForDescription("En pausa")
        // En pausa la barra de información no se oculta.
        compose.mainClock.advanceTimeBy(6_000)
        waitForText("HLS · Anime Ninja · Reproductor nativo · En pausa")
        capture("33_en_pausa")
        // → dos veces seguidas: el salto se suma a un lado ("+20 s"); ← cambia de lado.
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        waitForDescription("+20 s")
        capture("34_avanzar")
        assertEquals(2, FakeNative.commands.count { it == """{"type":"seekBy","seconds":10}""" })
        key(Key.DirectionLeft)
        waitForDescription("−10 s")

        // ↑ → "Cambiar servidor": todos en cuadros (HLS y MP4Upload con reproductor nativo; Voe y Byse caídos, al final).
        key(Key.DirectionUp)
        activate("Cambiar servidor")
        waitForText("Elige un servidor", substring = true)
        waitForFocusedDescription("HLS · Subtitulado")
        waitForText("No disponible", substring = true)
        capture("20_servidores")
        activateDescription("MP4Upload · Subtitulado")
        waitForPlayer { FakeNative.played.last().url.endsWith("/video.mp4") }
        nativeMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false,"frame":"native"}""")

        // Al terminar: Tank Chair tiene 6 episodios, así que este es el último.
        nativeMessage("""{"type":"ended","frame":"native"}""")
        waitForText("Has visto el último episodio disponible")
        assertTrue(runBlocking { library.history.first() }.first { it.episodeNumber == 6f }.completed)
    }

    @Test
    fun si_el_nativo_falla_sigue_con_la_pagina_del_mismo_servidor() {
        openTankChairEpisode6()
        waitForPlayer { FakeNative.played.any { it.url == zillaPlaylist } }
        nativeMessage("""{"type":"error","code":2004,"frame":"native"}""")
        // Mismo servidor, ahora en el reproductor web, con aviso.
        compose.waitUntil(10_000) { webView()?.let { shadowOf(it).lastLoadedUrl } == "https://player.zilla-networks.com/play/fa543590d3399084aa0f1dc73ee1ccac" }
        waitForText("usando el reproductor web", substring = true)
        jsMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false}""")
        jsMessage("""{"type":"progress","pos":60,"dur":1440,"paused":false}""")
        waitForText("1:0", substring = true)
        waitForText("HLS · Anime Ninja · Reproductor web")
        capture("23_respaldo_web")
        // Los avisos se van solos a los pocos segundos (no quedan pegados sobre el video).
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("usando el reproductor web", substring = true)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun con_servidor_preferido_reproduce_sin_preguntar_y_pasa_al_siguiente() {
        openTankChairEpisode6()
        waitForPlayer { FakeNative.played.any { it.url == zillaPlaylist } }
        // Elegirlo en la lista lo deja como preferido (arrancar solo no).
        key(Key.DirectionUp)
        activate("Cambiar servidor")
        waitForFocusedDescription("HLS · Subtitulado")
        key(Key.DirectionCenter)
        waitForPlayer { FakeNative.played.size >= 2 }
        back() // salir del reproductor
        waitForFocus("Ver episodio 6 · nuevo")
        // Episodio 5: ahora reproduce directo con el preferido, sin lista. (Con miniaturas, los
        // episodios quedan más abajo: se baja hasta él.)
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText("Ver episodio 6 · nuevo")))
            .onFirst().performScrollToNode(hasText("Ep. 5"))
        activate("Ep. 5")
        waitForText("Servidor preferido: HLS · ↑ para cambiar")
        waitForPlayer { FakeNative.played.size >= 3 }
        nativeMessage("""{"type":"playing","pos":0,"dur":1440,"paused":false,"frame":"native"}""")
        nativeMessage("""{"type":"progress","pos":1439,"dur":1440,"paused":false,"frame":"native"}""")
        // Cerca del final: siguiente episodio en 10 s.
        waitForText("Siguiente: Episodio 6 en", substring = true)
        capture("22_siguiente_episodio")
        activate("Ver ahora")
        waitForText("Tank Chair · Episodio 6", substring = true)
        // Los servidores del episodio 6 se buscan en segundo plano y la respuesta vuelve al hilo
        // principal: en Robolectric ese hilo solo avanza cuando la prueba lo procesa, así que se
        // procesa en cada comprobación (en la TV corre solo).
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            FakeNative.played.size >= 4
        }
    }
}
