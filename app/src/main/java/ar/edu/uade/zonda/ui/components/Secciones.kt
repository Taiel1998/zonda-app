package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.valor

// Piezas de las pantallas de Ajustes del Figma (4-66, 51-1137, 66-3316, 66-3558): tarjetas con borde
// gris, esquinas de 12, 12 de margen más el borde y 10 entre piezas; filas de 10 arriba y abajo con
// título Medium 15 y detalle Regular 13; la fila con interruptor; la tarjeta ámbar de advertencia y
// las filas de etiqueta y valor.

/** Tarjeta blanca con borde gris: la base de las secciones, las tarjetas de datos y las de cada red. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    colorBorde: Color = MaterialTheme.colorScheme.outlineVariant,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color,
        border = BorderStroke(1.dp, colorBorde),
        modifier = modifier.fillMaxWidth(),
    ) {
        // 12 de margen más 1 del borde, como el Figma (borde por dentro).
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
    }
}

/** El título chico gris de una sección ("Red", "Avisos", "Conexión"). */
@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(text = texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Tarjeta de una sección ("Red", "Avisos", "Datos locales"...) con su título chico arriba. */
@Composable
fun TarjetaSeccion(
    titulo: String,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Panel(modifier = modifier) {
        TituloSeccion(titulo)
        contenido()
    }
}

/** Título y detalle de una fila, uno debajo del otro. */
@Composable
private fun TextosDeFila(titulo: String, detalle: String?, colorTitulo: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = titulo, style = MaterialTheme.typography.titleSmall, color = colorTitulo)
        if (detalle != null) {
            Text(text = detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fila tocable con título, detalle opcional y la flecha que indica que abre otra pantalla. */
@Composable
fun FilaNavegable(
    titulo: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    colorTitulo: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = alTocar)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextosDeFila(titulo, detalle, colorTitulo, Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Fila de texto sin acción (por ejemplo, la última sincronización). */
@Composable
fun FilaTexto(titulo: String, detalle: String, modifier: Modifier = Modifier) {
    TextosDeFila(titulo, detalle, Color.Unspecified, modifier.fillMaxWidth().padding(vertical = 10.dp))
}

/** Fila con un interruptor; toda la fila se puede tocar (área de más de 48 dp, RNF09). */
@Composable
fun FilaConInterruptor(
    titulo: String,
    detalle: String,
    activo: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = activo, onValueChange = alCambiar, role = Role.Switch)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextosDeFila(titulo, detalle, Color.Unspecified, Modifier.weight(1f))
        Interruptor(activo = activo)
    }
}

/**
 * El interruptor del Figma: 52 por 32, círculo blanco de 24 en los dos estados, verde prendido y
 * gris sin borde apagado. Con [thumbContent] Material deja el círculo de 24 también apagado.
 */
@Composable
fun Interruptor(activo: Boolean, modifier: Modifier = Modifier, alCambiar: ((Boolean) -> Unit)? = null) {
    Switch(
        checked = activo,
        onCheckedChange = alCambiar,
        thumbContent = {},
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
            uncheckedBorderColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

/** Tarjeta ámbar con una advertencia y su salida ("Telegram sin conectar", "Notificaciones desactivadas"). */
@Composable
fun TarjetaAdvertencia(
    titulo: String,
    texto: String,
    accion: String,
    alAccionar: () -> Unit,
    modifier: Modifier = Modifier,
    nota: String? = null,
) {
    Surface(color = Tonos.Media.fondo, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.labelLarge, color = Tonos.Media.texto)
            Text(text = texto, style = MaterialTheme.typography.bodySmall, color = Tonos.Media.texto)
            BotonTonal(texto = accion, alTocar = alAccionar)
            if (nota != null) {
                Text(text = nota, style = MaterialTheme.typography.nota, color = Tonos.Media.texto)
            }
        }
    }
}

/** Tarjeta con un título chico y filas de etiqueta y valor ("Conexión", "Acceso de este teléfono"). */
@Composable
fun TarjetaDatos(
    titulo: String,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    TarjetaSeccion(titulo = titulo, modifier = modifier, contenido = contenido)
}

/** Etiqueta gris a la izquierda y valor a la derecha. */
@Composable
fun FilaDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.valor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Nota gris al pie de una pantalla. */
@Composable
fun NotaAlPie(texto: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = texto,
        style = MaterialTheme.typography.nota,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}
