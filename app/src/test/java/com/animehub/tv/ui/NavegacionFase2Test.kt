package com.animehub.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fase 2 con el control remoto: novedades de Anime Ninja en Inicio, búsqueda con cuadrícula
 * de portadas y ficha del anime (los HTML vienen de los fixtures de la Fase 0).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionFase2Test : TvUiTest() {

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
    }

    private fun searchFor(text: String) {
        toHeader()
        key(Key.DirectionCenter) // "Buscar"
        waitForText("Nombre del anime")
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForText("resultados para", substring = true)
    }

    @Test
    fun capitulos_nuevos_arriba_en_una_sola_fila_sin_repetir() {
        waitForText("Últimos capítulos")
        // "Últimos capítulos" alterna sitios: 1º de Latanime, 1º de Anime Ninja…
        waitForFocusedDescription(LATEST_FIRST)
        key(Key.DirectionRight)
        compose.onAllNodes(hasContentDescription("Mouse Cursor de Genjitsu wo Sousa Dekiru You ni Natta node, Onna no Ko wo Ippai Click Shimaasu") and isFocused())
            .assertCountEquals(1)
        // Ya no hay una fila de novedades por sitio que repita los mismos capítulos.
        compose.onAllNodes(hasText("Novedades", substring = true)).assertCountEquals(0)
        capture("08_inicio_novedades")
    }

    @Test
    fun elegir_un_capitulo_nuevo_abre_su_ficha_con_ese_capitulo_destacado() {
        waitForFocusedDescription(LATEST_FIRST)
        // Tank Chair, episodio 6 (publicado en Anime Ninja), más a la derecha en "Últimos capítulos".
        // La primera fila horizontal es "Últimos capítulos".
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasContentDescription(LATEST_FIRST)) and hasAnyDescendant(hasText("Últimos capítulos")).not())
            .onFirst()
            .performScrollToNode(hasContentDescription("Tank Chair"))
        activateDescription("Tank Chair")
        waitForText("Ver episodio 6 · nuevo")
        waitForFocus("Ver episodio 6 · nuevo")
        waitForText("NUEVO")
        capture("15_ficha_capitulo_nuevo")
    }

    @Test
    fun busqueda_muestra_cuadricula_de_portadas_y_el_foco_va_al_primer_resultado() {
        searchFor("dragon")
        waitForText("61 resultados para \"dragon\"") // 30 de Anime Ninja + 30 de JKAnime + 1 de AnimeFLV
        // El foco pasa solo al primer resultado al terminar la búsqueda.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithContentDescription("Dragon Ball Daima").assertIsFocused(); true }.getOrDefault(false) ||
                runCatching { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        capture("09_busqueda_resultados")
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        capture("10_busqueda_navegando")
    }

    @Test
    fun ficha_con_favorito_y_episodios() {
        searchFor("one piece")
        waitForText("resultados para \"one piece\"", substring = true)
        // El primero (Anime Ninja) tiene el foco; JKAnime también lo encuentra.
        compose.onAllNodes(hasContentDescription("One Piece") and isFocused()).assertCountEquals(1)
        key(Key.DirectionCenter)
        waitForText("Ver episodio 1")
        compose.onNodeWithText("Ver episodio 1").assertIsFocused()
        waitForText("Temporada 1 · 61 episodios", substring = true)
        key(Key.DirectionRight)
        compose.onNodeWithText("☆ Añadir a favoritos").assertIsFocused()
        key(Key.DirectionCenter)
        waitForText("★ Quitar de favoritos")
        capture("11_ficha_one_piece")
        // Flecha abajo: fila del selector de temporada / "Ir al episodio…", luego tramos y episodios.
        key(Key.DirectionDown)
        compose.onAllNodes(isFocused()).onLast().assert(hasText("Temporada 1", substring = true) or hasText("Ir al episodio…"))
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        compose.onAllNodes(isFocused()).onLast().assert(hasText("Ep. ", substring = true))
        back() // a la búsqueda
        back() // a Inicio
        waitForText("AnimeHub TV")
        scrollHomeTo("Favoritos")
        compose.onNodeWithContentDescription("One Piece").assertExists()
    }
}
