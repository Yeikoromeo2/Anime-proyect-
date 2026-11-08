package com.animehub.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
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
    fun inicio_tiene_foco_inicial_y_se_recorre_con_las_flechas() {
        compose.waitForIdle()
        compose.onNodeWithText("Buscar").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Historial").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Ajustes").assertIsFocused()
        capture("01_inicio")
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
        waitForText("Continuar viendo")
        waitForText("Favoritos")
        compose.onNodeWithText("Buscar").assertIsFocused()
        key(Key.DirectionDown)
        // El más reciente va primero.
        compose.onNodeWithContentDescription("Kaiju No. 8").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithContentDescription("Dandadan").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithContentDescription("One Piece").assertIsFocused()
        capture("02_inicio_con_filas")
    }

    @Test
    fun ajustes_se_abren_y_se_vuelve_con_atras() {
        compose.waitForIdle()
        key(Key.DirectionRight)
        key(Key.DirectionRight)
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
        compose.waitForIdle()
        key(Key.DirectionCenter) // Buscar
        compose.onNodeWithText("Nombre del anime").assertExists()
        capture("06_busqueda")
        back()
        compose.onNodeWithText("Buscar").assertIsFocused()
        key(Key.DirectionRight)
        key(Key.DirectionCenter) // Historial
        compose.onNodeWithText("Borrar todo").assertIsFocused()
        capture("07_historial_vacio")
    }
}
