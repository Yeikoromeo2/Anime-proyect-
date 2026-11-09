package com.animehub.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Inicio con recomendados (con reseña) y series nuevas, y Explorar el catálogo por categorías. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionExplorarTest : TvUiTest() {

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
    }

    private fun waitForAnyFocus() = compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun recomendados_con_resena_series_nuevas_y_generos_en_inicio() {
        waitForFocusedDescription(LATEST_FIRST)
        key(Key.DirectionDown) // Recomendados: alterna sitios (Latanime trae la reseña en su portada)
        waitForFocusedDescription("Black Clover Latino")
        waitForText("Asta y Yuno son huérfanos", substring = true)
        key(Key.DirectionRight) // Anime Ninja no la trae: se toma de la ficha
        waitForFocusedDescription("Jigokuraku")
        waitForText("Ante la posibilidad de la absolución", substring = true)
        waitForText("Anime Ninja · 2023 · ★ 10")
        capture("23_inicio_recomendados")

        key(Key.DirectionDown) // Series nuevas
        waitForFocusedDescription(LATEST_FIRST)
        waitForText("Series nuevas")

        key(Key.DirectionDown) // Explorar por género
        waitForFocus("Todo el catálogo")
        key(Key.DirectionRight)
        waitForFocus("Acción")
        capture("24_inicio_generos")

        // Elegir un género abre Explorar con ese género y el foco en las portadas.
        key(Key.DirectionCenter)
        waitForText("Acción · Todos los sitios")
        waitForText("títulos cargados", substring = true)
        waitForAnyFocus()
        capture("25_explorar_accion")
    }

    @Test
    fun explorar_menu_de_categorias_con_el_control() {
        toHeader()
        key(Key.DirectionRight)
        waitForFocus("Explorar")
        key(Key.DirectionCenter)
        // Sin filtros: todo el catálogo de todos los sitios; el foco empieza en el menú.
        waitForText("Todo el catálogo · Todos los sitios")
        waitForFocus("Sitio")
        waitForText("títulos cargados", substring = true)
        key(Key.DirectionDown)
        waitForFocus("Todo el catálogo")
        key(Key.DirectionDown)
        waitForFocus("Género")
        capture("26_explorar_menu")

        key(Key.DirectionCenter) // lista de géneros reales, con "Todos" marcado
        waitForFocus("✓ Todos")
        key(Key.DirectionDown)
        waitForFocus("Acción")
        capture("27_explorar_lista_generos")
        key(Key.DirectionCenter)
        waitForText("Acción · Todos los sitios")
        waitForText("títulos cargados", substring = true)

        // Cambiar de sitio: aparecen sus propias categorías (Latanime: año, categoría, letra…).
        activate("Sitio")
        waitForFocus("✓ Todos los sitios")
        key(Key.DirectionDown)
        waitForFocus("Latanime")
        key(Key.DirectionCenter)
        waitForText("Todo el catálogo · Latanime")
        waitForText("Año")
        waitForText("Letra")
        waitForText("títulos cargados", substring = true)
        capture("28_explorar_latanime")
    }
}
