package com.animehub.tv.ui.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.ui.components.rememberInitialFocus
import com.animehub.tv.ui.navigation.AnimeRoute
import com.animehub.tv.ui.theme.AppColors

/** Ficha provisional; la versión completa (sinopsis, episodios, favoritos) llega en la Fase 2. */
@Composable
fun AnimeScreen(route: AnimeRoute, onBack: () -> Unit) {
    val focus = rememberInitialFocus()
    Row(Modifier.fillMaxSize().padding(64.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        AsyncImage(
            model = route.coverUrl,
            contentDescription = route.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 240.dp, height = 350.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(route.title, style = MaterialTheme.typography.headlineMedium)
            Text(route.url, style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
            Text("La ficha completa con sinopsis y episodios llegará con el primer proveedor.", color = AppColors.TextoSecundario)
            OutlinedButton(onClick = onBack, modifier = Modifier.focusRequester(focus)) { Text("Volver") }
        }
    }
}
