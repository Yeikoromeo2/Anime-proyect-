package com.animehub.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.domain.model.FilterGroup
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors

/**
 * Panel lateral con los filtros reales del sitio elegido (o los comunes en "Todos").
 * Es una ventana aparte: el D-pad no se escapa a la cuadrícula y "Atrás" lo cierra
 * aplicando los cambios.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun FilterPanel(
    title: String,
    groups: List<FilterGroup>,
    loading: Boolean,
    selection: FilterSelection,
    onToggle: (FilterGroup, String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(480.dp)
                    .fillMaxHeight()
                    .background(AppColors.Superficie),
                contentPadding = PaddingValues(28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (selection.activeCount == 0) "Sin filtros activos" else "${selection.activeCount} filtros activos",
                            color = AppColors.TextoSecundario,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onClose, modifier = Modifier.initialFocus()) { Text("Ver resultados") }
                            OutlinedButton(onClick = onClear, enabled = selection.activeCount > 0) { Text("Limpiar") }
                        }
                    }
                }
                when {
                    loading -> item { Text("Leyendo los filtros del sitio…", color = AppColors.TextoSecundario) }
                    groups.isEmpty() -> item { Text("Este sitio no ofrece filtros.", color = AppColors.TextoSecundario) }
                }
                items(groups.sortedBy { GROUP_ORDER.indexOf(it.id).let { i -> if (i < 0) 99 else i } }, key = { it.id }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(group.label, style = MaterialTheme.typography.titleMedium, color = AppColors.Acento2)
                        val chip: @Composable (com.animehub.tv.domain.model.FilterOption) -> Unit = { option ->
                            FilterChip(
                                selected = option.value in selection.valuesOf(group.id),
                                onClick = { onToggle(group, option.value) },
                            ) { Text(option.label) }
                        }
                        if (group.id in SINGLE_ROW) {
                            // Años y letras: muchas opciones cortas en una sola fila desplazable.
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(group.options, key = { it.value }) { chip(it) }
                            }
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.options.forEach { chip(it) }
                            }
                        }
                    }
                }
                item { Box(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

/**
 * Orden de los grupos: primero los cortos (tipo, estado), luego año (una fila) y al final los
 * largos, para no tener que bajar por decenas de géneros para llegar al año.
 */
private val GROUP_ORDER = listOf("type", "status", "year", "genre", "letter")

/** Grupos que se muestran en una sola fila desplazable. */
private val SINGLE_ROW = setOf("year", "letter")
