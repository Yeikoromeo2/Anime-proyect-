package com.animehub.tv.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pestañas Películas y Series: estantes por categoría de la lista y ficha de cada título. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionCatalogoTest : TvUiTest() {

    @javax.inject.Inject lateinit var settings: com.animehub.tv.data.settings.SettingsRepository
    @javax.inject.Inject lateinit var catalog: com.animehub.tv.data.iptv.CatalogRepository

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
        kotlinx.coroutines.runBlocking {
            settings.setIptvHost("http://lista.test")
            settings.setIptvUser("prueba")
            settings.setIptvPass("prueba")
            catalog.refresh()
        }
    }

    @Test
    fun la_pestana_de_peliculas_muestra_sus_estantes_y_abre_la_ficha() {
        activateDescription("Pestaña Películas")
        waitForText("2 títulos")
        waitForText("Acción")
        capture("62_catalogo_peliculas")
        activateDescription("Película Uno")
        waitForText("▶ Reproducir")
        waitForText("Película · 2024 · ★ 4.2")
        capture("63_catalogo_ficha")
    }

    @Test
    fun la_pestana_de_series_muestra_la_sinopsis_en_la_ficha() {
        activateDescription("Pestaña Series")
        waitForText("1 títulos")
        waitForText("Drama")
        activateDescription("Serie Uno")
        waitForText("Una historia de prueba.")
    }
}
