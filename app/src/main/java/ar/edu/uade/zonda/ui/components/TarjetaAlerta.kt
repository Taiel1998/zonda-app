package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.CalculoSeveridad
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.ui.ContextoTarjetas
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.nombreEnFrase
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.rotulo
import ar.edu.uade.zonda.ui.theme.zonda
import java.time.ZoneId

/**
 * Tarjeta de la lista de alertas (RF02; Figma, página Componentes): severidad y estado de
 * revisión arriba, con la hora y el día a la derecha y el reloj de "pendiente" si la revisión
 * todavía no viajó; origen → destino en el medio; servicio, puerto y métodos abajo. Desde la 1.0.5
 * (Figma 468-1454), el chip del responsable a la derecha de los métodos y, si el equipo destino no es
 * normal, la línea que dice cómo cambió la severidad.
 */
@Composable
fun TarjetaAlerta(
    alerta: Alerta,
    alTocar: (alertaId: String) -> Unit,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
    contexto: ContextoTarjetas = ContextoTarjetas(),
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
            val ruta = descripcionDeRuta(alerta)
            Text(
                text = "${alerta.origen.etiqueta} → ${alerta.destino.etiqueta}",
                style = MaterialTheme.typography.titleSmall,
                // TalkBack dice "De pc-rrhh a srv-sueldos" en lugar de leer la flecha.
                modifier = Modifier.semantics { contentDescription = ruta },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = Formato.servicioYPuerto(alerta.servicio, alerta.puerto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val metodos = alerta.metodos.sorted()
                if (metodos.isNotEmpty()) {
                    // TalkBack dice "detectada por IA y umbrales", como el detalle, y no las etiquetas sueltas.
                    val detectadaPor = stringResource(R.string.detalle_detectada_por, Formato.enumerar(metodos.map { stringResource(it.nombreEnFrase()) }))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.semantics { contentDescription = detectadaPor },
                    ) {
                        metodos.forEach { metodo -> EtiquetaMetodo(metodo) }
                    }
                }
                val responsable = alerta.asignacion?.takeIf { it.responsableId != null }
                if (responsable != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    ChipResponsable(if (contexto.esMia(alerta)) stringResource(R.string.tarjeta_vos) else responsable.responsable.orEmpty())
                }
            }
            contexto.calculo(alerta)?.let { LineaImportancia(it) }
        }
    }
}

/** Lo que lee TalkBack de "pc-rrhh → srv-sueldos" en la tarjeta y en el detalle: "De pc-rrhh a srv-sueldos". */
@Composable
fun descripcionDeRuta(alerta: Alerta): String = stringResource(R.string.tarjeta_ruta, alerta.origen.etiqueta, alerta.destino.etiqueta)

/** El chip del responsable (Figma 468-2150): gris cálido, esquinas de 4, la persona de 13 y el nombre Medium 12. */
@Composable
private fun ChipResponsable(nombre: String) {
    // TalkBack dice "Responsable: Vos" y no el nombre suelto.
    val descripcion = stringResource(R.string.tarjeta_responsable, nombre)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.zonda.textoNota, modifier = Modifier.size(13.dp))
            Text(
                text = nombre,
                style = MaterialTheme.typography.rotulo,
                color = MaterialTheme.zonda.textoNota,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = descripcion },
            )
        }
    }
}

/**
 * La línea de la importancia (Figma 468-1595): "Subió de media a alta: srv-facturacion es crítico." en
 * azul con la flecha hacia arriba, "Bajó de media a baja: impresora-2 es poco importante." en gris con la
 * flecha hacia abajo, o "srv-sueldos es crítico (ya era alta)." en los topes.
 */
@Composable
private fun LineaImportancia(calculo: CalculoSeveridad) {
    val critico = calculo.importancia == Importancia.CRITICO
    val color = if (critico) Tonos.Critico.texto else MaterialTheme.colorScheme.onSurfaceVariant
    val importancia = stringResource(calculo.importancia.etiqueta()).lowercase()
    val delTipo = stringResource(calculo.delTipo.etiqueta()).lowercase()
    val final = stringResource(calculo.final.etiqueta()).lowercase()
    val texto = when {
        calculo.cambio > 0 -> stringResource(R.string.tarjeta_subio, delTipo, final, calculo.equipo, importancia)
        calculo.cambio < 0 -> stringResource(R.string.tarjeta_bajo, delTipo, final, calculo.equipo, importancia)
        else -> stringResource(R.string.tarjeta_ya_era, calculo.equipo, importancia, delTipo)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = if (critico) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(text = texto, style = MaterialTheme.typography.nota, color = color)
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
        val alto = if (elAviso == null || alLado) altoRenglon else altoRenglon + debajo + elAviso.height
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
