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

/** TV en vivo con la lista del usuario: arranca sola, con la cabecera de VLC y el formato alternativo si el primero falla. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionTvEnVivoTest : TvUiTest() {

    @javax.inject.Inject lateinit var settings: com.animehub.tv.data.settings.SettingsRepository
    @javax.inject.Inject lateinit var iptv: com.animehub.tv.data.iptv.IptvRepository

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
        FakeNative.reset()
        kotlinx.coroutines.runBlocking {
            settings.clearLiveTv()
            settings.setIptvHost("http://lista.test")
            settings.setIptvUser("prueba")
            settings.setIptvPass("prueba")
            settings.setIptvFormat("ts")
            iptv.refresh()
        }
        com.animehub.tv.ui.tv.LiveTv.menuIdleMs = 10 * 60_000L
    }

    @org.junit.After
    fun tearDown() { com.animehub.tv.ui.tv.LiveTv.menuIdleMs = 10_000L }

    private fun waitForPlayer(condition: () -> Boolean) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    @Test
    fun la_pestana_de_tv_abre_la_lista_y_suena_el_primer_canal_con_la_cabecera_de_vlc() {
        activateDescription("Pestaña TV en vivo")
        // Con output=ts el servidor de prueba responde 404: la lista se baja con el formato alternativo.
        waitForPlayer { FakeNative.played.isNotEmpty() }
        val first = FakeNative.played.first()
        assertTrue(first.url.endsWith("/101.ts"))
        assertEquals("VLC/3.0.20 LibVLC/3.0.20", first.headers["User-Agent"])
        capture("60_tv_en_vivo")
    }

    @Test
    fun sin_lista_m3u_los_canales_salen_de_la_api_y_suena_el_que_trae_direccion() {
        kotlinx.coroutines.runBlocking { settings.setIptvUser("soloapi"); iptv.refresh() }
        activateDescription("Pestaña TV en vivo")
        // El primer canal de la API no trae dirección: arranca el primero que sí (Canal Cuatro).
        waitForPlayer { FakeNative.played.isNotEmpty() }
        assertTrue(FakeNative.played.first().url.endsWith("/live/directo/4.ts"))
    }

    @Test
    fun si_el_canal_no_da_imagen_se_prueba_el_formato_alternativo() {
        activateDescription("Pestaña TV en vivo")
        waitForPlayer { FakeNative.played.isNotEmpty() }
        // Error de reproducción: primero se cambia a .m3u8 (el original estaba en .ts).
        FakeNative.emit("""{"type":"error"}""")
        waitForPlayer { FakeNative.played.size >= 2 }
        assertTrue(FakeNative.played.last().url.endsWith("/101.m3u8"))
    }
}
