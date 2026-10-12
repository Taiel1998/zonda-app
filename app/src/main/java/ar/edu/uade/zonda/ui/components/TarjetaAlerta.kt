package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    OutlinedCard(
        onClick = { alTocar(alerta.id) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${alerta.origen.etiqueta} → ${alerta.destino.etiqueta}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Formato.servicioYPuerto(alerta.servicio, alerta.puerto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                alerta.metodos.sorted().forEach { metodo ->
                    Spacer(modifier = Modifier.width(6.dp))
                    EtiquetaMetodo(metodo)
                }
            }
        }
    }
}
