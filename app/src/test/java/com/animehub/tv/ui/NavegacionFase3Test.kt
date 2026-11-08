package com.animehub.tv.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.speech.RecognizerIntent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fase 3: panel de filtros reales, accesos rápidos, búsqueda multisitio y búsqueda por voz. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NavegacionFase3Test : TvUiTest() {

    @Before
    fun setUp() {
        hilt.inject()
        setUpImages()
    }

    private fun openSearch() {
        key(Key.DirectionCenter) // "Buscar"
        waitForText("Nombre del anime")
    }

    /** Simula que la TV tiene reconocimiento de voz (en Robolectric no hay ninguno instalado). */
    private fun installFakeRecognizer() {
        val pm = shadowOf(compose.activity.packageManager)
        val component = ComponentName("com.google.android.katniss", "com.google.android.katniss.Reconocedor")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    @Test
    fun sin_reconocedor_de_voz_no_hay_microfono_y_el_campo_tiene_el_foco() {
        openSearch()
        compose.onNodeWithText("🎤 Hablar").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun el_microfono_tiene_el_foco_y_lo_dictado_se_busca() {
        installFakeRecognizer()
        openSearch()
        compose.onNodeWithText("🎤 Hablar").assertIsFocused()
        capture("12_busqueda_voz")
        key(Key.DirectionCenter)
        val shadow = shadowOf(compose.activity)
        val request = shadow.nextStartedActivityForResult
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, request.intent.action)
        assertEquals("es-419", request.intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        val spoken = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("one piece"))
        shadow.receiveResult(request.intent, Activity.RESULT_OK, spoken)
        // Anime Ninja encuentra One Piece; Latanime avisa de su verificación de Cloudflare.
        waitForText("1 resultados para \"one piece\"")
        waitForText("Latanime pide una verificación de seguridad", substring = true)
        compose.onNodeWithText("one piece").assertExists()
    }

    @Test
    fun filtrar_latanime_por_genero_y_anio_con_el_panel() {
        openSearch()
        activate("Latanime") // chip de sitio
        waitForText("Estrenos del año") // acceso rápido que Latanime sí permite
        // Con el D-pad: Buscar → Filtros. (Dentro del campo de texto las flechas del simulador
        // mueven el cursor; con el D-pad real de la TV Compose saca el foco del campo.)
        compose.onNode(hasText("Buscar") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionRight)
        compose.onNodeWithText("Filtros").assertIsFocused()
        key(Key.DirectionCenter)
        waitForText("Filtros · Latanime")
        waitForFocus("Ver resultados")
        waitForText("Año")
        activate("2024")
        compose.onAllNodes(hasScrollAction()).filterToOne(hasAnyDescendant(hasText("Ver resultados")))
            .performScrollToNode(hasText("Acción"))
        activate("Acción")
        waitForText("2 filtros activos")
        // Con el D-pad se baja desde los botones a los filtros.
        key(Key.DirectionDown)
        capture("13_panel_filtros")
        back() // Atrás cierra el panel y aplica los filtros
        waitForText("30 resultados para los filtros elegidos")
        compose.onNodeWithText("Filtros (2)").assertExists()
        compose.onNodeWithText("Filtros: Acción · 2024").assertExists()
        capture("14_resultados_filtrados")
        activate("Limpiar")
        waitForText("Filtros")
    }

    @Test
    fun en_todos_los_sitios_el_anio_omite_a_anime_ninja_con_aviso() {
        openSearch()
        activate("Filtros")
        waitForText("Filtros · Todos los sitios")
        // Grupos comunes: Tipo, Estado, Año y Género (Letra es solo de Latanime y no aparece).
        waitForText("Tipo")
        waitForText("Año")
        compose.onNodeWithText("Letra").assertDoesNotExist()
        activate("2024")
        waitForText("1 filtros activos")
        activate("Ver resultados")
        waitForText("Anime Ninja no tiene filtro de Año: se omitió", substring = true)
        waitForText("resultados para los filtros elegidos", substring = true)
    }
}
