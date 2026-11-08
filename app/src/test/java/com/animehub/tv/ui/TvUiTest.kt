package com.animehub.tv.ui

import android.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
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

    protected fun capture(name: String) {
        compose.waitForIdle()
        val dir = System.getProperty("animehub.capturasDir") ?: "build/capturas"
        captureScreenRoboImage("$dir/$name.png")
    }
}
