package com.animehub.tv.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.tv.data.config.IptvListConfig
import com.animehub.tv.data.iptv.IptvRepository
import com.animehub.tv.data.iptv.IptvUrl
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.domain.model.Channel
import com.animehub.tv.domain.model.DirectStream
import com.animehub.tv.domain.model.IptvList
import com.animehub.tv.domain.model.StreamType
import com.animehub.tv.nativeplayer.NativePlaybackFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/** Cómo va la señal del canal que suena. */
enum class Signal { CARGANDO, EN_VIVO, SIN_SENAL }

data class LiveTvState(
    val loading: Boolean = true,
    /** Las listas del menú (sin descargar): se abren una a una. */
    val menu: List<IptvListConfig> = emptyList(),
    /** Listas ya abiertas. */
    val lists: Map<String, IptvList> = emptyMap(),
    /** Lista que se está descargando ahora. */
    val loadingList: String? = null,
    val favoriteChannels: List<Channel> = emptyList(),
    val current: Channel? = null,
    /** De qué lista del menú salió el canal que suena (para abrir el menú ahí). */
    val currentList: String? = null,
    /** Qué señal (calidad) del canal suena: índice en [Channel.variants]. */
    val variant: Int = 0,
    /** Canales entre los que se cambia con ↑/↓ y CH±: la categoría o los favoritos desde donde se eligió. */
    val zap: List<Channel> = emptyList(),
    val signal: Signal = Signal.CARGANDO,
    /** Cambia con cada canal nuevo (para mostrar su cartel un momento). */
    val tick: Int = 0,
    /** Canales que no dieron señal en esta sesión (se atenúan en la lista). */
    val dead: Set<String> = emptySet(),
) {
    val favorites: Set<String> get() = favoriteChannels.mapTo(HashSet()) { it.key }
}

/**
 * TV en vivo: listas M3U (por país, categoría, idioma…) sin repetidos, un reproductor que ya está
 * sonando desde que se entra, favoritos y cambio de canal (CH+/CH−, ↑/↓). El menú es una capa
 * transparente (ver la pantalla).
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
    private val retried = mutableSetOf<String>()
    private val altTried = mutableSetOf<String>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val prefs = settings.current()
            _state.update { it.copy(menu = repository.menu(), favoriteChannels = decodeFavorites(prefs.favoriteChannels)) }
            // Empieza solo: el último canal visto o, la primera vez, el primero de Perú.
            val last = prefs.lastChannel?.let { runCatching { json.decodeFromString<Channel>(it) }.getOrNull() }
            if (last != null) {
                val context = prefs.lastContext
                _state.update { it.copy(loading = false) }
                play(last, listOf(last), context)
                launch { restoreZap(last, context) }
            } else {
                val start = repository.startList()
                _state.update { it.copy(loading = false) }
                start?.channels?.firstOrNull()?.let { play(it, start.channels, "${IptvRepository.START_ID}|") }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(favoriteChannels = decodeFavorites(s.favoriteChannels)) } }
        }
    }

    private fun decodeFavorites(map: Map<String, String>): List<Channel> =
        map.values.mapNotNull { runCatching { json.decodeFromString<Channel>(it) }.getOrNull() }.sortedBy { it.name.lowercase() }

    /** Tras abrir con el último canal: se recupera la lista de donde salió para poder seguir cambiando de canal. */
    private suspend fun restoreZap(channel: Channel, context: String?) {
        val (listId, group) = parseContext(context) ?: return
        val list = if (listId == IptvRepository.START_ID) repository.startList() else repository.list(listId)
        list ?: return
        val zap = if (group.isNullOrEmpty()) list.channels else list.groups.firstOrNull { it.first == group }?.second ?: list.channels
        _state.update { s -> if (s.current?.key == channel.key) s.copy(zap = zap, lists = if (list.id == IptvRepository.START_ID) s.lists else s.lists + (list.id to list)) else s }
    }

    /** Abre una lista del menú (la descarga la primera vez). */
    fun openList(id: String) {
        if (id in _state.value.lists || _state.value.loadingList == id) return
        _state.update { it.copy(loadingList = id) }
        viewModelScope.launch {
            val list = repository.list(id)
            _state.update { s -> s.copy(loadingList = null, lists = if (list != null) s.lists + (id to list) else s.lists) }
        }
    }

    /** @param context "lista|categoría" de donde se eligió (para volver ahí al abrir el menú y al reabrir la app). */
    fun play(channel: Channel, zap: List<Channel>, context: String? = null, variant: Int = 0) {
        val index = variant.coerceIn(0, channel.variants.lastIndex)
        _state.update { it.copy(current = channel, currentList = parseContext(context)?.first ?: it.currentList, variant = index, zap = zap, signal = Signal.CARGANDO, tick = it.tick + 1) }
        viewModelScope.launch { settings.setLastChannel(json.encodeToString(channel), context ?: _state.value.currentList?.let { "$it|" }) }
        retried.remove(channel.key)
        altTried.remove(channel.key)
        val v = channel.variants[index]
        emitStream(v.url, v)
    }

    /** Manda la señal al reproductor y vigila que dé imagen: si en 20 s no hay, se reintenta o se prueba el otro formato. */
    private fun emitStream(url: String, v: com.animehub.tv.domain.model.ChannelVariant) {
        _streams.tryEmit(DirectStream(url, typeOf(url), headers = buildMap {
            v.referer?.let { put("Referer", it) }
            // Las listas de TV esperan un reproductor conocido: sin esta cabecera algunos servidores responden 403.
            put("User-Agent", v.userAgent ?: IptvRepository.USER_AGENT)
        }))
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            delay(SIGNAL_WAIT_MS)
            if (_state.value.signal == Signal.CARGANDO) markDead()
        }
    }

    private fun parseContext(context: String?): Pair<String, String?>? =
        context?.takeIf { it.isNotBlank() }?.let { it.substringBefore('|') to it.substringAfter('|', "").ifEmpty { null } }

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
        play(channel, s.zap, variant = (s.variant + delta).mod(channel.variants.size))
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { settings.toggleFavoriteChannel(channel.key, json.encodeToString(channel)) }
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

    /**
     * La señal falló. Primero una reconexión (si ya estaba sonando), luego el formato alternativo
     * (`.ts` ↔ `.m3u8`); solo si nada de eso da imagen se marca "Sin señal".
     */
    private fun markDead() {
        val s = _state.value
        val channel = s.current ?: return
        val variant = channel.variants.getOrNull(s.variant) ?: return
        if (s.signal == Signal.EN_VIVO && retried.add(channel.key)) {
            _state.update { it.copy(signal = Signal.CARGANDO) }
            emitStream(variant.url, variant)
            return
        }
        val alternate = IptvUrl.swapStream(variant.url)
        if (alternate != null && altTried.add(channel.key)) {
            _state.update { it.copy(signal = Signal.CARGANDO) }
            emitStream(alternate, variant)
            return
        }
        _state.update { it.copy(signal = Signal.SIN_SENAL, dead = it.dead + channel.key) }
    }

    /** Recarga manual de la lista (sin esperar las 6 horas): vuelve a bajarla y abre el menú de nuevo. */
    fun reload() {
        viewModelScope.launch {
            repository.refresh()
            _state.update { it.copy(menu = repository.menu(), lists = emptyMap()) }
        }
    }

    private fun typeOf(url: String) = if (Regex("""\.(mp4|mkv|webm)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)) StreamType.MP4 else StreamType.HLS

    private companion object {
        const val SIGNAL_WAIT_MS = 20_000L
    }
}
