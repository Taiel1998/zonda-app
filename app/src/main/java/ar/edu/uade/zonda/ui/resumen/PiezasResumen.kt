package ar.edu.uade.zonda.ui.resumen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.components.BotonAyuda
import ar.edu.uade.zonda.ui.components.PuntoDeSeveridad
import ar.edu.uade.zonda.ui.theme.CremaBarra
import ar.edu.uade.zonda.ui.theme.GrisCalido
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.VerdeZonda
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.rotulo

// Las piezas del Resumen (Figma 52-2629 y las variantes 100-7158 a 100-11534), medidas sobre el
// prototipo: tarjetas de totales, métricas, tarjetas con borde, el gráfico de barras y las filas con
// barra. Toda cifra que se puede tocar, elegida, lleva el borde verde de 2.

/** Verde claro de las barras del gráfico; la de hoy va en el verde base. */
val VerdeBarra = Color(0xFF5AB89A)

/** La barra de precisión de un modelo con menos de 30 revisiones (cambio 4; Figma 72-5240). */
val VerdeBarraClara = Color(0xFF9FE1CB)

private val FormaTarjeta = RoundedCornerShape(12.dp)
private val BordeElegido = 2.dp

/** Número SemiBold 22 de los totales y las métricas. */
private val estiloNumero
    @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

/** Regular 11: rótulo de los totales, detalle de las métricas y pies. */
val estiloRotuloChicoResumen
    @Composable get() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal)

/** Regular 12 con renglones de 15: las frases y notas de las tarjetas. */
val estiloNotaResumen
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp)

/**
 * Un total (Figma 52-2629): número SemiBold 22 y rótulo Regular 11 gris sobre el fondo de su tono,
 * esquinas de 12, 12 a los costados y 11 arriba y abajo. Elegido, el borde verde.
 */
@Composable
fun TarjetaTotal(
    numero: String,
    rotulo: String,
    fondo: Color,
    colorNumero: Color,
    elegida: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = fondo,
        shape = FormaTarjeta,
        border = if (elegida) BorderStroke(BordeElegido, VerdeZonda) else null,
        modifier = modifier
            .clip(FormaTarjeta)
            .selectable(selected = elegida, onClick = alTocar, role = Role.Button),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = numero, style = estiloNumero, color = colorNumero, maxLines = 1)
            Text(text = rotulo, style = estiloRotuloChicoResumen, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * Una métrica de éxito (Figma 52-2629): el valor SemiBold 22 verde, el rótulo Medium 12 y el detalle
 * Regular 11 gris. No tiene fondo ni borde; elegida, el borde verde (Figma 100-7158 y 100-7426).
 */
@Composable
fun TarjetaMetrica(
    valor: String,
    rotulo: String,
    detalle: String,
    elegida: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(FormaTarjeta)
            .then(if (elegida) Modifier.border(BordeElegido, VerdeZonda, FormaTarjeta) else Modifier)
            .selectable(selected = elegida, onClick = alTocar, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = valor, style = estiloNumero, color = VerdeZonda, maxLines = 1)
        Text(text = rotulo, style = MaterialTheme.typography.rotulo, color = MaterialTheme.colorScheme.onSurface)
        Text(text = detalle, style = estiloRotuloChicoResumen, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** El ⓘ de una tarjeta: qué dice para TalkBack y qué abre. */
data class AyudaDeTarjeta(val descripcion: String, val alTocar: () -> Unit)

/**
 * Las tarjetas blancas del Resumen (Figma 52-2629): borde gris de 1, esquinas de 12, 12 de margen más
 * el borde, el título SemiBold 13 gris y 12 hasta el contenido (10 en el gráfico). Con [ayuda], el ⓘ va
 * a la derecha del título y abre su hoja (cambios 14 y 15); en la imagen que se comparte no va.
 */
@Composable
fun TarjetaResumen(
    titulo: String,
    modifier: Modifier = Modifier,
    espacioTitulo: Dp = 12.dp,
    ayuda: AyudaDeTarjeta? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = FormaTarjeta,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (ayuda != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    BotonAyuda(descripcion = ayuda.descripcion, alTocar = ayuda.alTocar)
                }
            }
            Spacer(modifier = Modifier.height(espacioTitulo))
            contenido()
        }
    }
}

/** El chip del filtro aplicado, con la cruz para quitarlo ("Viernes 25/09 ×"; Figma 100-8096). */
@Composable
fun ChipFiltro(texto: String, descripcionQuitar: String, alQuitar: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(forma)
            .background(MaterialTheme.colorScheme.secondaryContainer, forma)
            .clickable(onClick = alQuitar, role = Role.Button)
            .semantics { contentDescription = descripcionQuitar }
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(text = texto, style = MaterialTheme.typography.opcion, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
    }
}

/** Una barra del gráfico, lista para dibujar. */
data class BarraGrafico(
    val etiqueta: String,
    val cantidad: Int,
    /** La de hoy en 7 días: verde base. */
    val resaltada: Boolean,
    val elegida: Boolean,
    /** Hay otra barra elegida: esta va atenuada (Figma 463-1145). */
    val atenuada: Boolean,
)

private val AltoMaximoBarra = 90.dp
private val AnchoBarra = 28.dp

/** Con más barras que esto (un día por hora), van más juntas y con la letra más chica (cambio 19). */
const val BARRAS_APRETADAS = 12

/**
 * Alertas por día o por semana (Figma 52-2629, 100-7024 y 463-1145): una columna por barra, todas del
 * mismo ancho con 8 entre ellas, con el número arriba (Medium 11), la barra de 28 de ancho (alto
 * proporcional, 90 la mayor, esquinas de 3) y el día abajo (Regular 11 gris), con 4 entre cada cosa.
 * Las barras en cero son una raya de 2. Las elegidas llevan el borde verde de 2, a 2 por fuera, y las
 * demás quedan al 35 %.
 */
@Composable
fun GraficoDeBarras(
    barras: List<BarraGrafico>,
    alTocar: (indice: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maximo = barras.maxOfOrNull { it.cantidad }?.coerceAtLeast(1) ?: 1
    // Hasta 7 barras, como el Figma; hasta 14 días, un poco más juntas; un día por hora, bien juntas.
    val apretadas = barras.size > BARRAS_APRETADAS
    val espacio = when {
        apretadas -> 2.dp
        barras.size > 7 -> 4.dp
        else -> 8.dp
    }
    val numero = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium).let { if (apretadas) it.copy(fontSize = 9.sp) else it }
    val etiqueta = if (apretadas) estiloRotuloChicoResumen.copy(fontSize = 9.sp) else estiloRotuloChicoResumen
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(espacio), verticalAlignment = Alignment.Bottom) {
        barras.forEachIndexed { indice, barra ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (barra.atenuada) 0.35f else 1f)
                    .marcoElegido(barra.elegida, horizontal = 2.dp, vertical = 2.dp)
                    .selectable(selected = barra.elegida, role = Role.Button) { alTocar(indice) },
            ) {
                Text(
                    text = barra.cantidad.toString(),
                    style = numero,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                val alto = AltoMaximoBarra * (barra.cantidad.toFloat() / maximo)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = AnchoBarra)
                        .height(if (barra.cantidad == 0) 2.dp else alto.coerceAtLeast(2.dp))
                        .background(color = if (barra.resaltada) VerdeZonda else VerdeBarra, shape = RoundedCornerShape(3.dp)),
                )
                Text(
                    text = barra.etiqueta,
                    style = etiqueta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** La barra más larga llega al 95 % de la pista, como en el Figma. */
private const val FRACCION_MAXIMA = 0.95f

/**
 * Una fila de "Reales y falsos positivos" (Figma 52-2629): el rótulo Regular 13 en 150 de ancho, la
 * pista gris de 10 con la barra proporcional a la mayor de la tarjeta y el valor Medium 13 a la derecha
 * en 28. En cero queda una marca de 2 al comienzo de la pista.
 */
@Composable
fun FilaConBarra(
    rotulo: String,
    valor: Int,
    maximo: Int,
    color: Color,
    elegida: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .marcoElegido(elegida)
            .selectable(selected = elegida, onClick = alTocar, role = Role.Button),
    ) {
        Text(text = rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.width(150.dp))
        Spacer(modifier = Modifier.width(8.dp))
        PistaConBarra(fraccion = fraccionDe(valor, maximo), color = color, alto = 10.dp, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        ValorDeFila(valor = valor, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Una fila de "Por tipo de ataque" (cambio 14; Figma 52-2629): el punto de la severidad elegida para
 * el tipo y el tipo Regular 13, los dos en 168; la pista gris de 10 con la barra proporcional a la
 * mayor de la tarjeta y el valor Medium 13 a la derecha en 28. En cero, el texto va gris y la pista
 * queda vacía.
 */
@Composable
fun FilaTipo(
    rotulo: String,
    severidad: Severidad,
    valor: Int,
    maximo: Int,
    elegida: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (valor == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .marcoElegido(elegida)
            .selectable(selected = elegida, onClick = alTocar, role = Role.Button),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(168.dp),
        ) {
            PuntoDeSeveridad(severidad)
            Text(text = rotulo, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        PistaConBarra(fraccion = fraccionDe(valor, maximo), color = VerdeZonda, alto = 10.dp, modifier = Modifier.weight(1f), marcaEnCero = false)
        Spacer(modifier = Modifier.width(8.dp))
        ValorDeFila(valor = valor, color = color)
    }
}

private fun fraccionDe(valor: Int, maximo: Int): Float = if (maximo == 0) 0f else valor.toFloat() / maximo * FRACCION_MAXIMA

/** El número de una fila con barra: Medium 13 a la derecha, en 28 (Figma 52-2629). */
@Composable
private fun ValorDeFila(valor: Int, color: Color) {
    Text(
        text = valor.toString(),
        style = MaterialTheme.typography.opcion,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(28.dp),
    )
}

/**
 * Una fila de la precisión (Figma 52-2629): el modelo Regular 14 en 152, la pista de 8 con la
 * precisión, el porcentaje SemiBold 13 en 46 y "27 de 33" Regular 12 gris en 54, los dos a la
 * derecha. Sin revisiones de ese modelo, una raya. Con menos de 30 revisiones la barra va [clara]
 * (cambio 4): ese porcentaje todavía no se puede comparar.
 */
@Composable
fun FilaPrecision(
    rotulo: String,
    porcentaje: String,
    xDeY: String,
    fraccion: Float,
    elegida: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    clara: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .marcoElegido(elegida)
            .selectable(selected = elegida, onClick = alTocar, role = Role.Button),
    ) {
        Text(text = rotulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.width(152.dp))
        Spacer(modifier = Modifier.width(8.dp))
        PistaConBarra(fraccion = fraccion, color = if (clara) VerdeBarraClara else VerdeZonda, alto = 8.dp, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = porcentaje,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(46.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = xDeY,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.width(54.dp),
        )
    }
}

/** "Cobertura de revisión · 73 % · 33 de 45" en el recuadro crema (Figma 52-2629), que también se toca. */
@Composable
fun CajaCobertura(valor: String, rotulo: String, elegida: Boolean, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(CremaBarra, forma)
            .then(if (elegida) Modifier.border(BordeElegido, VerdeZonda, forma) else Modifier)
            .selectable(selected = elegida, onClick = alTocar, role = Role.Button)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text = rotulo, style = MaterialTheme.typography.opcion, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(text = valor, style = MaterialTheme.typography.labelMedium, color = VerdeZonda, maxLines = 1)
    }
}

/** La pista gris con la barra de color; con [fraccion] cero, una marca de 2 al comienzo si [marcaEnCero]. */
@Composable
private fun PistaConBarra(fraccion: Float, color: Color, alto: Dp, modifier: Modifier = Modifier, marcaEnCero: Boolean = true) {
    val forma = RoundedCornerShape(alto / 2)
    Box(
        modifier = modifier
            .height(alto)
            .clip(forma)
            .background(GrisCalido, forma),
    ) {
        if (fraccion > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraccion.coerceIn(0f, 1f))
                    .background(color, forma),
            )
        } else if (marcaEnCero) {
            Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(color))
        }
    }
}

/**
 * El borde verde de lo elegido, dibujado un poco por fuera para que no tape el texto (Figma 100-8498):
 * no cambia el lugar que ocupa la pieza.
 */
private fun Modifier.marcoElegido(elegida: Boolean, horizontal: Dp = 4.dp, vertical: Dp = 3.dp): Modifier =
    if (!elegida) {
        this
    } else {
        drawWithContent {
            drawContent()
            val grosor = BordeElegido.toPx()
            val h = horizontal.toPx()
            val v = vertical.toPx()
            drawRoundRect(
                color = VerdeZonda,
                topLeft = Offset(-h + grosor / 2, -v + grosor / 2),
                size = Size(size.width + 2 * h - grosor, size.height + 2 * v - grosor),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(grosor),
            )
        }
    }

/** El naranja de los falsos positivos (Figma 52-2629). */
val NaranjaFalsos: Color = Tonos.Media.punto
