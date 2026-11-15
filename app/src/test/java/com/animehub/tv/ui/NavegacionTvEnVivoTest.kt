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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
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

    /** Atrás como lo recibe la pantalla (el gesto/botón del sistema): no depende de qué elemento tenga el foco. */
    private fun backKey() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun openLiveTv() {
        activateDescription("Pestaña TV en vivo")
        activateDescription("Ver TV en vivo")
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
    fun ok_abre_el_menu_general_con_listas_por_pais_categoria_e_idioma_y_favoritos() {
        openLiveTv()
        waitForPlayer { FakeNative.played.isNotEmpty() }
        key(Key.DirectionCenter) // OK: el menú general (no la categoría del canal que suena)
        waitForText("TV en vivo")
        waitForText("★ Favoritos")
        waitForText("Por país")
        waitForText("Por categoría")
        waitForText("Por idioma")
        waitForText("Latinoamérica")
        waitForText("Dibujos animados")
        capture("61_tv_menu")
        // La lista caída (Studio Ghibli) y la repetida (Canales del mundo) ya no están.
        compose.onAllNodes(hasText("Studio Ghibli")).assertCountEquals(0)
        compose.onAllNodes(hasText("Canales del mundo")).assertCountEquals(0)
        // Por país → Perú primero (el orden pedido), luego los demás.
        activate("Por país")
        waitForText("Perú")
        waitForText("Chile")
        waitForText("España")
        activate("Perú")
        // Los canales de Perú (de la lista general): se marca uno como favorito.
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Añadir a favoritos", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val first = compose.onAllNodes(hasContentDescription("Añadir a favoritos", substring = true)).fetchSemanticsNodes().first()
        compose.onAllNodes(hasContentDescription("Añadir a favoritos", substring = true)).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Quitar de favoritos", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        backKey(); backKey() // sube a las listas
        waitForText("1 canal")
        capture("62_tv_favoritos")
        // Back cierra el menú; otro Back sale a Inicio.
        backKey()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("★ Favoritos")).fetchSemanticsNodes().isEmpty() }
        backKey()
        waitForText("AnimeHub TV") // de vuelta en Inicio (barra lateral)
    }

    @Test
    fun por_categoria_e_idioma_tienen_sus_submenus_en_espanol() {
        openLiveTv()
        waitForPlayer { FakeNative.played.isNotEmpty() }
        key(Key.DirectionCenter)
        activate("Por categoría")
        waitForText("Noticias")
        waitForText("Infantil")
        waitForText("Deportes")
        backKey()
        activate("Por idioma")
        waitForText("Español")
        waitForText("Inglés")
    }

    @Test
    fun las_series_de_dibujos_se_reproducen_como_titulos() {
        openLiveTv()
        waitForPlayer { FakeNative.played.isNotEmpty() }
        key(Key.DirectionCenter)
        activate("Dibujos animados")
        waitForText("Las aventuras de Tintin", substring = true)
        compose.onAllNodes(hasText("Las aventuras de Tintin", substring = true)).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        // El título entero (no la estrella de favoritos, cuya descripción también lo contiene).
        val title = "Las aventuras de Tintin - E1 Tintín en América"
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription(title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription(title)).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitForPlayer { FakeNative.played.last().url.endsWith(".mp4") }
        compose.onAllNodes(hasText("★ Favoritos")).assertCountEquals(0) // el menú se cerró al elegir
    }
}
