package com.animehub.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.config.ConfigParser
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.ConfigState
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.log.ErrorEntry
import com.animehub.tv.data.log.ErrorLogRepository
import com.animehub.tv.data.settings.AppSettings
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.data.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val sites: List<EffectiveSite> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val config: ConfigState? = null,
    val errors: List<ErrorEntry> = emptyList(),
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Running : UpdateStatus
    data class Done(val version: Int) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val configRepository: ConfigRepository,
    private val errorLog: ErrorLogRepository,
    /** Actualización de la app desde Releases. */
    val appUpdates: UpdateManager,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        configRepository.sites, settingsRepository.settings, configRepository.state, errorLog.entries,
    ) { sites, settings, config, errors -> SettingsUiState(sites, settings, config, errors) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val update: StateFlow<UpdateStatus> = _update.asStateFlow()

    fun setSiteEnabled(site: EffectiveSite, enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSiteEnabled(site.id, enabled)
    }

    /** Devuelve un mensaje de error si el dominio no es válido. */
    fun setDomain(site: EffectiveSite, value: String): String? {
        if (!ConfigParser.isHttpUrl(value)) return "Escribe un dominio completo, por ejemplo https://sitio.com"
        viewModelScope.launch { settingsRepository.setDomainOverride(site.id, ConfigParser.normalizeBaseUrl(value)) }
        return null
    }

    fun resetDomain(site: EffectiveSite) = viewModelScope.launch { settingsRepository.setDomainOverride(site.id, null) }

    fun setRemoteUrl(value: String): String? {
        if (!ConfigParser.isHttpUrl(value)) return "La URL debe empezar por https://"
        viewModelScope.launch { settingsRepository.setRemoteConfigUrl(value) }
        return null
    }

    fun resetRemoteUrl() = viewModelScope.launch { settingsRepository.resetRemoteConfigUrl() }

    fun refreshConfig() {
        if (_update.value == UpdateStatus.Running) return
        _update.value = UpdateStatus.Running
        viewModelScope.launch {
            _update.value = configRepository.refresh().fold(
                onSuccess = { UpdateStatus.Done(it.config.version) },
                onFailure = { UpdateStatus.Failed(it.message ?: "Error desconocido") },
            )
        }
    }

    fun toggleSeek() = viewModelScope.launch {
        settingsRepository.setSeekSeconds(if (state.value.settings.seekSeconds == 10) 30 else 10)
    }

    fun nextQuality() = viewModelScope.launch {
        settingsRepository.setPreferredQuality(state.value.settings.preferredQuality.next())
    }

    fun clearPreferredServers() = viewModelScope.launch { settingsRepository.clearPreferredServers() }

    fun clearErrors() = errorLog.clear()
}
