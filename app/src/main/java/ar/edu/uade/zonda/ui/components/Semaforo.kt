package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.Tono
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import ar.edu.uade.zonda.ui.theme.etiquetaBarra
import ar.edu.uade.zonda.ui.theme.numeroContador
import ar.edu.uade.zonda.ui.theme.rotulo
import ar.edu.uade.zonda.ui.theme.tituloHoja

// Piezas de la pantalla Estado (RF01; Figma 1-3, 1-178, 1-222, 50-616 y 99-122). Las usa también el tutorial.

/**
 * Lo que va adentro del círculo del semáforo (cambio 11): cuántas alertas faltan revisar del nivel que
 * pinta el color, o una tilde si no falta ninguna. Así el estado se distingue sin depender del color.
 */
sealed interface MarcaSemaforo {
    data class Numero(val cantidad: Int) : MarcaSemaforo

    data object Tilde : MarcaSemaforo

    /** Sin datos: el círculo gris va vacío. */
    data object Nada : MarcaSemaforo
}

/**
 * Semáforo de la red: el círculo del color del estado con el número o la tilde, la etiqueta
 * ("Atención", "Sin urgencias", "Tranquila" o "Sin datos"), el título y el detalle. Rojo si hay altas
 * sin revisar, amarillo si solo quedan medias y verde si no queda ninguna de las dos; las bajas no lo
 * cambian. Sin datos, nunca verde.
 *
 * Medidas del Figma (35-263 y 1-178): esquinas de 16, 20 de margen, 12 entre la fila y el detalle,
 * círculo de 48 a 14 de los textos con el número SemiBold 20 o la tilde de 24, etiqueta SemiBold 12,
 * título SemiBold 20 y detalle Regular 13.
 */
@Composable
fun Semaforo(
    tono: Tono,
    etiqueta: String,
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier,
    marca: MarcaSemaforo = MarcaSemaforo.Nada,
    /** Tocar el recuadro filtra la lista como el contador de su color (corrección 9); sin acción, no se toca. */
    alTocar: (() -> Unit)? = null,
    /** Lo que dice TalkBack al tocarlo: "Ver las alertas altas sin revisar". */
    descripcionAccion: String? = null,
) {
    val forma = RoundedCornerShape(16.dp)
    Surface(
        color = tono.fondo,
        shape = forma,
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .then(if (alTocar != null) Modifier.clickable(onClickLabel = descripcionAccion, role = Role.Button, onClick = alTocar) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // El número ya lo dice el título: TalkBack no lo lee dos veces.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(tono.punto, CircleShape)
                        .clearAndSetSemantics {},
                ) {
                    when (marca) {
                        is MarcaSemaforo.Numero -> Text(
                            text = if (marca.cantidad > 99) "99+" else marca.cantidad.toString(),
                            style = MaterialTheme.typography.tituloHoja,
                            color = tono.sobrePunto,
                        )
                        MarcaSemaforo.Tilde -> Icon(Icons.Filled.Check, contentDescription = null, tint = tono.sobrePunto, modifier = Modifier.size(24.dp))
                        MarcaSemaforo.Nada -> Unit
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = etiqueta.uppercase(), style = MaterialTheme.typography.etiquetaBarra, color = tono.texto)
                    Text(text = titulo, style = MaterialTheme.typography.tituloHoja, color = tono.texto)
                }
            }
            Text(text = detalle, style = MaterialTheme.typography.bodySmall, color = tono.texto)
        }
    }
}

/** El borde del contador elegido (Figma 99-122): va por fuera del contenido, así que el contador crece. */
private val BordeElegido = 2.dp

/**
 * Contador de alertas sin revisar de una severidad. Tocarlo filtra la lista de la misma pantalla y
 * otro toque la vuelve a mostrar completa (RF01, CA4); elegido, lleva un borde de 2 del color del
 * punto de la severidad. Sin [alTocar] es solo de lectura, como en el tutorial.
 */
@Composable
fun ContadorSeveridad(
    severidad: Severidad,
    cantidad: Int,
    modifier: Modifier = Modifier,
    elegido: Boolean = false,
    alTocar: (() -> Unit)? = null,
) {
    val tono = severidad.tono()
    val nombre = stringResource(severidad.etiqueta())
    val descripcion = stringResource(R.string.contador_descripcion, cantidad, nombre)
    val borde = if (elegido) BorderStroke(BordeElegido, tono.punto) else null
    // Figma 35-263: 12 de margen, 2 entre el número (SemiBold 24) y el nombre (Medium 12). Elegido, el
    // borde se suma por fuera de esos 12 (99-122: el contador pasa de 70 a 74 de alto).
    val margen = if (elegido) 12.dp + BordeElegido else 12.dp
    val contenido: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(margen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = cantidad.toString(), style = MaterialTheme.typography.numeroContador, color = tono.texto)
            Text(text = nombre, style = MaterialTheme.typography.rotulo, color = tono.texto)
        }
    }
    val semantica = Modifier.semantics {
        contentDescription = descripcion
        selected = elegido
    }
    if (alTocar != null) {
        Surface(
            onClick = alTocar,
            color = tono.fondo,
            shape = RoundedCornerShape(12.dp),
            border = borde,
            modifier = modifier.then(semantica),
            content = contenido,
        )
    } else {
        Surface(
            color = tono.fondo,
            shape = RoundedCornerShape(12.dp),
            border = borde,
            modifier = modifier.then(semantica),
            content = contenido,
        )
    }
}

/**
 * Los tres contadores, uno al lado del otro, con 12 entre ellos y arriba alineados. Sin ninguno elegido
 * miden lo mismo; el elegido suma su borde (4 más de ancho y de alto) y los otros dos se achican para que
 * la fila siga ocupando el mismo ancho, como en el Figma (99-122, 99-300 y 99-442).
 */
@Composable
fun FilaContadores(
    altas: Int,
    medias: Int,
    bajas: Int,
    modifier: Modifier = Modifier,
    elegida: Severidad? = null,
    alTocar: ((Severidad) -> Unit)? = null,
) {
    val contadores = listOf(Severidad.ALTA to altas, Severidad.MEDIA to medias, Severidad.BAJA to bajas)
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            contadores.forEach { (severidad, cantidad) ->
                ContadorSeveridad(
                    severidad = severidad,
                    cantidad = cantidad,
                    elegido = elegida == severidad,
                    alTocar = alTocar?.let { { it(severidad) } },
                )
            }
        },
    ) { medibles, restricciones ->
        val entre = 12.dp.roundToPx()
        val extra = if (elegida == null) 0 else (BordeElegido * 2).roundToPx()
        val libre = restricciones.maxWidth - 2 * entre - extra
        val base = libre / 3
        // Los píxeles que sobran de dividir por tres van a los primeros, para que la fila llene el ancho.
        val anchos = contadores.mapIndexed { i, (severidad, _) ->
            base + (if (i < libre - 3 * base) 1 else 0) + (if (severidad == elegida) extra else 0)
        }
        val ubicables = medibles.mapIndexed { i, medible -> medible.measure(Constraints.fixedWidth(anchos[i])) }
        layout(restricciones.maxWidth, ubicables.maxOf { it.height }) {
            var x = 0
            ubicables.forEach { ubicable ->
                ubicable.placeRelative(x, 0)
                x += ubicable.width + entre
            }
        }
    }
}

/**
 * La leyenda de los tres colores, que se ve cuando la red está tranquila (informe, 11.6). Figma 99-49:
 * fondo crema, esquinas de 12, 14 de margen, 10 entre filas y círculos de 14 a 10 del texto Regular 13.
 */
@Composable
fun LeyendaSemaforo(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilaLeyenda(Tonos.Alta, stringResource(R.string.leyenda_rojo))
            FilaLeyenda(Tonos.Media, stringResource(R.string.leyenda_amarillo))
            FilaLeyenda(Tonos.Baja, stringResource(R.string.leyenda_verde))
        }
    }
}

@Composable
private fun FilaLeyenda(tono: Tono, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(tono.punto, CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = texto, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * La primera descarga después del pareo (Figma 50-616): "Descargando alertas…", la barra y
 * "120 de 213 · últimos 30 días · solo la primera vez". Es un [Panel] con el título Medium 15, la
 * barra de 6 con esquinas de 3 y el texto Regular 13 gris. Sin el total, la barra no tiene fin.
 */
@Composable
fun TarjetaDescarga(
    descargadas: Int,
    total: Int?,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier) {
        Text(text = stringResource(R.string.descarga_titulo), style = MaterialTheme.typography.titleSmall)
        val barra = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        if (total != null && total > 0) {
            LinearProgressIndicator(
                progress = { (descargadas.toFloat() / total).coerceIn(0f, 1f) },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = barra,
            )
        } else {
            LinearProgressIndicator(
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                modifier = barra,
            )
        }
        Text(
            text = if (total != null) {
                stringResource(R.string.descarga_progreso, descargadas, total)
            } else {
                stringResource(R.string.descarga_sin_total, descargadas)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun SemaforoAtencionPreview() {
    ZondaTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Semaforo(
                tono = Tonos.Alta,
                etiqueta = stringResource(R.string.semaforo_atencion),
                titulo = pluralStringResource(R.plurals.semaforo_altas_sin_revisar, 3, 3),
                detalle = stringResource(R.string.semaforo_ultima_alta, "5 min", "02:14"),
                marca = MarcaSemaforo.Numero(3),
            )
            FilaContadores(altas = 3, medias = 1, bajas = 0, elegida = Severidad.ALTA, alTocar = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun SemaforoTranquilaPreview() {
    ZondaTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Semaforo(
                tono = Tonos.Baja,
                etiqueta = stringResource(R.string.semaforo_tranquila),
                titulo = stringResource(R.string.semaforo_tranquila_titulo),
                detalle = stringResource(R.string.semaforo_tranquila_detalle, "02:14"),
                marca = MarcaSemaforo.Tilde,
            )
            LeyendaSemaforo()
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun SemaforoSinDatosPreview() {
    ZondaTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Semaforo(
                tono = Tonos.Neutro,
                etiqueta = stringResource(R.string.semaforo_sin_datos),
                titulo = stringResource(R.string.semaforo_sin_datos_titulo),
                detalle = stringResource(R.string.semaforo_sin_datos_detalle),
            )
            TarjetaDescarga(descargadas = 120, total = 213)
        }
    }
}
