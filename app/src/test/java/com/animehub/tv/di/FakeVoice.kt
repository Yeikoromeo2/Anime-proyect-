package com.animehub.tv.di

import android.content.Context
import com.animehub.tv.voice.VoiceEngine
import com.animehub.tv.voice.VoiceSession
import com.animehub.tv.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Voz simulada para las pruebas de interfaz. Sin activar no hay voz dentro de la app (se usa la
 * pantalla del sistema, como en una TV sin servicio de voz); activada, la prueba envía lo que
 * "se escucha" con [emit].
 */
object FakeVoice : VoiceEngine {
    var available = false
    var started = 0
    private val state = MutableStateFlow<VoiceState>(VoiceState.Idle)

    override fun isAvailable(context: Context) = available

    override fun create(context: Context): VoiceSession = object : VoiceSession {
        override val state: StateFlow<VoiceState> = this@FakeVoice.state
        override fun start() {
            started++
            this@FakeVoice.state.value = VoiceState.Listening()
        }
        override fun stop() = Unit
        override fun destroy() = Unit
    }

    fun emit(value: VoiceState) {
        state.value = value
    }

    fun reset() {
        available = false
        started = 0
        state.value = VoiceState.Idle
    }
}
