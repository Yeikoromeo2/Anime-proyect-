package com.animehub.tv.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.domain.model.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val items: List<WatchProgress> = emptyList(),
    val siteNames: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val library: LibraryRepository,
    config: ConfigRepository,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> = combine(library.history, config.sites) { items, sites ->
        HistoryUiState(items, sites.associate { it.id to it.name }, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun delete(item: WatchProgress) = viewModelScope.launch { library.deleteHistoryItem(item.siteId, item.episodeUrl) }

    fun clearAll() = viewModelScope.launch { library.clearHistory() }
}
