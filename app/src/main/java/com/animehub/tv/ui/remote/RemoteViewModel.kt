package com.animehub.tv.ui.remote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.remote.PlayRequest
import com.animehub.tv.remote.TargetSession
import com.animehub.tv.remote.TargetSessionFactory
import com.animehub.tv.remote.RemoteCommand
import com.animehub.tv.remote.RemoteResult
import com.animehub.tv.remote.RemoteStatus
import com.animehub.tv.ui.navigation.RemoteRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado del envío a la TV. */
sealed interface SendPhase {
    data object Sending : SendPhase
    /** La TV tarda en responder: probablemente está preguntando si se permite este dispositivo. */
    data object WaitingApproval : SendPhase
    data object Connected : SendPhase
    data object Rejected : SendPhase
    data class Failed(val message: String) : SendPhase
}

data class RemoteUiState(
    val phase: SendPhase = SendPhase.Sending,
    val status: RemoteStatus? = null,
    val notice: String? = null,
)

/**
 * Control remoto de la TV desde la tablet: envía el episodio al entrar y luego pregunta el estado
 * cada segundo para mostrar el minuto, la pausa y el servidor.
 */
@HiltViewModel
class RemoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessions: TargetSessionFactory,
    private val settings: SettingsRepository,
) : ViewModel() {

    val route: RemoteRoute = savedStateHandle.toRoute()
    /** TV con AnimeHub, Chromecast o TV DLNA. */
    private val session: TargetSession? = sessions.create(route)
    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()
    private var polling: Job? = null
    var seekSeconds = 10
        private set

    init {
        viewModelScope.launch { seekSeconds = settings.current().seekSeconds }
        send()
    }

    fun send() {
        _state.value = RemoteUiState(SendPhase.Sending)
        viewModelScope.launch {
            val hint = launch {
                delay(APPROVAL_HINT_MS)
                _state.update { if (it.phase == SendPhase.Sending) it.copy(phase = SendPhase.WaitingApproval) else it }
            }
            val result = session?.play(
                PlayRequest(
                    siteId = route.siteId, animeUrl = route.animeUrl, animeTitle = route.animeTitle, coverUrl = route.coverUrl,
                    episodeUrl = route.episodeUrl, episodeNumber = route.episodeNumber, startMs = route.startMs,
                ),
            ) ?: RemoteResult.Failed("Este dispositivo no está disponible")
            hint.cancel()
            _state.update {
                it.copy(
                    phase = when (result) {
                        RemoteResult.Ok, RemoteResult.NothingPlaying -> SendPhase.Connected
                        RemoteResult.Rejected -> SendPhase.Rejected
                        is RemoteResult.Failed -> SendPhase.Failed(result.message)
                    },
                )
            }
            if (result == RemoteResult.Ok) startPolling()
        }
    }

    fun command(type: String, seconds: Int = 0) {
        viewModelScope.launch {
            when (val result = session?.command(RemoteCommand(type, seconds)) ?: RemoteResult.NothingPlaying) {
                RemoteResult.Ok -> refresh()
                RemoteResult.NothingPlaying -> _state.update { it.copy(notice = "La TV no está reproduciendo nada") }
                RemoteResult.Rejected -> _state.update { it.copy(phase = SendPhase.Rejected) }
                is RemoteResult.Failed -> _state.update { it.copy(notice = result.message) }
            }
        }
    }

    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun refresh() {
        val status = session?.status()
        _state.update { it.copy(status = status ?: it.status, notice = if (status == null) "Sin respuesta de ${route.tvName}" else null) }
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val APPROVAL_HINT_MS = 2_500L
    }
}
