package com.animehub.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.data.iptv.CatalogRepository
import com.animehub.tv.domain.model.Catalog
import com.animehub.tv.domain.model.CatalogItem
import com.animehub.tv.domain.model.CatalogKind
import com.animehub.tv.ui.components.Button
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.home.LocalSidebarLeft
import com.animehub.tv.ui.navigation.CatalogDetailRoute
import com.animehub.tv.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repository: CatalogRepository) : ViewModel() {
    suspend fun catalog(kind: CatalogKind): Catalog? = repository.catalog(kind)
}

private sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data object Empty : CatalogLoad
    data class Ready(val catalog: Catalog) : CatalogLoad
}

/** "2024 · ★ 4.5" para el subtítulo de una tarjeta. */
internal fun CatalogItem.meta(): String? =
    listOfNotNull(year, rating.takeIf { it > 0f }?.let { "★ " + "%.1f".format(java.util.Locale.US, it) }).joinToString(" · ").ifEmpty { null }

/**
 * Página de Películas o Series del Inicio: un estante por categoría de la lista, con portada, año y valoración.
 * Abrir un título lleva a su ficha.
 */
@Composable
fun CatalogPage(kind: CatalogKind, onOpen: (kind: String, id: String) -> Unit, modifier: Modifier = Modifier, viewModel: CatalogViewModel = hiltViewModel()) {
    val load by produceState<CatalogLoad>(CatalogLoad.Loading, kind) {
        value = viewModel.catalog(kind)?.let { CatalogLoad.Ready(it) } ?: CatalogLoad.Empty
    }
    val leftToSidebar = LocalSidebarLeft.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "titulo") {
            Column {
                Text(kind.title, style = MaterialTheme.typography.headlineSmall)
                val count = (load as? CatalogLoad.Ready)?.catalog?.items?.size
                Text(
                    when {
                        count != null -> "$count títulos"
                        load is CatalogLoad.Loading -> "Cargando…"
                        else -> "Sin contenido por ahora"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextoSecundario,
                )
            }
        }
        (load as? CatalogLoad.Ready)?.catalog?.shelves?.take(MAX_SHELVES)?.forEach { (category, titles) ->
            item(key = "estante-${category.id}") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(category.name, style = MaterialTheme.typography.titleLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                        items(titles.take(MAX_PER_SHELF), key = { it.id }) { item ->
                            val first = item.id == titles.first().id
                            PosterCard(
                                title = item.title,
                                imageUrl = item.poster,
                                subtitle = item.meta(),
                                cardModifier = if (first) leftToSidebar else Modifier,
                                onClick = { onOpen(kind.key, item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class CatalogDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: CatalogRepository) : ViewModel() {
    val route: CatalogDetailRoute = savedStateHandle.toRoute()
    suspend fun item(): CatalogItem? = repository.item(CatalogKind.of(route.kind), route.id)
}

/** Ficha de una película o serie: fondo, portada, año, valoración, sinopsis y el botón de reproducir. */
@Composable
fun CatalogDetailScreen(viewModel: CatalogDetailViewModel = hiltViewModel()) {
    val kind = CatalogKind.of(viewModel.route.kind)
    val item by produceState<CatalogItem?>(null) { value = viewModel.item() }
    var pressed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(AppColors.Fondo)) {
        item?.backdrop?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.35f)) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(AppColors.Fondo, AppColors.Fondo.copy(alpha = 0.85f), Color.Transparent))))
        item?.let { it ->
            Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                PosterCard(title = it.title, imageUrl = it.poster, onClick = {}, width = 220.dp)
                Column(Modifier.fillMaxWidth(0.6f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(it.title, style = MaterialTheme.typography.displaySmall, maxLines = 3)
                    Text(listOfNotNull(kind.title.removeSuffix("s"), it.meta()).joinToString(" · "), color = AppColors.TextoSecundario)
                    it.plot?.let { plot -> Text(plot, style = MaterialTheme.typography.bodyMedium, maxLines = 6) }
                    Button(onClick = { pressed = true }, modifier = Modifier.initialFocus()) { Text("▶ Reproducir") }
                    if (pressed) Text("La reproducción de este título aún no está disponible.", color = AppColors.TextoSecundario, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private const val MAX_SHELVES = 14
private const val MAX_PER_SHELF = 30
