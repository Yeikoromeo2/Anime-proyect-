package com.animehub.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las secciones nuevas (Retro, Pelis, Series): cada una con su catálogo, su buscador y su ficha. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionSeccionesTest : TvUiTest() {

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
    }

    private fun openSection(name: String) {
        toHeader()
        key(Key.DirectionDown) // la fila de secciones queda justo debajo de la cabecera
        activate(name)
    }

    @Test
    fun retro_lista_caricaturas_y_abre_su_ficha_con_temporadas() {
        openSection("Retro")
        waitForText("Buscar una caricatura")
        waitForText("títulos cargados", substring = true)
        waitForText("Canal", substring = false)
        capture("50_retro")
        activateDescription("2 Perros Tontos")
        waitForText("Temporada 1", substring = true)
        waitForText("Ver episodio 1")
        capture("51_retro_ficha")
    }

    @Test
    fun pelis_lista_el_catalogo_y_abre_su_ficha_con_un_solo_episodio() {
        openSection("Pelis")
        waitForText("Buscar una película")
        waitForText("títulos cargados", substring = true)
        waitForDescription("Quartier Lointain")
        capture("52_pelis")
        // Con una sola fuente no hay menú "Origen".
        compose.onAllNodes(hasText("Origen")).assertCountEquals(0)
        activateDescription("Quartier Lointain")
        waitForText("Ver", substring = false)
    }

    @Test
    fun series_lista_el_catalogo_de_la_api() {
        openSection("Series")
        waitForText("Buscar una serie")
        waitForText("títulos cargados", substring = true)
        waitForDescription("Policías de Chicago")
        capture("54_series")
        activateDescription("Policías de Chicago")
        waitForText("Temporada 1", substring = true)
        capture("55_series_ficha")
    }

    private fun waitForDescription(description: String) =
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
}
