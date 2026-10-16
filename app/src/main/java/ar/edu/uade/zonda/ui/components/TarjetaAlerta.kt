package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.theme.nota
import java.time.ZoneId

/**
 * Tarjeta de la lista de alertas (RF02; Figma, página Componentes): severidad y estado de
 * revisión arriba, con la hora y el día a la derecha y el reloj de "pendiente" si la revisión
 * todavía no viajó; origen → destino en el medio; servicio, puerto y métodos abajo.
 */
@Composable
fun TarjetaAlerta(
    alerta: Alerta,
    alTocar: (alertaId: String) -> Unit,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
) {
    // Figma 35-363: borde gris por dentro, esquinas de 12, 12 de margen más el borde y 8 entre filas.
    OutlinedCard(
        onClick = { alTocar(alerta.id) },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimerRenglon(alerta, zona)
            Text(
                text = "${alerta.origen.etiqueta} → ${alerta.destino.etiqueta}",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = Formato.servicioYPuerto(alerta.servicio, alerta.puerto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                alerta.metodos.sorted().forEach { metodo -> EtiquetaMetodo(metodo) }
            }
        }
    }
}

/**
 * El primer renglón (Figma 35-363): las etiquetas a la izquierda y la hora a la derecha, siempre
 * entera. El aviso del envío va pegado a la hora, como "pendiente" en el Figma; si no entra (con
 * "revisada por otra persona" y un falso positivo, por ejemplo), baja al renglón de abajo, a la
 * izquierda. Antes la hora se quedaba sin lugar y se partía letra por letra.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrimerRenglon(alerta: Alerta, zona: ZoneId) {
    Layout(
        contents = listOf(
            {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IndicadorSeveridad(alerta.severidad)
                    IndicadorEstadoRevision(alerta.estadoRevision)
                }
            },
            { alerta.envio?.let { IndicadorEnvio(it) } },
            {
                Text(
                    text = Formato.horaYDia(alerta.inicio, zona),
                    style = MaterialTheme.typography.nota,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            },
        ),
    ) { (etiquetas, aviso, hora), restricciones ->
        val ancho = restricciones.maxWidth
        val separacion = 8.dp.roundToPx()
        val laHora = hora.first().measure(Constraints())
        val lasEtiquetas = etiquetas.first().measure(Constraints(maxWidth = (ancho - laHora.width - separacion).coerceAtLeast(0)))
        val elAviso = aviso.firstOrNull()?.measure(Constraints(maxWidth = ancho))
        val alLado = elAviso == null || lasEtiquetas.width + elAviso.width + laHora.width + 2 * separacion <= ancho
        val altoRenglon = maxOf(lasEtiquetas.height, laHora.height, if (alLado) elAviso?.height ?: 0 else 0)
        val debajo = 6.dp.roundToPx()
        val alto = if (alLado || elAviso == null) altoRenglon else altoRenglon + debajo + elAviso.height
        layout(ancho, alto) {
            lasEtiquetas.placeRelative(0, (altoRenglon - lasEtiquetas.height) / 2)
            laHora.placeRelative(ancho - laHora.width, (altoRenglon - laHora.height) / 2)
            if (elAviso != null) {
                if (alLado) {
                    elAviso.placeRelative(ancho - laHora.width - separacion - elAviso.width, (altoRenglon - elAviso.height) / 2)
                } else {
                    elAviso.placeRelative(0, altoRenglon + debajo)
                }
            }
        }
    }
}
