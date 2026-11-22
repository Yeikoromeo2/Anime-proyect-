package com.animehub.tv.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.animehub.tv.ui.components.Button
import com.animehub.tv.ui.components.ListItem
import com.animehub.tv.ui.components.OutlinedButton
import com.animehub.tv.ui.components.PosterCard
import com.animehub.tv.ui.components.SecondaryText
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.navigation.VodDetailRoute
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.vod.VodRepository
import com.animehub.tv.vod.models.MediaDetail
import com.animehub.tv.vod.models.MediaType
import com.animehub.tv.vod.models.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VodDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: VodRepository) : ViewModel() {
    val route: VodDetailRoute = savedStateHandle.toRoute()
    suspend fun detail(): MediaDetail? = repository.detail(ProviderId.of(route.provider), MediaType.of(route.type), route.id)
}

/** Detalle de un título: carátula, sinopsis y, en series, selector de temporada y lista de episodios. */
@Composable
fun VodDetailScreen(onPlay: (provider: String, type: String, id: String, season: Int, episode: Int, title: String) -> Unit, viewModel: VodDetailViewModel = hiltViewModel()) {
    val detail by produceState<MediaDetail?>(null) { value = viewModel.detail() }
    var seasonIndex by rememberSaveable { mutableIntStateOf(0) }
    val route = viewModel.route
    Box(Modifier.fillMaxSize().background(AppColors.Fondo)) {
        detail?.item?.backdrop?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.3f)) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(AppColors.Fondo, AppColors.Fondo.copy(alpha = 0.85f), Color.Transparent))))
        val d = detail
        if (d == null) {
            Text("Cargando…", modifier = Modifier.align(Alignment.Center), color = AppColors.TextoSecundario)
            return@Box
        }
        val item = d.item
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            PosterCard(title = item.title, imageUrl = item.poster, onClick = {}, width = 220.dp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, style = MaterialTheme.typography.displaySmall, maxLines = 2)
                Text(
                    listOfNotNull(item.type.title.removeSuffix("s"), item.year, item.rating?.let { "★ " + "%.1f".format(java.util.Locale.US, it) }, item.genres.takeIf { item.provider == ProviderId.COMMUNITY }?.take(3)?.joinToString(", ")?.ifEmpty { null }).joinToString(" · "),
                    color = AppColors.TextoSecundario,
                )
                item.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 5) }
                if (item.type == MediaType.MOVIE) {
                    Button(onClick = { onPlay(route.provider, route.type, route.id, 0, 0, item.title) }, modifier = Modifier.initialFocus()) { Text("▶ Reproducir") }
                } else if (d.seasons.isEmpty()) {
                    Text("Sin episodios disponibles por ahora.", color = AppColors.TextoSecundario)
                } else {
                    val season = d.seasons[seasonIndex.coerceIn(0, d.seasons.lastIndex)]
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(d.seasons.size) { i ->
                            val label = d.seasons[i].label
                            if (i == seasonIndex) Button(onClick = { seasonIndex = i }, modifier = Modifier.initialFocus()) { Text(label, maxLines = 1) }
                            else OutlinedButton(onClick = { seasonIndex = i }) { Text(label, maxLines = 1) }
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(season.episodes, key = { "${it.seasonNumber}-${it.episodeNumber}" }) { e ->
                            ListItem(
                                selected = false,
                                onClick = { onPlay(route.provider, route.type, route.id, e.seasonNumber, e.episodeNumber, "${item.title} · T${e.seasonNumber} E${e.episodeNumber}") },
                                headlineContent = { Text("${e.episodeNumber}. ${e.title}", maxLines = 1) },
                                supportingContent = e.overview?.let { o -> { SecondaryText(o.take(140)) } },
                            )
                        }
                    }
                }
            }
        }
    }
}
