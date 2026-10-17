package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.tituloHoja

// Las piezas de los filtros del Figma (2-3 y las hojas 51-1805 a 51-2202): el chip, el botón de radio,
// la casilla, la fila de una opción y las hojas que los usan.

/** Una opción de una hoja: el valor que representa, su título y, si hace falta, una aclaración. */
data class Opcion<T>(
    val valor: T,
    val titulo: String,
    val detalle: String? = null,
)

private val FormaChip = RoundedCornerShape(8.dp)

/**
 * El chip del Figma: Medium 13, esquinas de 8 y 12 por 6 de margen. Elegido es verde claro con el texto
 * verde y sin borde (28 de alto); sin elegir lleva el borde gris de 1 por dentro (30 de alto), por eso
 * la fila los centra. [rol] es Button en los chips que abren una hoja y Checkbox en los que se marcan.
 */
@Composable
fun ChipOpcion(
    texto: String,
    elegido: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    rol: Role = Role.Checkbox,
) {
    val colores = MaterialTheme.colorScheme
    Text(
        text = texto,
        style = MaterialTheme.typography.opcion,
        color = if (elegido) colores.primary else colores.onSurface,
        maxLines = 1,
        modifier = modifier
            .clip(FormaChip)
            .then(if (elegido) Modifier.background(colores.secondaryContainer, FormaChip) else Modifier.border(1.dp, colores.outlineVariant, FormaChip))
            .selectable(selected = elegido, onClick = alTocar, role = rol)
            .padding(horizontal = if (elegido) 12.dp else 13.dp, vertical = if (elegido) 6.dp else 7.dp),
    )
}

/** El botón de radio del Figma: círculo de 20 con borde de 2, gris sin elegir y verde con el punto de 10. */
@Composable
fun MarcaRadio(elegida: Boolean, modifier: Modifier = Modifier) {
    val color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(20.dp)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (elegida) Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
    }
}

/** La casilla del Figma: cuadrado de 20 con esquinas de 4; marcada, verde lleno con el tilde blanco de 16. */
@Composable
fun MarcaCasilla(marcada: Boolean, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .size(20.dp)
            .then(
                if (marcada) {
                    Modifier.background(MaterialTheme.colorScheme.primary, forma)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, forma)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/** Título Medium 15 y aclaración Regular 13 gris, con 2 entre los dos. */
@Composable
private fun TextosDeOpcion(titulo: String, detalle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = titulo, style = MaterialTheme.typography.titleSmall)
        if (detalle != null) {
            Text(text = detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Una fila de una hoja con botón de radio (Figma 51-1916): 6 arriba y abajo y 12 entre la marca y el texto. */
@Composable
fun FilaRadio(opcion: Opcion<*>, elegida: Boolean, alElegir: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = elegida, onClick = alElegir, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MarcaRadio(elegida)
        TextosDeOpcion(opcion.titulo, opcion.detalle, Modifier.weight(1f))
    }
}

/** Una fila de una hoja con casilla (Figma 51-2051). */
@Composable
fun FilaCasilla(opcion: Opcion<*>, marcada: Boolean, alCambiar: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = marcada, onValueChange = alCambiar, role = Role.Checkbox)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MarcaCasilla(marcada)
        TextosDeOpcion(opcion.titulo, opcion.detalle, Modifier.weight(1f))
    }
}

/** La fila de botones de una hoja: 8 arriba y abajo y 12 entre los dos (Figma 51-2069). */
@Composable
fun BotonesDeHoja(
    alAplicar: () -> Unit,
    modifier: Modifier = Modifier,
    alLimpiar: (() -> Unit)? = null,
    /** El texto del botón lleno: "Aplicar", o "Mostrar 11 alertas" en la hoja del Resumen (Figma 463-1317). */
    textoAplicar: String = stringResource(R.string.filtro_aplicar),
) {
    Row(modifier = modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (alLimpiar != null) {
            BotonContorno(texto = stringResource(R.string.filtro_limpiar), alTocar = alLimpiar, modifier = Modifier.weight(1f), relleno = RellenoBotonAngosto)
            BotonPrincipal(texto = textoAplicar, alTocar = alAplicar, modifier = Modifier.weight(1f))
        } else {
            BotonPrincipal(texto = textoAplicar, alTocar = alAplicar)
        }
    }
}

/**
 * Hoja con opciones excluyentes y Aplicar (Figma 51-1805 y 51-2072). La elección se confirma con
 * Aplicar; cerrar la hoja la descarta.
 */
@Composable
fun <T> HojaDeOpciones(
    titulo: String,
    opciones: List<Opcion<T>>,
    seleccionada: T,
    alCerrar: () -> Unit,
    alAplicar: (T) -> Unit,
) {
    HojaInferior(alCerrar = alCerrar) {
        ContenidoHojaDeOpciones(titulo, opciones, seleccionada, alAplicar)
    }
}

/** El contenido de [HojaDeOpciones], aparte para dibujarlo en las pruebas sin la ventana de la hoja. */
@Composable
fun <T> ContenidoHojaDeOpciones(
    titulo: String,
    opciones: List<Opcion<T>>,
    seleccionada: T,
    alAplicar: (T) -> Unit,
) {
    var elegida by remember(seleccionada) { mutableStateOf(seleccionada) }
    Text(text = titulo, style = MaterialTheme.typography.tituloHoja)
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        opciones.forEach { opcion ->
            FilaRadio(opcion = opcion, elegida = elegida == opcion.valor, alElegir = { elegida = opcion.valor })
        }
    }
    BotonesDeHoja(alAplicar = { alAplicar(elegida) })
}

/**
 * Hoja con casillas que se combinan, Limpiar y Aplicar (Figma 51-1940). Limpiar vuelve a marcarlas
 * todas, que es no filtrar.
 */
@Composable
fun <T> HojaDeCasillas(
    titulo: String,
    opciones: List<Opcion<T>>,
    marcadas: Set<T>,
    alCerrar: () -> Unit,
    alAplicar: (Set<T>) -> Unit,
) {
    HojaInferior(alCerrar = alCerrar) {
        ContenidoHojaDeCasillas(titulo, opciones, marcadas, alAplicar)
    }
}

@Composable
fun <T> ContenidoHojaDeCasillas(
    titulo: String,
    opciones: List<Opcion<T>>,
    marcadas: Set<T>,
    alAplicar: (Set<T>) -> Unit,
) {
    var elegidas by remember(marcadas) { mutableStateOf(marcadas) }
    Text(text = titulo, style = MaterialTheme.typography.tituloHoja)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        opciones.forEach { opcion ->
            FilaCasilla(
                opcion = opcion,
                marcada = opcion.valor in elegidas,
                alCambiar = { marcar -> elegidas = if (marcar) elegidas + opcion.valor else elegidas - opcion.valor },
            )
        }
    }
    BotonesDeHoja(
        alAplicar = { alAplicar(elegidas) },
        alLimpiar = { elegidas = opciones.map { it.valor }.toSet() },
    )
}

/** Un grupo de chips de Más filtros (Figma 51-2313): título SemiBold 13 gris y los chips con 8 entre sí. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrupoDeChips(titulo: String, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) { contenido() }
    }
}

@Preview(showBackground = true)
@Composable
private fun PiezasDeFiltroPreview() {
    ZondaTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChipOpcion("Todas", elegido = true, alTocar = {})
                ChipOpcion("Severidad", elegido = false, alTocar = {})
            }
            FilaRadio(Opcion(1, "Sin revisar", "Las que todavía nadie marcó"), elegida = true, alElegir = {})
            FilaCasilla(Opcion(2, "Alta"), marcada = true, alCambiar = {})
            FilaCasilla(Opcion(3, "Media"), marcada = false, alCambiar = {})
        }
    }
}
