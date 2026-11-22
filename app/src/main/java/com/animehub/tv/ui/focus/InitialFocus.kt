package com.animehub.tv.ui.focus

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.withTimeoutOrNull

/** Recuerda si la pantalla ya dio su foco inicial, para no "robar" el foco después. */
@Stable
class InitialFocusState {
    var done by mutableStateOf(false)
        internal set
}

@androidx.compose.runtime.Composable
fun rememberInitialFocusState(): InitialFocusState = remember { InitialFocusState() }

/**
 * Da el foco a este elemento cuando aparece. Reintenta durante unos segundos porque los
 * diálogos (otra ventana), las listas que esperan datos y las transiciones entre pantallas
 * no están listos en el primer intento.
 * Solo actúa una vez por [state].
 */
fun Modifier.initialFocus(state: InitialFocusState): Modifier = composed {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        // Reintenta en cada fotograma durante unos segundos (la pantalla anterior puede seguir
        // animando su salida y retener el foco un momento).
        withTimeoutOrNull(MAX_WAIT_MS) {
            var frames = 0
            while (!state.done && frames < MAX_FRAMES) {
                runCatching { requester.requestFocus() }
                withFrameNanos { }
                if (focused) state.done = true
                frames++
            }
        }
    }
    focusRequester(requester).onFocusChanged {
        focused = it.isFocused
        if (it.isFocused) state.done = true
    }
}

/** Variante para diálogos y elementos que se muestran una sola vez. */
fun Modifier.initialFocus(): Modifier = composed { initialFocus(remember { InitialFocusState() }) }

private const val MAX_WAIT_MS = 3_000L

/** Tope de intentos (≈ 3 s a 60 fps) para no quedar en bucle si el reloj no avanza. */
private const val MAX_FRAMES = 180
