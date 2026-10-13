package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.ui.Formato
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IndicadorSeveridad(alerta.severidad)
                Spacer(modifier = Modifier.width(6.dp))
                IndicadorEstadoRevision(alerta.estadoRevision)
                Spacer(modifier = Modifier.weight(1f))
                alerta.envio?.let { envio ->
                    IndicadorEnvio(envio)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = Formato.horaYDia(alerta.inicio, zona),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
