package com.animehub.tv.ui.update

import androidx.compose.runtime.Composable
import com.animehub.tv.data.update.AppUpdate
import com.animehub.tv.data.update.UpdateState
import com.animehub.tv.ui.components.ActionsDialog
import com.animehub.tv.ui.components.DialogAction
import java.util.Locale

/** Acciones de la actualización (las implementa UpdateManager). */
interface UpdateActions {
    fun download(update: AppUpdate)
    fun openInstallPermission(): Boolean
    fun install(state: UpdateState.NeedsPermission)
    fun check()
}

/** Hay algo que mostrar en la cabecera de Inicio (versión nueva, descarga o un paso pendiente). */
fun UpdateState.showsInHeader(): Boolean = when (this) {
    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.NeedsPermission, is UpdateState.Installing -> true
    is UpdateState.Failed -> update != null
    else -> false
}

/** Texto del botón de la cabecera. */
fun UpdateState.headerLabel(): String = when (this) {
    is UpdateState.Available -> "⬆ Versión ${update.version}"
    is UpdateState.Downloading -> "Descargando ${percent(progress)}"
    is UpdateState.NeedsPermission -> "⬆ Instalar ${update.version}"
    is UpdateState.Installing -> "⬆ Instalar ${update.version}"
    is UpdateState.Failed -> "⬆ Reintentar actualización"
    else -> ""
}

/**
 * Diálogo de la actualización: avisa de la versión nueva, muestra la descarga y guía el permiso
 * de "instalar apps" la primera vez. Se instala encima: favoritos, historial y ajustes se conservan.
 */
@Composable
fun UpdateDialog(state: UpdateState, installed: String, actions: UpdateActions, onDismiss: () -> Unit) {
    when (state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> ActionsDialog(
            title = "Buscando actualizaciones…",
            message = "Versión instalada: $installed",
            actions = emptyList(),
            onDismiss = onDismiss,
        )
        is UpdateState.UpToDate -> ActionsDialog(
            title = "Ya tienes la última versión",
            message = "AnimeHub TV $installed",
            actions = emptyList(),
            onDismiss = onDismiss,
        )
        is UpdateState.Available -> ActionsDialog(
            title = "Nueva versión ${state.update.version}",
            message = buildString {
                append("Tienes la $installed. Se descarga")
                if (state.update.sizeBytes > 0) append(" (${megabytes(state.update.sizeBytes)})")
                append(" y se instala encima: tus favoritos, historial y ajustes se conservan.")
                releaseNotes(state.update.notes)?.let { append("\n\n").append(it) }
            },
            actions = listOf(
                DialogAction("Descargar e instalar") { actions.download(state.update) },
            ),
            onDismiss = onDismiss,
        )
        is UpdateState.Downloading -> ActionsDialog(
            title = "Descargando la versión ${state.update.version}…",
            message = "${percent(state.progress)} · al terminar se abre el instalador de Android.",
            actions = emptyList(), // "Cerrar" deja la descarga en segundo plano
            onDismiss = onDismiss,
        )
        is UpdateState.NeedsPermission -> ActionsDialog(
            title = "Falta un permiso (solo la primera vez)",
            message = "Para instalar la actualización, Android pide permitir que AnimeHub TV instale apps. " +
                "Se abrirá Ajustes: activa \"Permitir de esta fuente\", vuelve con Atrás y elige \"Instalar\".",
            actions = listOf(
                DialogAction("Abrir el permiso") { actions.openInstallPermission() },
                DialogAction("Instalar") { actions.install(state) },
            ),
            onDismiss = onDismiss,
        )
        is UpdateState.Installing -> ActionsDialog(
            title = "Instalando la versión ${state.update.version}",
            message = "Confirma \"Actualizar\" en la ventana de Android. La app se cerrará y podrás abrirla de nuevo.",
            actions = emptyList(),
            onDismiss = onDismiss,
        )
        is UpdateState.Failed -> ActionsDialog(
            title = "No se pudo actualizar",
            message = state.message,
            actions = listOfNotNull(
                state.update?.let { u -> DialogAction("Reintentar") { actions.download(u) } } ?: DialogAction("Buscar de nuevo") { actions.check() },
            ),
            onDismiss = onDismiss,
        )
    }
}

private const val NOTES_MAX = 600

/**
 * Notas de la Release en texto simple: sin el enlace automático "Full Changelog" de GitHub,
 * sin líneas que solo son enlaces y sin el formato Markdown.
 */
internal fun releaseNotes(raw: String?): String? = raw
    ?.lines()
    ?.map { it.trim() }
    ?.filterNot { it.startsWith("**Full Changelog**") || it.matches(Regex("""https?://\S+""")) }
    ?.map { it.replace("**", "").replace(Regex("""^#+\s*"""), "").replace(Regex("""^[*-]\s+"""), "• ") }
    ?.joinToString("\n")
    ?.trim()
    ?.take(NOTES_MAX)
    ?.takeIf { it.isNotBlank() }

private fun percent(progress: Float) = "${(progress * 100).toInt()} %"

private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
