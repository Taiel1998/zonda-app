package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R

/** Alto de la barra superior del Figma: el título de 22 con 12 arriba y 12 abajo. */
val AltoBarraSuperior = 51.dp

/**
 * La barra superior de cada pantalla (Figma): fondo blanco, título Medium 22 y, en las pantallas
 * que se abren desde otra, la flecha para volver. El escáner la usa oscura ([colorFondo] y
 * [colorContenido]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    modifier: Modifier = Modifier,
    alVolver: (() -> Unit)? = null,
    colorFondo: Color = MaterialTheme.colorScheme.surface,
    colorContenido: Color = MaterialTheme.colorScheme.onSurface,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        // TopAppBar no marca el título como encabezado: con TalkBack se llega a él saltando por títulos.
        title = { Text(text = titulo, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                }
            }
        },
        actions = acciones,
        expandedHeight = AltoBarraSuperior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorFondo,
            scrolledContainerColor = colorFondo,
            titleContentColor = colorContenido,
            navigationIconContentColor = colorContenido,
            actionIconContentColor = colorContenido,
        ),
        modifier = modifier,
    )
}

/**
 * El aviso inferior del Figma (66-3808): fondo casi negro, esquinas de 8, texto Regular 13 blanco y
 * la acción en verde claro. Toda la altura del aviso responde al toque de la acción.
 */
@Composable
fun AvisoInferior(datos: SnackbarData, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            .fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = datos.visuals.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (datos.visuals.actionLabel == null) 16.dp else 0.dp),
            )
            val accion = datos.visuals.actionLabel
            if (accion != null) {
                Box(
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = datos::performAction)
                        .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Text(text = accion, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
