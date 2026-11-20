package com.animehub.tv.ui

import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.input.key.Key
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animehub.tv.di.TestAppModule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actualizar desde la TV: aviso en Inicio, descarga, permiso de instalar apps e instalador de Android. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionActualizacionTest : TvUiTest() {

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
    }

    private val V = TestAppModule.UPDATE_TEST_VERSION

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun nextStartedActivity(): Intent? = shadowOf(compose.activity.application).nextStartedActivity

    @Test
    fun version_nueva_se_descarga_e_instala_encima() {
        // La Release simulada es la 99.0.0 (más nueva que cualquier compilación): aparece en la cabecera.
        toHeader()
        compose.waitUntil(10_000) { idleMain(); compose.onAllNodes(androidx.compose.ui.test.hasText("⬆ Versión $V")).fetchSemanticsNodes().isNotEmpty() }
        repeat(4) { key(Key.DirectionRight) } // Explorar, Historial, Ajustes y el aviso
        waitForFocus("⬆ Versión $V")
        key(Key.DirectionCenter)
        waitForText("Nueva versión $V")
        waitForText("se instala encima", substring = true)
        capture("31_actualizacion")

        // La primera vez Android pide permitir que la app instale apps.
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>().packageManager).setCanRequestPackageInstalls(false)
        activate("Descargar e instalar")
        compose.waitUntil(10_000) { idleMain(); compose.onAllNodes(androidx.compose.ui.test.hasText("Falta un permiso (solo la primera vez)")).fetchSemanticsNodes().isNotEmpty() }
        capture("32_actualizacion_permiso")
        activate("Abrir el permiso")
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, nextStartedActivity()?.action)

        // Con el permiso dado: se abre el instalador de Android con el APK descargado.
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>().packageManager).setCanRequestPackageInstalls(true)
        activate("Instalar")
        val install = nextStartedActivity()!!
        assertEquals(Intent.ACTION_VIEW, install.action)
        assertEquals("application/vnd.android.package-archive", install.type)
        assertEquals("content", install.data?.scheme)
        waitForText("Instalando la versión $V")
    }
}
