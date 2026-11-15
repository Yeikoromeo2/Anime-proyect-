package com.animehub.tv.ui

import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animehub.tv.di.FakeNative
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf

/** TV en vivo: suena sola, cambia de canal con CH+/CH−, menú transparente con listas y favoritos. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionTvEnVivoTest : TvUiTest() {

    @javax.inject.Inject lateinit var settings: com.animehub.tv.data.settings.SettingsRepository

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
        FakeNative.reset()
        // Favoritos y último canal se guardan en disco: cada prueba empieza limpia.
        kotlinx.coroutines.runBlocking { settings.clearLiveTv() }
        com.animehub.tv.ui.tv.LiveTv.menuIdleMs = 10 * 60_000L
    }

    @org.junit.After
    fun tearDown() { com.animehub.tv.ui.tv.LiveTv.menuIdleMs = 10_000L }

    private fun waitForPlayer(condition: () -> Boolean) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun openLiveTv() {
        toHeader()
        key(Key.DirectionDown) // fila de secciones
        activate("TV en vivo")
    }

    @Test
    fun entra_sonando_y_cambia_de_canal_con_los_botones_de_canal() {
        openLiveTv()
        waitForPlayer { FakeNative.played.isNotEmpty() }
        val first = FakeNative.played.last().url
        capture("60_tv_en_vivo")
        key(Key.ChannelUp)
        waitForPlayer { FakeNative.played.size >= 2 }
        assertNotEquals(first, FakeNative.played.last().url)
        key(Key.ChannelDown)
        waitForPlayer { FakeNative.played.size >= 3 }
        assertTrue("volvió al primero", FakeNative.played.last().url == first)
    }

    @Test
    fun el_menu_transparente_muestra_listas_categorias_canales_y_favoritos() {
        openLiveTv()
        waitForPlayer { FakeNative.played.isNotEmpty() }
        key(Key.DirectionCenter) // abre el menú en la categoría del canal que suena
        waitForText("Perú · ", substring = true)
        capture("61_tv_menu")
        key(Key.Back) // sube a las categorías
        waitForText("Perú")
        key(Key.Back) // sube a las listas
        waitForText("TV en vivo")
        waitForText("★ Favoritos")
        waitForText("Dibujos animados")
        waitForText("Studio Ghibli")
        waitForText("Canales del mundo")
        // Entrar a Perú → General → marcar un canal como favorito.
        activate("Perú")
        activate("General")
        waitForText("36 TV Huacho")
        activateDescription("Añadir a favoritos 36 TV Huacho")
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Quitar de favoritos 36 TV Huacho")).fetchSemanticsNodes().isNotEmpty() }
        key(Key.Back); key(Key.Back)
        waitForText("1 canal")
        capture("62_tv_favoritos")
        // Back cierra el menú; otro Back sale a Inicio.
        key(Key.Back)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("★ Favoritos")).fetchSemanticsNodes().isEmpty() }
        key(Key.Back)
        waitForText("Secciones")
    }

    @Test
    fun las_listas_de_peliculas_y_dibujos_se_reproducen_como_titulos() {
        openLiveTv()
        waitForPlayer { FakeNative.played.isNotEmpty() }
        key(Key.DirectionCenter)
        waitForText("Perú · ", substring = true) // espera a que el menú esté abierto
        key(Key.Back); waitForText("Perú")
        key(Key.Back); waitForText("TV en vivo")
        activate("Studio Ghibli")
        waitForText("Películas")
        activate("Películas")
        activateDescription("Cuentos de Terramar")
        waitForPlayer { FakeNative.played.last().url.endsWith(".mp4") }
        compose.onAllNodes(hasText("★ Favoritos")).assertCountEquals(0) // el menú se cerró al elegir
    }
}
