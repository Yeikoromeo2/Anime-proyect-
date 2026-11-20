package com.animehub.tv.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ButtonShape
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.ListItemBorder
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.ListItemShape
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.SelectableChipColors
import androidx.tv.material3.SelectableChipShape

/*
 * Toque en pantalla (tablets). Los componentes de Compose para TV solo responden al control
 * remoto (OK/Enter): en una tablet se ven pero no hacen nada al tocarlos. Estas versiones son las
 * mismas de TV y además reconocen el toque, con el mismo efecto visual de "presionado".
 * Con el control remoto funcionan igual que antes.
 */

/**
 * Toque = clic. Comparte [interaction] con el componente para que se vea presionado mientras se
 * toca. Si el dedo se arrastra (desplazar una fila o una lista), no cuenta como toque.
 */
@Composable
fun Modifier.tapToClick(interaction: MutableInteractionSource, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    if (!enabled) return this
    return this.pointerInput(interaction) {
        detectTapGestures(
            onPress = { offset ->
                val press = PressInteraction.Press(offset)
                interaction.emit(press)
                val released = tryAwaitRelease()
                interaction.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
            },
            onTap = { currentOnClick() },
        )
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = ButtonDefaults.colors(),
    border: ButtonBorder = ButtonDefaults.border(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = modifier.tapToClick(interaction, enabled, onClick),
        enabled = enabled,
        scale = scale,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: ButtonScale = OutlinedButtonDefaults.scale(),
    shape: ButtonShape = OutlinedButtonDefaults.shape(),
    colors: ButtonColors = OutlinedButtonDefaults.colors(),
    border: ButtonBorder = OutlinedButtonDefaults.border(),
    contentPadding: PaddingValues = OutlinedButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.tv.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.tapToClick(interaction, enabled, onClick),
        enabled = enabled,
        scale = scale,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: CardShape = CardDefaults.shape(),
    colors: CardColors = CardDefaults.colors(),
    scale: CardScale = CardDefaults.scale(),
    border: CardBorder = CardDefaults.border(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier.tapToClick(interaction, true, onClick),
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun ListItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    shape: ListItemShape = ListItemDefaults.shape(),
    colors: ListItemColors = ListItemDefaults.colors(),
    scale: ListItemScale = ListItemDefaults.scale(),
    border: ListItemBorder = ListItemDefaults.border(),
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.tv.material3.ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = headlineContent,
        modifier = modifier.tapToClick(interaction, enabled, onClick),
        enabled = enabled,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        interactionSource = interaction,
    )
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: SelectableChipShape = FilterChipDefaults.shape(),
    colors: SelectableChipColors = FilterChipDefaults.colors(),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.tv.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.tapToClick(interaction, enabled, onClick),
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = shape,
        colors = colors,
        interactionSource = interaction,
        content = content,
    )
}
