package com.animehub.tv.ui

import android.os.Looper
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.di.FakeNative
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * La misma app en una tablet (sin control remoto): todo se maneja tocando la pantalla.
 * Los componentes de TV no reconocían el toque; ahora tarjetas, botones y listas sí.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
class NavegacionTabletTest : TvUiTest() {

    @Inject lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
        FakeNative.reset()
        runBlocking {
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

    private fun tap(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitFor(matcher)
        return compose.onAllNodes(matcher).onFirst().also {
            it.performTouchInput { click() }
            compose.waitForIdle()
        }
    }

    @Test
    fun se_maneja_tocando_inicio_ficha_y_reproductor() {
        // Inicio: tocar una portada abre la ficha.
        waitFor(hasContentDescription(LATEST_FIRST))
        capture("40_tablet_inicio")
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasContentDescription(LATEST_FIRST)) and hasAnyDescendant(hasText("Últimos capítulos")).not())
            .onFirst().performScrollToNode(hasContentDescription("Tank Chair"))
        tap(hasContentDescription("Tank Chair"))
        // Ficha: tocar el episodio lo reproduce (arranca solo con el mejor servidor).
        tap(hasText("Ver episodio 6 · nuevo"))
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            FakeNative.played.isNotEmpty()
        }

        // Reproductor: un toque muestra los controles; tocar "Pausa" pausa el video.
        FakeNative.emit("""{"type":"playing","pos":0,"dur":1440,"paused":false,"frame":"native"}""")
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400) // espera del doble toque
        tap(hasContentDescription("Pausa"))
        compose.waitUntil(5_000) { FakeNative.commands.contains("""{"type":"toggle"}""") }
        waitFor(hasText("Toca la pantalla para ver los controles", substring = true))
        capture("41_tablet_controles")

        // Doble toque en el lado derecho: avanza.
        compose.onRoot().performTouchInput { doubleClick(centerRight.copy(x = width * 0.85f)) }
        compose.waitUntil(5_000) { FakeNative.commands.contains("""{"type":"seekBy","seconds":10}""") }

        // "Servidores": la cuadrícula se maneja tocando.
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        tap(hasText("Servidores"))
        tap(hasContentDescription("MP4Upload · Subtitulado"))
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            FakeNative.played.last().url.endsWith("/video.mp4")
        }
        assertTrue(FakeNative.played.size >= 2)
    }

    /** En Android 16 las tablets pueden girar la app aunque pida horizontal: también debe verse bien. */
    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun en_vertical_tambien_se_ve_bien() {
        waitFor(hasContentDescription(LATEST_FIRST))
        capture("42_tablet_vertical_inicio")
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasContentDescription(LATEST_FIRST)) and hasAnyDescendant(hasText("Últimos capítulos")).not())
            .onFirst().performScrollToNode(hasContentDescription("Tank Chair"))
        tap(hasContentDescription("Tank Chair"))
        waitFor(hasText("Ver episodio 6 · nuevo"))
        capture("43_tablet_vertical_ficha")
    }
}
