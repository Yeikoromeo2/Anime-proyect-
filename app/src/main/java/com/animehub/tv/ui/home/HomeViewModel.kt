package com.animehub.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.config.ConfigRepository
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.data.db.FavoriteEntity
import com.animehub.tv.data.library.LibraryRepository
import com.animehub.tv.domain.model.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val continueWatching: List<WatchProgress> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val activeSites: List<EffectiveSite> = emptyList(),
    val siteNames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    config: ConfigRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(library.continueWatching, library.favorites, config.sites) { cw, fav, sites ->
        HomeUiState(
            continueWatching = cw,
            favorites = fav,
            activeSites = sites.filter { it.enabled },
            siteNames = sites.associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
