package com.animehub.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.ui.components.Card
import com.animehub.tv.ui.theme.AppColors

/** Color del trazo interior de un mosaico enfocado. */
private val FocusStroke = Color(0xFF3A66FE)

/**
 * Mosaico de póster para las páginas del Inicio: imagen de lado a lado; al enfocarlo crece un 5 %,
 * se marca con un trazo interior y aparece su título sobre un degradado (sin foco no se ve el texto).
 */
@Composable
fun MosaicTile(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxSize().onFocusChanged { focused = it.isFocused }.semantics { contentDescription = title },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusStroke), shape = shape)),
        colors = CardDefaults.colors(containerColor = AppColors.Superficie),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = remember(imageUrl) { ImageRequest.Builder(context).data(imageUrl).size(480).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (focused) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AppColors.TextoSecundario, maxLines = 1)
                }
            }
        }
    }
}

/** Texto corto debajo del título de un mosaico: tipo · año · idioma. */
fun AnimeCard.mosaicSubtitle(): String? = listOfNotNull(type?.etiqueta, year?.toString(), language).joinToString(" · ").ifEmpty { null }

/**
 * Distribución de la página "Películas/Series" de la referencia: arriba a la izquierda un mosaico
 * apaisado grande con cuatro pequeños debajo, y a la derecha dos verticales altos.
 * Los espacios sin título quedan vacíos.
 */
@Composable
fun MosaicGrid(items: List<AnimeCard>, onOpen: (AnimeCard) -> Unit, modifier: Modifier = Modifier, firstTile: Modifier = Modifier) {
    fun tile(i: Int, m: Modifier = Modifier, first: Boolean = false) = @Composable {
        val c = items.getOrNull(i)
        if (c != null) MosaicTile(c.title, c.coverUrl, { onOpen(c) }, m.then(if (first) firstTile else Modifier), c.mosaicSubtitle())
        else Box(m)
    }
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(2.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1.75f).fillMaxWidth()) { tile(0, first = true)() }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 1..4) Box(Modifier.weight(1f).fillMaxHeight()) { tile(i)() }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { tile(5)() }
        Box(Modifier.weight(1f).fillMaxHeight()) { tile(6)() }
    }
}
