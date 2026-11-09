package com.animehub.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToNode
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

/** Ficha de una serie larga (One Piece): desplegable de temporadas, tramos e "Ir al episodio". */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionFichaTest : TvUiTest() {

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
    }

    private fun openOnePiece() {
        toHeader()
        key(Key.DirectionCenter)
        waitForText("Nombre del anime")
        compose.onNode(hasSetTextAction()).performTextInput("one piece")
        compose.onNode(hasSetTextAction()).performImeAction()
        // Anime Ninja y JKAnime lo encuentran; el primero (alternando sitios) es el de Anime Ninja.
        waitForText("resultados para \"one piece\"", substring = true)
        key(Key.DirectionCenter)
        waitForText("Temporada 1 · 61 episodios  ▾")
    }

    @Test
    fun elegir_temporada_en_el_desplegable() {
        openOnePiece()
        activate("Temporada 1 · 61 episodios  ▾")
        waitForText("Elegir temporada")
        waitForFocus("✓ Temporada 1 · 61 episodios")
        capture("16_desplegable_temporadas")
        // En la TV se baja con el D-pad; aquí se desplaza la lista del desplegable.
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Temporada 21 · 197 episodios"))
        activate("Temporada 21 · 197 episodios")
        // La temporada larga se divide en tramos y el foco va a su primer episodio.
        waitForText("Temporada 21 · 197 episodios  ▾")
        waitForText("892–941")
        waitForText("1042–1088")
        waitForFocus("Ep. 892")
        capture("17_temporada_21_tramos")
    }

    @Test
    fun ir_directo_al_episodio_1015() {
        openOnePiece()
        activate("Ir al episodio…")
        waitForText("Elige el número con el control", substring = true)
        // Con el D-pad sobre el teclado numérico (1 2 3 / 4 5 6 / 7 8 9 / ⌫ 0 Ir): 1 0 1 5.
        waitForFocus("1")
        key(Key.DirectionCenter) // 1
        repeat(3) { key(Key.DirectionDown) }; key(Key.DirectionRight); key(Key.DirectionCenter) // 0
        repeat(3) { key(Key.DirectionUp) }; key(Key.DirectionLeft); key(Key.DirectionCenter) // 1
        key(Key.DirectionDown); key(Key.DirectionRight); key(Key.DirectionCenter) // 5
        waitForText("1015")
        capture("18_teclado_numerico")
        activate("Ir")
        // La lista salta a la temporada 21, tramo 992–1041, y el foco queda en el episodio 1015.
        waitForFocus("Ep. 1015")
        capture("19_ir_al_episodio_1015")
    }
}
