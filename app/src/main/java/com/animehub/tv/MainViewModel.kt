package com.animehub.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.log.ErrorLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Al abrir la app: carga la configuración local y busca una versión nueva en GitHub. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val errorLog: ErrorLogRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            runCatching { configRepository.loadLocal() }
                .onFailure { errorLog.log("Configuración", "No se pudo leer la configuración incluida: ${it.message}") }
            configRepository.refresh()
        }
    }
}
