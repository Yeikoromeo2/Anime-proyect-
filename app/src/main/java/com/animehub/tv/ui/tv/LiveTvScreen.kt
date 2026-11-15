package com.animehub.tv.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.animehub.tv.data.settings.PreferredQuality
import com.animehub.tv.domain.model.Channel
import com.animehub.tv.ui.components.ListItem
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.components.SecondaryText
import com.animehub.tv.ui.components.listItemBorder
import com.animehub.tv.ui.components.listItemColors
import com.animehub.tv.ui.components.listItemScale
import com.animehub.tv.ui.focus.InitialFocusState
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.focus.rememberInitialFocusState
import com.animehub.tv.ui.theme.AppColors
import kotlinx.coroutines.delay

private const val FAVORITES = "__favoritos"

private fun plural(n: Int, onDemand: Boolean) = if (onDemand) (if (n == 1) "1 título" else "$n títulos") else (if (n == 1) "1 canal" else "$n canales")

/**
 * TV en vivo a pantalla completa: el canal suena desde que se entra y el menú es una capa
 * transparente encima (se ve la imagen a través) que se esconde sola.
 * - Con el menú cerrado: CH+/CH− o ↑/↓ cambian de canal, ←/→ cambian de calidad, OK abre el menú.
 * - Menú: Listas → categorías → canales (la estrella marca favoritos). Atrás sube un nivel y luego cierra.
 */
@Composable
fun LiveTvScreen(onExit: () -> Unit, viewModel: LiveTvViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val native = remember { viewModel.nativeFactory.create(context, PreferredQuality.AUTO, viewModel::onPlayerMessage) }
    DisposableEffect(native) { onDispose { native.release() } }
    LaunchedEffect(native) { viewModel.streams.collect { stream -> if (stream == null) native.stop() else native.play(stream) } }

    var menuOpen by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf(listOf<String>()) }
    var interaction by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    // El foco empieza en la pantalla (reintenta mientras entra): así las teclas de canal funcionan de inmediato.
    val startFocus = rememberInitialFocusState()

    fun openMenu() {
        // Se abre en la categoría del canal que suena, con el foco en él.
        val current = state.current
        path = when {
            current == null -> emptyList()
            state.zap === state.favoriteChannels || (state.zap.isNotEmpty() && state.zap.map { it.key } == state.favoriteChannels.map { it.key }) -> listOf(FAVORITES)
            else -> state.lists.firstNotNullOfOrNull { l -> l.groups.firstOrNull { (_, ch) -> ch.any { it.key == current.key } }?.let { (g, _) -> listOf(l.id, g) } } ?: emptyList()
        }
        menuOpen = true
        interaction++
    }

    // Capa transparente: se esconde sola a los 10 s sin tocar nada.
    LaunchedEffect(menuOpen, interaction) { if (menuOpen) { delay(LiveTv.menuIdleMs); menuOpen = false } }
    LaunchedEffect(menuOpen) { if (!menuOpen) runCatching { rootFocus.requestFocus() } }

    // Cartel del canal: un momento al cambiar y mientras no hay señal.
    var banner by remember { mutableStateOf(false) }
    LaunchedEffect(state.tick, state.signal) {
        banner = true
        if (state.signal == Signal.EN_VIVO) delay(BANNER_MS) else delay(BANNER_MS * 3)
        banner = false
    }

    // Atrás: sube un nivel del menú, luego lo cierra y, con el menú cerrado, sale de la TV en vivo.
    fun goBack() {
        when {
            menuOpen && path.isNotEmpty() -> { path = path.dropLast(1); interaction++ }
            menuOpen -> menuOpen = false
            else -> onExit()
        }
    }
    BackHandler { goBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .initialFocus(startFocus)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // La tecla Atrás del control llega aquí primero; el gesto de Atrás de la tablet, por BackHandler.
                if (event.key == Key.Back) { goBack(); return@onPreviewKeyEvent true }
                if (menuOpen) { interaction++; return@onPreviewKeyEvent false }
                when (event.key) {
                    Key.ChannelUp, Key.DirectionUp, Key.PageUp -> viewModel.zap(1)
                    Key.ChannelDown, Key.DirectionDown, Key.PageDown -> viewModel.zap(-1)
                    Key.DirectionLeft -> viewModel.cycleQuality(-1)
                    Key.DirectionRight -> viewModel.cycleQuality(1)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu, Key.Spacebar -> openMenu()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        native.view?.let { view ->
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        }
        // Tocar la pantalla (tablet) muestra u oculta el menú.
        Box(Modifier.fillMaxSize().pointerInput(menuOpen) { detectTapGestures(onTap = { if (menuOpen) menuOpen = false else openMenu() }) })

        AnimatedVisibility(visible = banner && !menuOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
            Banner(state)
        }

        if (state.loading) {
            Text("Preparando los canales…", modifier = Modifier.align(Alignment.Center), color = AppColors.TextoSecundario)
        } else if (state.lists.all { it.channels.isEmpty() }) {
            Text("Todavía no hay canales para mostrar.", modifier = Modifier.align(Alignment.Center), color = AppColors.TextoSecundario)
        }

        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn() + slideInHorizontally { -it / 3 },
            exit = fadeOut() + slideOutHorizontally { -it / 3 },
        ) {
            Menu(
                state = state,
                path = path,
                onPath = { path = it; interaction++ },
                onPlay = { channel, zap -> viewModel.play(channel, zap); menuOpen = false },
                onFavorite = { viewModel.toggleFavorite(it); interaction++ },
                onClose = { menuOpen = false },
            )
        }
    }
}

/** Cartel con el canal que suena: logo, nombre, categoría, calidad y si es favorito. */
@Composable
private fun Banner(state: LiveTvState) {
    val channel = state.current ?: return
    Row(
        Modifier
            .padding(28.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Logo(channel, 56)
        Column {
            Text(channel.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    channel.group,
                    channel.variants.getOrNull(state.variant)?.label?.takeIf { channel.variants.size > 1 || it != "Auto" },
                    "★".takeIf { channel.key in state.favorites },
                ).joinToString(" · "),
                color = AppColors.TextoSecundario,
            )
            when (state.signal) {
                Signal.CARGANDO -> Text("Cargando…", color = AppColors.Acento2)
                Signal.SIN_SENAL -> Text("Sin señal · ↑ ↓ para cambiar de canal", color = AppColors.Aviso)
                Signal.EN_VIVO -> Unit
            }
        }
    }
}

/** La capa del menú: transparente, a la izquierda, sin tapar el centro de la pantalla. */
@Composable
private fun Menu(
    state: LiveTvState,
    path: List<String>,
    onPath: (List<String>) -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    onFavorite: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(470.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.34f), Color.Transparent)))
                .padding(start = 28.dp, end = 12.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val list = state.lists.firstOrNull { it.id == path.firstOrNull() }
            Text(
                when {
                    path.firstOrNull() == FAVORITES -> "★ Favoritos"
                    list != null && path.size > 1 -> "${list.name} · ${path[1]}"
                    list != null -> list.name
                    else -> "TV en vivo"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            val focus = remember(path) { InitialFocusState() }
            val listState = rememberLazyListState()
            // Al abrir una categoría la lista se acerca al canal que suena.
            val channelsHere = if (list != null && path.size > 1) list.groups.firstOrNull { it.first == path[1] }?.second.orEmpty() else emptyList()
            val soundingAt = channelsHere.indexOfFirst { it.key == state.current?.key }
            LaunchedEffect(path) { if (soundingAt > 2) listState.scrollToItem(soundingAt - 2) }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                when {
                    path.isEmpty() -> {
                        val favorites = state.favoriteChannels
                        item(key = FAVORITES) {
                            Entry("★ Favoritos", if (favorites.isEmpty()) "Marca canales con la estrella" else plural(favorites.size, false), Modifier.initialFocus(focus)) { onPath(listOf(FAVORITES)) }
                        }
                        items(state.lists.filter { it.channels.isNotEmpty() }, key = { it.id }) { l ->
                            Entry(l.name, plural(l.channels.size, l.onDemand)) { onPath(listOf(l.id)) }
                        }
                    }
                    path.first() == FAVORITES -> {
                        val favorites = state.favoriteChannels
                        if (favorites.isEmpty()) item { Text("Aún no tienes favoritos. Abre una lista y toca la estrella.", color = AppColors.TextoSecundario) }
                        items(favorites, key = { it.key }) { c ->
                            ChannelRow(c, state, if (c.key == state.current?.key) Modifier.initialFocus(focus) else Modifier, onFavorite) { onPlay(c, favorites) }
                        }
                    }
                    path.size == 1 && list != null -> items(list.groups, key = { it.first }) { (group, channels) ->
                        Entry(group, plural(channels.size, list.onDemand), if (group == list.groups.first().first) Modifier.initialFocus(focus) else Modifier) { onPath(listOf(list.id, group)) }
                    }
                    list != null -> {
                        val channels = list.groups.firstOrNull { it.first == path[1] }?.second.orEmpty()
                        val currentIndex = channels.indexOfFirst { it.key == state.current?.key }
                        items(channels, key = { it.key }) { c ->
                            val first = if (currentIndex >= 0) c.key == state.current?.key else c.key == channels.first().key
                            ChannelRow(c, state, if (first) Modifier.initialFocus(focus) else Modifier, onFavorite) { onPlay(c, channels) }
                        }
                    }
                }
            }
        }
        // Lo que queda a la derecha es la imagen: tocarla cierra el menú.
        Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) })
    }
}

@Composable
private fun Entry(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { SecondaryText(subtitle) },
        scale = listItemScale(),
        colors = listItemColors(),
        border = listItemBorder(),
    )
}

/** Un canal: logo, nombre, categoría y calidades, y la estrella de favoritos al lado. */
@Composable
private fun ChannelRow(channel: Channel, state: LiveTvState, modifier: Modifier, onFavorite: (Channel) -> Unit, onPlay: () -> Unit) {
    val favorite = channel.key in state.favorites
    val dead = channel.key in state.dead
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListItem(
            selected = channel.key == state.current?.key,
            onClick = onPlay,
            modifier = modifier.weight(1f).alpha(if (dead) 0.5f else 1f).semantics { contentDescription = channel.name },
            headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                SecondaryText(listOfNotNull(channel.group, channel.qualities.takeIf { it.size > 1 || it.firstOrNull() != "Auto" }?.joinToString(" / ")).joinToString(" · "))
            },
            leadingContent = { Logo(channel, 44) },
            scale = listItemScale(),
            colors = listItemColors(),
            border = listItemBorder(),
        )
        OutlinedButton(onClick = { onFavorite(channel) }, modifier = Modifier.semantics { contentDescription = (if (favorite) "Quitar de favoritos " else "Añadir a favoritos ") + channel.name }) {
            Text(if (favorite) "★" else "☆")
        }
    }
}

@Composable
private fun Logo(channel: Channel, size: Int) {
    val context = LocalContext.current
    if (channel.logo != null) {
        AsyncImage(
            model = remember(channel.logo) { ImageRequest.Builder(context).data(channel.logo).size(120).build() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.SuperficieFoco), contentAlignment = Alignment.Center) {
            Text(channel.name.take(2).uppercase(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Ajustes de la pantalla (las pruebas alargan el tiempo del menú para no depender del reloj). */
object LiveTv {
    @Volatile var menuIdleMs = 10_000L
}
private const val BANNER_MS = 3_500L
