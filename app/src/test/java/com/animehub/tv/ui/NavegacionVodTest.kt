package com.animehub.tv.ui

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animehub.tv.di.FakeNative
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Módulo VOD de punta a punta: explorador (Comunidad / Servidor), detalle, selector de fuente y respaldo automático. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionVodTest : TvUiTest() {

    @javax.inject.Inject lateinit var settings: com.animehub.tv.data.settings.SettingsRepository
    @javax.inject.Inject lateinit var vod: com.animehub.tv.vod.VodRepository

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
        FakeNative.reset()
        kotlinx.coroutines.runBlocking {
            settings.setIptvHost("http://lista.test"); settings.setIptvUser("prueba"); settings.setIptvPass("prueba")
            settings.setCommunityCatalogUrl("https://v3-cinemeta.strem.io")
            settings.setCommunityAddons("http://addon.test")
        }
        vod.refresh()
    }

    private fun waitForPlayer(condition: () -> Boolean) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    @Test
    fun peliculas_muestra_las_fuentes_y_reproduce_con_las_cabeceras_y_el_respaldo() {
        activateDescription("Pestaña Películas")
        waitForText("Comunidad")
        waitForText("Servidor")
        waitForText("Todo")
        capture("62_vod_peliculas")
        activateDescription("Batman Test")
        waitForText("▶ Reproducir")
        waitForText("Película · 2005 · ★ 8.2 · Action, Crime")
        capture("63_vod_detalle")
        activate("▶ Reproducir")
        // La primera fuente (la mejor calidad) suena con las cabeceras que pide.
        waitForPlayer { FakeNative.played.isNotEmpty() }
        val first = FakeNative.played.first()
        assertEquals("https://cdn.test/a/1080.mp4", first.url)
        assertTrue(first.headers["User-Agent"]!!.startsWith("VLC/3.0.20"))
        assertEquals("https://cdn.test/", first.headers["Referer"])
        // Si falla, pasa sola a la siguiente fuente.
        FakeNative.emit("""{"type":"error"}""")
        waitForPlayer { FakeNative.played.size >= 2 }
        assertEquals("https://cdn.test/b/720.m3u8", FakeNative.played.last().url)
    }

    @Test
    fun series_muestra_temporadas_y_reproduce_el_episodio_elegido() {
        activateDescription("Pestaña Series")
        activateDescription("Serie Test")
        waitForText("Temporada 1")
        waitForText("Temporada 2")
        waitForText("1. Piloto")
        activate("2. Segundo")
        waitForPlayer { FakeNative.played.isNotEmpty() }
        assertEquals("https://cdn.test/s/s01e02.mkv", FakeNative.played.first().url)
    }

    @Test
    fun el_filtro_de_servidor_muestra_el_catalogo_del_servidor_y_su_detalle() {
        activateDescription("Pestaña Películas")
        waitForText("Servidor")
        activate("Servidor")
        waitForText("Acción") // categorías del servidor
        activateDescription("Película Uno")
        waitForText("▶ Reproducir")
        activate("▶ Reproducir")
        waitForPlayer { FakeNative.played.isNotEmpty() }
        assertEquals("http://lista.test/movie/prueba/prueba/501.mp4", FakeNative.played.first().url)
    }
}
