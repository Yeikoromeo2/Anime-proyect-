package com.animehub.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.animehub.tv.BuildConfig
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.ui.components.ActionsDialog
import com.animehub.tv.ui.components.ConfirmDialog
import com.animehub.tv.ui.components.DialogAction
import com.animehub.tv.ui.components.ListDialog
import com.animehub.tv.ui.components.SecondaryText
import com.animehub.tv.ui.components.listItemBorder
import com.animehub.tv.ui.components.listItemColors
import com.animehub.tv.ui.components.listItemScale
import com.animehub.tv.ui.components.TextInputDialog
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.focus.rememberInitialFocusState
import com.animehub.tv.ui.theme.AppColors
import com.animehub.tv.util.Format

private sealed interface SettingsDialog {
    data class SiteOptions(val site: EffectiveSite) : SettingsDialog
    data class EditDomain(val site: EffectiveSite) : SettingsDialog
    data object EditRemoteUrl : SettingsDialog
    data object ClearServers : SettingsDialog
    data object Errors : SettingsDialog
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val firstFocus = rememberInitialFocusState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp)) }

        item { Section("Sitios") }
        state.sites.forEachIndexed { index, site ->
            item(key = "site-${site.id}") {
                val detail = buildString {
                    append(site.baseUrl)
                    if (site.domainEdited) append(" · dominio editado")
                    if (site.config.isClosed) append(" · cerró (no hay nada que ver)")
                    else if (site.config.isPending) append(" · pendiente de análisis")
                }
                SettingItem(
                    title = site.name,
                    subtitle = detail,
                    modifier = if (index == 0) Modifier.initialFocus(firstFocus) else Modifier,
                    onClick = { dialog = SettingsDialog.SiteOptions(site) },
                    trailing = { Switch(checked = site.enabled, onCheckedChange = null, enabled = !site.config.isPending) },
                )
            }
        }

        item { Section("Configuración remota") }
        item {
            SettingItem(
                title = "URL de la configuración",
                subtitle = state.settings.remoteConfigUrl,
                onClick = { dialogError = null; dialog = SettingsDialog.EditRemoteUrl },
            )
        }
        item {
            val config = state.config
            val base = if (config == null) "Cargando…" else buildString {
                append("Versión ${config.config.version} (${config.source.etiqueta})")
                if (state.settings.lastConfigUpdate > 0) append(" · última descarga ${Format.date(state.settings.lastConfigUpdate)}")
            }
            val status = when (val u = update) {
                UpdateStatus.Idle -> null
                UpdateStatus.Running -> "Actualizando…"
                is UpdateStatus.Done -> "Configuración actualizada (versión ${u.version})"
                is UpdateStatus.Failed -> "No se pudo actualizar: ${u.message}"
            }
            SettingItem(
                title = "Actualizar configuración",
                subtitle = listOfNotNull(status, base).joinToString("\n"),
                onClick = viewModel::refreshConfig,
            )
        }

        item { Section("Reproducción") }
        item {
            SettingItem(
                title = "Saltar con las flechas",
                subtitle = "${state.settings.seekSeconds} segundos (pulsa para cambiar)",
                onClick = { viewModel.toggleSeek() },
            )
        }
        item {
            SettingItem(
                title = "Calidad preferida",
                subtitle = "${state.settings.preferredQuality.etiqueta} (pulsa para cambiar)",
                onClick = { viewModel.nextQuality() },
            )
        }
        item {
            val names = state.sites.associate { it.id to it.name }
            val servers = state.settings.preferredServers
            SettingItem(
                title = "Servidor preferido",
                subtitle = if (servers.isEmpty()) "Ninguno todavía: se guarda al elegir un servidor"
                else servers.entries.joinToString(" · ") { "${names[it.key] ?: it.key}: ${it.value}" },
                onClick = { if (servers.isNotEmpty()) dialog = SettingsDialog.ClearServers },
            )
        }

        item { Section("Diagnóstico") }
        item {
            SettingItem(
                title = "Registro de errores",
                subtitle = if (state.errors.isEmpty()) "Sin errores" else "${state.errors.size} errores (últimos 50)",
                onClick = { dialog = SettingsDialog.Errors },
            )
        }

        item { Section("Acerca de") }
        item { SettingItem(title = "Versión de la app", subtitle = BuildConfig.VERSION_NAME, onClick = {}) }
    }

    when (val d = dialog) {
        is SettingsDialog.SiteOptions -> {
            val site = state.sites.firstOrNull { it.id == d.site.id } ?: d.site
            ActionsDialog(
                title = site.name,
                message = site.config.notes,
                actions = listOf(
                    DialogAction(if (site.enabled) "Desactivar sitio" else "Activar sitio", enabled = !site.config.isPending) {
                        viewModel.setSiteEnabled(site, !site.enabled); dialog = null
                    },
                    DialogAction("Editar dominio") { dialogError = null; dialog = SettingsDialog.EditDomain(site) },
                    DialogAction("Restablecer dominio", enabled = site.domainEdited) { viewModel.resetDomain(site); dialog = null },
                ),
                onDismiss = { dialog = null },
            )
        }
        is SettingsDialog.EditDomain -> TextInputDialog(
            title = "Dominio de ${d.site.name}",
            message = "Original de la configuración: ${d.site.config.baseUrl}",
            initialValue = d.site.baseUrl,
            placeholder = "https://…",
            error = dialogError,
            onSave = { value ->
                dialogError = viewModel.setDomain(d.site, value)
                if (dialogError == null) dialog = null
            },
            onReset = { viewModel.resetDomain(d.site); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.EditRemoteUrl -> TextInputDialog(
            title = "URL de la configuración",
            message = "Archivo JSON con los dominios y selectores (por defecto, el del repositorio en GitHub).",
            initialValue = state.settings.remoteConfigUrl,
            placeholder = "https://…/sites.json",
            error = dialogError,
            onSave = { value ->
                dialogError = viewModel.setRemoteUrl(value)
                if (dialogError == null) dialog = null
            },
            onReset = { viewModel.resetRemoteUrl(); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.ClearServers -> ConfirmDialog(
            title = "Olvidar servidores preferidos",
            message = "La próxima vez se preguntará qué servidor usar en cada sitio.",
            confirmText = "Olvidar",
            onConfirm = { viewModel.clearPreferredServers(); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Errors -> ListDialog(
            title = "Registro de errores",
            lines = state.errors.map { "${Format.date(it.timestamp)} · ${it.source}: ${it.message}" },
            emptyText = "No se ha registrado ningún error.",
            onDismiss = { dialog = null },
            extraAction = DialogAction("Borrar registro", enabled = state.errors.isNotEmpty()) { viewModel.clearErrors() },
        )
        null -> Unit
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = AppColors.Acento2,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(title) },
        supportingContent = { SecondaryText(subtitle) },
        trailingContent = trailing,
        scale = listItemScale(),
        colors = listItemColors(),
        border = listItemBorder(),
    )
}
