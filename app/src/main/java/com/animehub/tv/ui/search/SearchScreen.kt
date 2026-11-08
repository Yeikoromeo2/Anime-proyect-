package com.animehub.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.ui.components.TvTextField
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors

/**
 * Esqueleto de la búsqueda: campo de texto e historial de búsquedas.
 * Los resultados con portadas llegan con el primer proveedor (Fase 2) y la voz en la Fase 3.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    val lastQuery by viewModel.lastQuery.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Buscar", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Nombre del anime",
                onDone = { viewModel.search(text) },
                modifier = Modifier.width(520.dp).initialFocus(),
            )
            Button(onClick = { viewModel.search(text) }) { Text("Buscar") }
        }

        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Búsquedas recientes", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(recent, key = { it }) { query ->
                        OutlinedButton(onClick = { text = query; viewModel.search(query) }) { Text(query) }
                    }
                    item { OutlinedButton(onClick = { viewModel.clearRecent() }) { Text("Borrar recientes") } }
                }
            }
        }

        lastQuery?.let {
            Text(
                "Buscaste \"$it\". Los resultados con portadas aparecerán cuando esté listo el primer proveedor (Fase 2).",
                color = AppColors.TextoSecundario,
            )
        }
    }
}
