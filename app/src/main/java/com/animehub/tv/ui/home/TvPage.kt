package com.animehub.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.domain.model.Channel
import com.animehub.tv.ui.components.Card
import com.animehub.tv.ui.components.focusBorder
import com.animehub.tv.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class TvPageState(val last: Channel? = null, val favorites: List<Channel> = emptyList())

/** Lo que muestra la página de TV en vivo del Inicio: el último canal y los favoritos. */
@HiltViewModel
class TvPageViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    val state: StateFlow<TvPageState> = settings.settings.map { s ->
        TvPageState(
            last = s.lastChannel?.let { runCatching { json.decodeFromString<Channel>(it) }.getOrNull() },
            favorites = s.favoriteChannels.values.mapNotNull { runCatching { json.decodeFromString<Channel>(it) }.getOrNull() }.sortedBy { it.name.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvPageState())

    /** Deja elegido el canal con el que arrancará la TV en vivo. */
    fun choose(channel: Channel, then: () -> Unit) {
        viewModelScope.launch {
            settings.setLastChannel(json.encodeToString(channel), null)
            then()
        }
    }
}

/**
 * Página de TV en vivo del Inicio: un bloque grande con el canal que sigue ("Ver ahora") y, debajo,
 * los favoritos; abrir cualquiera lleva a la TV en vivo.
 */
@Composable
fun TvPage(onOpenTv: () -> Unit, modifier: Modifier = Modifier, leftFocus: Modifier = Modifier, viewModel: TvPageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "titulo") {
            Column {
                Text("TV en vivo", style = MaterialTheme.typography.headlineSmall)
                Text("El canal sigue sonando donde lo dejaste", style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
            }
        }
        item(key = "ahora") {
            Card(
                onClick = onOpenTv,
                modifier = leftFocus.fillMaxWidth().height(210.dp).semantics { contentDescription = "Ver TV en vivo" },
                shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                scale = CardDefaults.scale(focusedScale = 1.02f),
                border = focusBorder(),
                colors = CardDefaults.colors(containerColor = AppColors.Superficie),
            ) {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF2A1B52), Color(0xFF14102A))))) {
                    Row(Modifier.align(Alignment.CenterStart).padding(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        val logo = state.last?.logo
                        if (logo != null) {
                            AsyncImage(logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(state.last?.name ?: "Elige un canal", style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(state.last?.group, state.last?.best?.label?.takeIf { it != "Auto" }).joinToString(" · ").ifEmpty { "Canales, películas y series" },
                                color = AppColors.TextoSecundario,
                            )
                            Text("▶ Ver ahora", color = AppColors.Acento2, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        if (state.favorites.isNotEmpty()) {
            item(key = "favoritos") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Favoritos", style = MaterialTheme.typography.titleLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                        itemsIndexed(state.favorites, key = { _, c -> c.key }) { _, c ->
                            Box(Modifier.width(170.dp).height(110.dp)) {
                                MosaicTile(c.name, c.logo, { viewModel.choose(c, onOpenTv) }, subtitle = c.group)
                            }
                        }
                    }
                }
            }
        }
    }
}
