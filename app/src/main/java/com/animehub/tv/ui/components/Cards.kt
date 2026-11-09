package com.animehub.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.animehub.tv.ui.theme.AppColors

private val CardShape = RoundedCornerShape(10.dp)

/** Escala y borde del elemento enfocado (resaltado visible para el control remoto). */
@Composable
fun focusBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, AppColors.Foco), shape = CardShape),
)

/**
 * Tarjeta de portada vertical con título debajo. Al recibir el foco crece y se marca con borde.
 * @param progress fracción vista (0–1) para "Continuar viendo"; null para ocultar la barra.
 */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Se aplica a la tarjeta enfocable (para focusRequester). */
    cardModifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    progress: Float? = null,
    /** Ancho fijo para filas; null = ocupa la celda (cuadrícula). */
    width: Dp? = 140.dp,
) {
    Column(modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())) {
        Card(
            onClick = onClick,
            modifier = cardModifier.fillMaxWidth().aspectRatio(POSTER_RATIO),
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = focusBorder(),
            colors = CardDefaults.colors(containerColor = AppColors.Superficie),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (badge != null) Badge(badge, Modifier.align(Alignment.TopStart))
                if (progress != null) {
                    ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Separado para que el borde de foco (la tarjeta crece un 8 %) no lo tape.
            modifier = Modifier.padding(top = 12.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextoSecundario,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tarjeta horizontal 16:9 para episodios (novedades). */
@Composable
fun WideCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    width: Dp = 240.dp,
) {
    Column(modifier = modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = cardModifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = focusBorder(),
            colors = CardDefaults.colors(containerColor = AppColors.Superficie),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (badge != null) Badge(badge, Modifier.align(Alignment.TopStart))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AppColors.TextoSecundario, maxLines = 1)
        }
    }
}

/**
 * Tarjeta con reseña corta: portada a la izquierda y, a la derecha, título, datos, géneros y
 * sinopsis. Pensada para "Recomendados" y "Series nuevas" de Inicio.
 * @param synopsis null mientras se carga; vacío si el sitio no tiene reseña.
 */
@Composable
fun ReviewCard(
    title: String,
    imageUrl: String?,
    synopsis: String?,
    onClick: () -> Unit,
    cardModifier: Modifier = Modifier,
    meta: String? = null,
    genres: String? = null,
    badge: String? = null,
    width: Dp = 430.dp,
    height: Dp = 176.dp,
) {
    Card(
        onClick = onClick,
        modifier = cardModifier.width(width).height(height),
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = focusBorder(),
        colors = CardDefaults.colors(
            containerColor = AppColors.Superficie,
            contentColor = Color.White,
            focusedContainerColor = AppColors.SuperficieFoco,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().aspectRatio(POSTER_RATIO)) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (badge != null) Badge(badge, Modifier.align(Alignment.TopStart))
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta != null) Text(meta, style = MaterialTheme.typography.labelSmall, color = AppColors.Acento2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (genres != null) Text(genres, style = MaterialTheme.typography.labelSmall, color = AppColors.TextoSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = when {
                        synopsis == null -> "Cargando reseña…"
                        synopsis.isEmpty() -> "Sin reseña en el sitio."
                        else -> synopsis
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.85f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .padding(6.dp)
            .background(AppColors.Acento.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Proporción de las portadas verticales (2:2,9). */
const val POSTER_RATIO = 2f / 2.9f

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha = 0.6f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).background(AppColors.Acento2))
    }
}

/** Colores de las filas de lista: al enfocarse, fondo violeta oscuro y texto blanco (alto contraste). */
@Composable
fun listItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = Color.White,
    focusedContainerColor = AppColors.SuperficieFoco,
    focusedContentColor = Color.White,
    pressedContainerColor = AppColors.SuperficieFoco,
    pressedContentColor = Color.White,
)

@Composable
fun listItemBorder() = ListItemDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, AppColors.Foco), shape = RoundedCornerShape(10.dp)),
)

@Composable
fun listItemScale() = ListItemDefaults.scale(focusedScale = 1.02f)

/** Texto secundario que hereda el color de su contenedor (legible enfocado o no). */
@Composable
fun SecondaryText(text: String) {
    Text(text, color = LocalContentColor.current.copy(alpha = 0.75f))
}
