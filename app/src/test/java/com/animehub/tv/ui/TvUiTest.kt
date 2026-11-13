package com.animehub.tv.ui

import android.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.ColorImage
import com.animehub.tv.MainActivity
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule

/**
 * Base de las pruebas de interfaz: app real en una TV 1080p simulada, manejada solo con
 * teclas del control. Las portadas se sustituyen por colores (no hay red en las pruebas).
 */
abstract class TvUiTest {

    companion object {
        /** Primer capítulo de "Últimos capítulos" con los HTML de prueba (el primero de Latanime). */
        const val LATEST_FIRST = "Tensei Kizoku, Kantei Skill de Nariagaru"
        const val NEW_SERIES_FIRST = "Tensei Kizoku, Kantei Skill de Nariagaru S1 Latino"
    }

    init {
        // La animación de entrada tarda ~2 s: las pruebas de navegación no la esperan.
        com.animehub.tv.ui.home.Intro.enabled = false
    }

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    protected fun setUpImages() {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(compose.activity)
                .components {
                    add(Interceptor { chain ->
                        val hue = (chain.request.data.toString().hashCode() and 0x7fffffff) % 360
                        val color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.75f))
                        SuccessResult(image = ColorImage(color), request = chain.request, dataSource = DataSource.MEMORY)
                    })
                }
                .build(),
        )
    }

    /** Tecla del control sobre el elemento enfocado de la ventana superior (diálogo si lo hay). */
    protected fun key(k: Key) {
        compose.onAllNodes(isFocused()).onLast().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    /** Tecla Atrás enviada a la ventana con foco (cierra primero los diálogos). */
    protected fun back() {
        Espresso.pressBack()
        compose.waitForIdle()
    }

    protected fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Espera a que el elemento con ese texto tenga el foco (los datos llegan en otro hilo). */
    protected fun waitForFocus(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Activa un elemento por su texto como lo haría OK sobre él (sin depender de coordenadas). */
    protected fun activate(text: String) {
        waitForText(text)
        compose.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Espera a que el elemento con esa descripción (portada) tenga el foco. */
    protected fun waitForFocusedDescription(description: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasContentDescription(description) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Al abrir la app el foco pasa al capítulo más reciente; desde ahí se sube con la flecha
     * arriba hasta "Explorar" (el primer botón de la cabecera).
     */
    protected fun toHeader() {
        waitForFocusedDescription(LATEST_FIRST)
        repeat(4) {
            if (compose.onAllNodes(hasText("Explorar") and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            key(Key.DirectionUp)
        }
        compose.onNode(hasText("Explorar") and isFocused()).assertExists()
    }

    /** Abre Explorar (que incluye la búsqueda) desde Inicio. */
    protected fun openExplore() {
        toHeader()
        key(Key.DirectionCenter)
        waitForText("Buscar un anime")
    }

    /** Activa una tarjeta por su descripción (portada) como lo haría OK sobre ella. */
    protected fun activateDescription(description: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription(description)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Desplaza la lista principal de Inicio hasta el texto indicado (filas fuera de la pantalla). */
    protected fun scrollHomeTo(text: String) {
        compose.onAllNodes(hasScrollAction()).filterToOne(hasAnyDescendant(hasText("AnimeHub TV")))
            .performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    protected fun capture(name: String) {
        compose.waitForIdle()
        val dir = System.getProperty("animehub.capturasDir") ?: "build/capturas"
        captureScreenRoboImage("$dir/$name.png")
    }
}
