package com.animehub.tv.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.iptv.IptvRepository
import com.animehub.tv.data.settings.PreferredQuality
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.domain.model.Channel
import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.IptvList
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.nativeplayer.NativePlaybackFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/** Cómo va la señal del canal que suena. */
enum class Signal { CARGANDO, EN_VIVO, SIN_SENAL }

data class LiveTvState(
    val loading: Boolean = true,
    val lists: List<IptvList> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val current: Channel? = null,
    /** Qué señal (calidad) del canal suena: índice en [Channel.variants]. */
    val variant: Int = 0,
    /** Canales entre los que se cambia con ↑/↓ y CH+/CH−: la categoría o los favoritos desde donde se eligió. */
    val zap: List<Channel> = emptyList(),
    val signal: Signal = Signal.CARGANDO,
    /** Cambia con cada canal nuevo (para mostrar su cartel un momento). */
    val tick: Int = 0,
    /** Canales que no dieron señal en esta sesión (se atenúan en la lista). */
    val dead: Set<String> = emptySet(),
) {
    val favoriteChannels: List<Channel>
        get() = lists.flatMap { it.channels }.filter { it.key in favorites }
}

/**
 * TV en vivo: listas M3U sin repetidos, un reproductor que ya está sonando desde que se entra,
 * favoritos y cambio de canal (CH+/CH−, ↑/↓). El menú es una capa transparente (ver la pantalla).
 */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val settings: SettingsRepository,
    val nativeFactory: NativePlaybackFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    private val _streams = MutableSharedFlow<DirectStream?>(replay = 1, extraBufferCapacity = 4)
    /** Qué reproducir ahora (null = parar). Se repite al último para quien empiece a escuchar tarde. */
    val streams: SharedFlow<DirectStream?> = _streams.asSharedFlow()

    private var watchdog: Job? = null

    init {
        viewModelScope.launch {
            val lists = repository.lists()
            val prefs = settings.current()
            _state.update { it.copy(loading = false, lists = lists, favorites = prefs.favoriteChannels) }
            // Empieza solo: el último canal visto o, si no hay, el primero de la primera lista con canales.
            // Con ↑/↓ y CH± se cambia entre todos los canales de esa lista.
            val last = prefs.lastChannel?.let { key -> lists.firstNotNullOfOrNull { l -> l.channels.firstOrNull { it.key == key }?.let { it to l.channels } } }
            val start = last ?: lists.firstOrNull { it.channels.isNotEmpty() }?.let { it.channels.first() to it.channels }
            start?.let { (channel, zap) -> play(channel, zap) }
        }
        viewModelScope.launch { settings.settings.collect { s -> _state.update { it.copy(favorites = s.favoriteChannels) } } }
    }

    fun play(channel: Channel, zap: List<Channel>, variant: Int = 0) {
        val index = variant.coerceIn(0, channel.variants.lastIndex)
        _state.update { it.copy(current = channel, variant = index, zap = zap, signal = Signal.CARGANDO, tick = it.tick + 1) }
        viewModelScope.launch { settings.setLastChannel(channel.key) }
        val v = channel.variants[index]
        _streams.tryEmit(DirectStream(v.url, typeOf(v.url), headers = buildMap { v.referer?.let { put("Referer", it) }; v.userAgent?.let { put("User-Agent", it) } }))
        // Si en 20 s no hay imagen se muestra "Sin señal" (sin ruido técnico) y se puede seguir cambiando.
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            delay(SIGNAL_WAIT_MS)
            if (_state.value.signal == Signal.CARGANDO) markDead()
        }
    }

    /** Siguiente (+1) o anterior (−1) canal de la lista actual; da la vuelta al llegar al final. */
    fun zap(delta: Int) {
        val s = _state.value
        val list = s.zap.ifEmpty { return }
        val i = list.indexOfFirst { it.key == s.current?.key }
        val next = list[((if (i < 0) 0 else i) + delta).mod(list.size)]
        play(next, list)
    }

    /** Cambia a otra señal del mismo canal (HD ↔ Full HD…). */
    fun cycleQuality(delta: Int) {
        val s = _state.value
        val channel = s.current ?: return
        if (channel.variants.size < 2) return
        play(channel, s.zap, (s.variant + delta).mod(channel.variants.size))
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { settings.toggleFavoriteChannel(channel.key) }
    }

    /** Mensajes del reproductor (los mismos que los del reproductor de video de anime). */
    fun onPlayerMessage(raw: String) {
        val type = runCatching { Json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content }.getOrNull() ?: return
        when (type) {
            "playing", "progress" -> if (_state.value.signal != Signal.EN_VIVO) {
                watchdog?.cancel()
                _state.update { it.copy(signal = Signal.EN_VIVO) }
            }
            "error" -> markDead()
        }
    }

    private fun markDead() {
        val key = _state.value.current?.key ?: return
        _state.update { it.copy(signal = Signal.SIN_SENAL, dead = it.dead + key) }
    }

    private fun typeOf(url: String) = if (Regex("""\.(mp4|mkv|webm)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)) StreamType.MP4 else StreamType.HLS

    fun quality(): PreferredQuality = PreferredQuality.AUTO

    private companion object {
        const val SIGNAL_WAIT_MS = 20_000L
    }
}
