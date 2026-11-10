package com.animehub.tv.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Dónde se reproduce desde la tablet: aquí (null) o en una TV de la red. Se recuerda mientras la app está abierta. */
@Singleton
class CastTarget @Inject constructor() {
    private val _selected = MutableStateFlow<RemoteTv?>(null)
    val selected: StateFlow<RemoteTv?> = _selected.asStateFlow()

    fun select(tv: RemoteTv?) {
        _selected.value = tv
    }
}
