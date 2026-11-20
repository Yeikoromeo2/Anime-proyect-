package com.animehub.tv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.animehub.tv.R
import com.animehub.tv.ui.components.ListItem
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.components.listItemBorder
import com.animehub.tv.ui.components.listItemColors
import com.animehub.tv.ui.components.listItemScale
import com.animehub.tv.ui.theme.AppColors

/** Pestañas del Inicio: una página por sección. */
enum class HomeTab(val title: String, val section: String) {
    ANIME("Anime", "anime"),
    RETRO("Retro", "retro"),
    PELIS("Pelis", "pelis"),
    SERIES("Series", "series"),
    TV("TV en vivo", "tv"),
}

/** A dónde va "←" desde la primera columna de una página: a la pestaña elegida de la barra lateral. */
val LocalSidebarLeft = compositionLocalOf<Modifier> { Modifier }

val SidebarWidth = 214.dp

/**
 * Barra lateral de pestañas (como en la referencia): a la izquierda y por encima del contenido, con un
 * degradado negro que se desvanece; arriba el logo. La página cambia al enfocar cada pestaña.
 */
@Composable
fun HomeSidebar(
    selected: HomeTab,
    onFocusTab: (HomeTab) -> Unit,
    requesters: Map<HomeTab, FocusRequester>,
    modifier: Modifier = Modifier,
    onActivate: (HomeTab) -> Unit = onFocusTab,
) {
    Column(
        modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.70f), Color.Transparent)))
            .padding(start = 22.dp, top = 22.dp, end = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.padding(bottom = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.mascota), contentDescription = null, modifier = Modifier.size(40.dp))
            Text("AnimeHub TV", style = MaterialTheme.typography.titleMedium)
        }
        HomeTab.entries.forEach { tab ->
            ListItem(
                selected = tab == selected,
                onClick = { onActivate(tab) },
                modifier = Modifier.focusRequester(requesters.getValue(tab)).onFocusChanged { if (it.isFocused) onFocusTab(tab) }.semantics { contentDescription = "Pestaña ${tab.title}" },
                headlineContent = { Text(tab.title, color = if (tab == selected) AppColors.Acento2 else Color.White) },
                scale = listItemScale(),
                colors = listItemColors(),
                border = listItemBorder(),
            )
        }
    }
}

/** Pestañas en una sola fila (tablet en vertical, donde no cabe la barra lateral). */
@Composable
fun HomeTabsRow(selected: HomeTab, onFocusTab: (HomeTab) -> Unit, requesters: Map<HomeTab, FocusRequester>, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeTab.entries.forEach { tab ->
            OutlinedButton(
                onClick = { onFocusTab(tab) },
                modifier = Modifier.focusRequester(requesters.getValue(tab)).onFocusChanged { if (it.isFocused) onFocusTab(tab) },
            ) { Text(tab.title, color = if (tab == selected) AppColors.Acento2 else Color.White) }
        }
    }
}

/** Accesos de arriba a la derecha: Explorar (con la búsqueda), voz, Historial y Ajustes, y el aviso de versión. */
@Composable
fun HomeTopBar(
    onExplore: () -> Unit,
    onVoiceSearch: () -> Unit,
    voiceAvailable: Boolean,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    exploreModifier: Modifier,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Explorar incluye la búsqueda; el micrófono (si hay voz) abre Explorar escuchando.
        com.animehub.tv.ui.components.Button(onClick = onExplore, modifier = exploreModifier) { Text("Explorar") }
        if (voiceAvailable) OutlinedButton(onClick = onVoiceSearch) { Text("🎤") }
        OutlinedButton(onClick = onHistory) { Text("Historial") }
        OutlinedButton(onClick = onSettings) { Text("Ajustes") }
        extra()
    }
}
