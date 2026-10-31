package ar.edu.uade.zonda.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.ui.theme.Tono
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
    /** 10 entre piezas; las listas de personas y equipos van a 4, con las filas más altas (Figma 468-2607). */
    espacio: Dp = 10.dp,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color,
        border = BorderStroke(1.dp, colorBorde),
        modifier = modifier.fillMaxWidth(),
    ) {
        // 12 de margen más 1 del borde, como el Figma (borde por dentro).
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(espacio), content = contenido)
    }
}

/** El título chico gris de una sección ("Red", "Avisos", "Conexión"), con su "?" si tiene [ayuda]. */
@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier, ayuda: String? = null) {
    // Encabezado para TalkBack: se salta de sección en sección ("Red", "Avisos", "Datos del flujo").
    if (ayuda != null) {
        EtiquetaConAyuda(
            texto = texto,
            ayuda = ayuda,
            estilo = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
            encabezado = true,
        )
    } else {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.semantics { heading() },
        )
    }
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
            .clickable(role = Role.Button, onClick = alTocar)
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
 * El interruptor del Figma (Switch/on y Switch/off): 52 por 32, círculo blanco de 24 a 4 del borde en
 * los dos estados, verde prendido y gris sin borde apagado. Se dibuja acá porque el Switch de Material
 * achica el círculo a 16 cuando está apagado (con `thumbContent` vacío tampoco lo agranda en esta
 * versión). Con [alCambiar] se puede tocar y mide 48 de alto para el dedo; sin él, lo toca la fila.
 */
@Composable
fun Interruptor(activo: Boolean, modifier: Modifier = Modifier, alCambiar: ((Boolean) -> Unit)? = null) {
    val colores = MaterialTheme.colorScheme
    val circulo by animateDpAsState(if (activo) 24.dp else 4.dp, label = "circulo")
    val fondo by animateColorAsState(if (activo) colores.primary else colores.outlineVariant, label = "fondo")
    val toque = if (alCambiar != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = activo, onValueChange = alCambiar, role = Role.Switch)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(toque)
            .size(width = 52.dp, height = 32.dp)
            // El color y la posición del círculo se leen al dibujar y al ubicar: la animación no recompone en cada cuadro.
            .drawBehind { drawRoundRect(color = fondo, cornerRadius = CornerRadius(size.minDimension / 2)) },
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(circulo.roundToPx(), 4.dp.roundToPx()) }
                .size(24.dp)
                .background(if (activo) colores.onPrimary else colores.surface, CircleShape),
        )
    }
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

/**
 * Tarjeta del color de un tono, sin borde (Figma 35-692, 35-720, 50-679 y 50-729): "Conexión segura"
 * en verde, "No se pudo conectar" en rojo, "El código venció o ya se usó" en ámbar. Título SemiBold
 * 14 con un ícono opcional de 18 y los textos en Regular 13, todo del color del tono; 12 de margen y
 * 10 entre piezas.
 */
@Composable
fun TarjetaTono(
    tono: Tono,
    titulo: String,
    textos: List<String>,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
) {
    Surface(color = tono.fondo, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icono != null) {
                    Icon(imageVector = icono, contentDescription = null, tint = tono.texto, modifier = Modifier.size(18.dp))
                }
                Text(text = titulo, style = MaterialTheme.typography.labelLarge, color = tono.texto)
            }
            textos.forEach { texto ->
                Text(text = texto, style = MaterialTheme.typography.bodySmall, color = tono.texto)
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

/**
 * Etiqueta gris a la izquierda y valor a la derecha ("Anómalo" va en rojo con [colorValor]). Con
 * [ayuda], la etiqueta lleva su "?" ("Conexiones en 60 s", Figma 468-674).
 */
@Composable
fun FilaDato(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    colorValor: Color = Color.Unspecified,
    ayuda: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ayuda != null) {
            EtiquetaConAyuda(
                texto = etiqueta,
                ayuda = ayuda,
                estilo = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = valor,
            style = MaterialTheme.typography.valor,
            color = colorValor,
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
