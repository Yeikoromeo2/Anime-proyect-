package com.animehub.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.domain.model.WatchProgress
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * Recorre la app solo con las teclas del control remoto (flechas, OK y Atrás) en una pantalla
 * de TV 1080p simulada. Con `-Pcapturas` guarda capturas en app/build/capturas.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionTvTest : TvUiTest() {

    @Inject lateinit var library: LibraryRepository

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
    }

    @Test
    fun inicio_muestra_de_frente_los_capitulos_nuevos_y_arriba_la_cabecera() {
        // Al abrir, el foco pasa solo al capítulo más reciente de "Últimos capítulos".
        waitForText("Últimos capítulos")
        waitForFocusedDescription(LATEST_FIRST)
        capture("01_inicio")
        key(Key.DirectionUp)
        compose.onNodeWithText("Buscar").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Explorar").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Historial").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Ajustes").assertIsFocused()
    }

    @Test
    fun las_filas_de_inicio_se_alcanzan_con_flecha_abajo() {
        runBlocking {
            listOf("Frieren", "Dandadan", "Kaiju No. 8").forEachIndexed { i, title ->
                library.saveProgress(
                    WatchProgress(
                        siteId = "latanime", animeUrl = "https://latanime.org/anime/$i", animeTitle = title, coverUrl = null,
                        episodeNumber = (i + 3).toFloat(), episodeUrl = "https://latanime.org/ver/$i-episodio-${i + 3}",
                        serverName = "voe", positionMs = 600_000L + i * 120_000L, durationMs = 1_440_000L,
                        updatedAt = 1_000L + i, completed = false,
                    ),
                )
            }
            library.setFavorite("animeninja", "https://veranime.ninja/anime/one-piece/", "One Piece", null, true)
        }
        // Orden: Últimos capítulos → Continuar viendo → Favoritos → Recomendados → Series nuevas → Géneros.
        waitForFocusedDescription(LATEST_FIRST)
        key(Key.DirectionDown) // Continuar viendo: el más reciente primero
        compose.onNodeWithContentDescription("Kaiju No. 8").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithContentDescription("Dandadan").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithContentDescription("One Piece").assertIsFocused()
        capture("02_inicio_con_filas")
        key(Key.DirectionDown) // Recomendados
        waitForFocusedDescription("Black Clover Latino")
        key(Key.DirectionDown) // Series nuevas
        waitForFocusedDescription(LATEST_FIRST)
        key(Key.DirectionDown) // Explorar por género
        waitForFocus("Todo el catálogo")
    }

    @Test
    fun ajustes_se_abren_y_se_vuelve_con_atras() {
        toHeader()
        key(Key.DirectionRight) // Explorar
        key(Key.DirectionRight) // Historial
        key(Key.DirectionRight) // Ajustes
        key(Key.DirectionCenter)
        waitForText("Latanime")
        // Foco inicial en el primer sitio.
        compose.onNodeWithText("Latanime").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithText("Anime Ninja").assertIsFocused()
        capture("03_ajustes")
        key(Key.DirectionCenter)
        compose.onNodeWithText("Desactivar sitio").assertIsFocused()
        capture("04_ajustes_opciones_sitio")
        back() // cierra el diálogo
        compose.onNodeWithText("Anime Ninja").assertIsFocused()
        back() // vuelve a Inicio
        compose.onNodeWithText("Tus sitios de anime en un solo lugar").assertExists()
    }

    @Test
    fun atras_en_inicio_pide_confirmacion_y_cancelar_tiene_el_foco() {
        compose.waitForIdle()
        back()
        compose.onNodeWithText("¿Salir de AnimeHub TV?").assertExists()
        compose.onNodeWithText("Cancelar").assertIsFocused()
        capture("05_confirmar_salida")
        key(Key.DirectionCenter)
        compose.onNodeWithText("¿Salir de AnimeHub TV?").assertDoesNotExist()
        assertTrue(!compose.activity.isFinishing)
    }

    @Test
    fun busqueda_e_historial_tienen_foco_inicial() {
        toHeader()
        key(Key.DirectionCenter) // Buscar
        compose.onNodeWithText("Nombre del anime").assertExists()
        capture("06_busqueda")
        back()
        // Al volver, el foco se queda en "Buscar" (no salta otra vez a los capítulos).
        waitForFocus("Buscar")
        key(Key.DirectionRight) // Explorar
        key(Key.DirectionRight) // Historial
        waitForFocus("Historial")
        key(Key.DirectionCenter)
        waitForFocus("Borrar todo")
        capture("07_historial_vacio")
    }
}
