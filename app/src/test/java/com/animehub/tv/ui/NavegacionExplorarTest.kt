package com.animehub.tv.ui

import androidx.compose.ui.input.key.Key
import javax.inject.Inject
import org.junit.After
import kotlinx.coroutines.runBlocking
import com.animehub.tv.data.settings.SettingsRepository
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
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

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var admin: com.animehub.tv.ui.AdminMode

    /** El sitio elegido en Inicio se guarda en disco: cada prueba lo deja como estaba. */
    @After
    fun tearDown() {
        runBlocking { settings.setHomeSite(null) }
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
        waitForText("2023 · ★ 10", substring = true)
        capture("23_inicio_recomendados")

        key(Key.DirectionDown) // Series nuevas
        waitForFocusedDescription(NEW_SERIES_FIRST)
        waitForText("Series nuevas")

        key(Key.DirectionDown) // Explorar por género
        waitForFocus("Todo el catálogo")
        key(Key.DirectionRight)
        waitForFocus("Acción")
        capture("24_inicio_generos")

        // Elegir un género abre Explorar con ese género y el foco en las portadas.
        key(Key.DirectionCenter)
        waitForText("títulos cargados", substring = true)
        waitForAnyFocus()
        capture("25_explorar_accion")
    }

    @Test
    fun explorar_menu_de_categorias_con_el_control() {
        admin.unlock(com.animehub.tv.ui.AdminMode.PIN) // el menú "Sitio" es del modo administrador
        toHeader()
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

    @Test
    fun elegir_un_solo_sitio_en_inicio_y_explorar_lo_usa() {
        admin.unlock(com.animehub.tv.ui.AdminMode.PIN) // elegir el sitio es del modo administrador
        waitForFocusedDescription(LATEST_FIRST)
        key(Key.DirectionUp) // "Viendo: Todos los sitios · Latanime · Anime Ninja · AnimeFLV · JKAnime"
        waitForFocus("Todos los sitios")
        repeat(4) { key(Key.DirectionRight) }
        waitForFocus("JKAnime")
        key(Key.DirectionCenter)
        // Solo JKAnime: sus capítulos, sus destacados y sus géneros, sin mezclar.
        waitForText("Lo que se acaba de publicar en JKAnime")
        waitForText("Lo que destaca hoy, con una pequeña reseña")
        compose.onAllNodes(hasContentDescription(LATEST_FIRST)).assertCountEquals(0)
        waitForText("Kanojo no Tomodachi")
        capture("29_inicio_solo_jkanime")

        // Explorar empieza en ese sitio, con sus categorías reales.
        toHeaderFromChips()
        key(Key.DirectionCenter)
        waitForText("Todo el catálogo · JKAnime")
        waitForText("Año")
        waitForText("títulos cargados", substring = true)
        capture("30_explorar_jkanime")
    }

    /** Desde la fila de sitios sube a la cabecera y va hasta "Buscar" (el primer botón). */
    private fun toHeaderFromChips() {
        val header = listOf("Explorar", "Historial", "Ajustes")
        fun headerFocused() = header.any { compose.onAllNodes(hasText(it) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        repeat(4) { if (!headerFocused()) key(Key.DirectionUp) }
        repeat(4) {
            if (compose.onAllNodes(hasText("Explorar") and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            key(Key.DirectionLeft)
        }
    }
}
