package com.animehub.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {

    val recentSearches: StateFlow<List<String>> =
        library.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastQuery = MutableStateFlow<String?>(null)
    val lastQuery: StateFlow<String?> = _lastQuery.asStateFlow()

    fun search(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        _lastQuery.value = clean
        viewModelScope.launch { library.addSearch(clean) }
    }

    fun clearRecent() = viewModelScope.launch { library.clearSearches() }
}
