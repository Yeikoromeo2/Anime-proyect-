package com.animehub.tv.ui

import android.Manifest
import com.animehub.tv.di.FakeVoice
import com.animehub.tv.voice.VoiceState
import org.junit.Assert.assertNull

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
        FakeVoice.reset()
    }

    private fun openSearch() = openExplore() // la búsqueda vive dentro de Explorar

    /** Simula que la TV tiene reconocimiento de voz (en Robolectric no hay ninguno instalado). */
    private fun installFakeRecognizer() {
        val pm = shadowOf(compose.activity.packageManager)
        val component = ComponentName("com.google.android.katniss", "com.google.android.katniss.Reconocedor")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    @Test
    fun sin_reconocedor_de_voz_no_hay_microfono() {
        openSearch()
        compose.onNodeWithText("🎤").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertExists()
    }

    @Test
    fun el_microfono_pequeno_escucha_al_pulsarlo_y_lo_dictado_se_busca() {
        installFakeRecognizer()
        openSearch()
        capture("12_busqueda_voz")
        activate("🎤")
        val shadow = shadowOf(compose.activity)
        val request = shadow.nextStartedActivityForResult
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, request.intent.action)
        assertEquals("es-419", request.intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        val spoken = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("one piece"))
        shadow.receiveResult(request.intent, Activity.RESULT_OK, spoken)
        waitForText("“one piece”")
        waitForText("títulos cargados", substring = true)
    }

    @Test
    fun voz_dentro_de_la_app_sin_abrir_el_asistente_de_la_tv() {
        FakeVoice.available = true
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        openSearch()
        activate("🎤")
        // Panel propio de la app (no la pantalla del asistente, que tapaba todo).
        waitForText("Te escucho…")
        compose.runOnUiThread { FakeVoice.emit(VoiceState.Listening(level = 0.8f, partial = "one pi")) }
        waitForText("“one pi”")
        capture("35_voz_en_la_app")
        compose.runOnUiThread { FakeVoice.emit(VoiceState.Result("one piece")) }
        waitForText("“one piece”")
        compose.onNodeWithText("Te escucho…").assertDoesNotExist()
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        assertEquals(1, FakeVoice.started)
    }
}
