package com.animehub.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.animehub.tv.ui.focus.initialFocus
import com.animehub.tv.ui.theme.AppColors

/** Contenedor común de los diálogos (fondo, borde y ancho fijo). */
@Composable
fun DialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .background(AppColors.Superficie, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

/** Confirmación con el foco inicial en "Cancelar" para evitar acciones accidentales. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "Cancelar",
) {
    DialogFrame(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextoSecundario)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.initialFocus()) { Text(dismissText) }
            Button(onClick = onConfirm) { Text(confirmText) }
        }
    }
}

/** Opción de un diálogo de acciones. */
data class DialogAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** Lista vertical de acciones grandes (cómodas con el control remoto). */
@Composable
fun ActionsDialog(title: String, message: String?, actions: List<DialogAction>, onDismiss: () -> Unit) {
    DialogFrame(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextoSecundario)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val firstEnabled = actions.indexOfFirst { it.enabled }
            actions.forEachIndexed { index, action ->
                Button(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.fillMaxWidth().let { if (index == firstEnabled) it.initialFocus() else it },
                ) { Text(action.label) }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}

/** Campo de texto para TV: al enfocarlo se abre el teclado en pantalla del televisor. */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(AppColors.Acento2),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) AppColors.Foco else AppColors.TextoSecundario, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = AppColors.TextoSecundario, fontSize = 18.sp)
            inner()
        },
    )
}

/** Diálogo para editar un texto (dominio, URL de configuración…). */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    placeholder: String = "",
    onReset: (() -> Unit)? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Uri,
    saveLabel: String = "Guardar",
) {
    var text by remember { mutableStateOf(initialValue) }
    DialogFrame(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
        TvTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = placeholder,
            keyboardType = keyboardType,
            onDone = { onSave(text) },
            modifier = Modifier.fillMaxWidth().initialFocus(),
        )
        if (error != null) Text(error, color = AppColors.Error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(text) }) { Text(saveLabel) }
            if (onReset != null) OutlinedButton(onClick = onReset) { Text("Restablecer") }
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        }
    }
}

/** Lista simple de solo lectura dentro de un diálogo (registro de errores). */
@Composable
fun ListDialog(title: String, lines: List<String>, emptyText: String, onDismiss: () -> Unit, extraAction: DialogAction? = null) {
    DialogFrame(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (lines.isEmpty()) {
            Text(emptyText, color = AppColors.TextoSecundario)
        } else {
            LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lines.size) { i -> Text(lines[i], style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.initialFocus()) { Text("Cerrar") }
            if (extraAction != null) Button(onClick = extraAction.onClick, enabled = extraAction.enabled) { Text(extraAction.label) }
        }
    }
}

/** Lista de opciones desplazable (p. ej. 23 temporadas) con el foco en la opción actual. */
@Composable
fun ChoiceDialog(title: String, options: List<String>, selectedIndex: Int, onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    DialogFrame(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0)),
        ) {
            items(options.size) { i ->
                val modifier = Modifier.fillMaxWidth().let { if (i == selectedIndex) it.initialFocus() else it }
                if (i == selectedIndex) {
                    Button(onClick = { onChoose(i) }, modifier = modifier) { Text("✓ " + options[i]) }
                } else {
                    OutlinedButton(onClick = { onChoose(i) }, modifier = modifier) { Text(options[i]) }
                }
            }
        }
    }
}

/**
 * Teclado numérico para el control remoto: botones 0–9, borrar e "Ir". También acepta las
 * teclas numéricas del control. Evita abrir el teclado del sistema para escribir un número.
 */
@Composable
fun NumberPadDialog(
    title: String,
    message: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    maxDigits: Int = 5,
) {
    var number by remember { mutableStateOf("") }
    val add: (Char) -> Unit = { d -> if (number.length < maxDigits) number += d }
    DialogFrame(onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = DIGIT_KEYS[event.key]
                when {
                    digit != null -> { add(digit); true }
                    event.key == Key.Backspace || event.key == Key.Delete -> { number = number.dropLast(1); true }
                    else -> false
                }
            },
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.TextoSecundario)
            Text(
                number.ifEmpty { "—" },
                style = MaterialTheme.typography.displaySmall,
                color = if (number.isEmpty()) AppColors.TextoSecundario else Color.White,
            )
            if (error != null) Text(error, color = AppColors.Error, style = MaterialTheme.typography.bodySmall)
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "Ir"))
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { c, label ->
                        val modifier = Modifier.width(120.dp).let { if (r == 0 && c == 0) it.initialFocus() else it }
                        when (label) {
                            "⌫" -> OutlinedButton(onClick = { number = number.dropLast(1) }, modifier = modifier) { Text("⌫ Borrar") }
                            "Ir" -> Button(onClick = { onSubmit(number) }, enabled = number.isNotEmpty(), modifier = modifier) { Text("Ir") }
                            else -> OutlinedButton(onClick = { add(label[0]) }, modifier = modifier) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}

private val DIGIT_KEYS = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9',
)
