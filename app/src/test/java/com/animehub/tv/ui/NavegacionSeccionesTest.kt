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

    /** Elige la pestaña del Inicio (su página con el mosaico) y entra a "Ver todo y buscar". */
    private fun openSection(name: String) {
        activateDescription("Pestaña $name")
        activate("Ver todo y buscar")
    }

    @Test
    fun la_pagina_de_cada_pestana_muestra_su_mosaico_de_titulos() {
        activateDescription("Pestaña Retro")
        waitForDescription("2 Perros Tontos")
        capture("49_inicio_retro")
        activateDescription("Pestaña Pelis")
        waitForDescription("Quartier Lointain")
        capture("49_inicio_pelis")
        activateDescription("Pestaña Series")
        waitForDescription("Policías de Chicago")
        // La pestaña de TV: bloque "Ver TV en vivo".
        activateDescription("Pestaña TV en vivo")
        waitForDescription("Ver TV en vivo")
        capture("49_inicio_tv")
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
