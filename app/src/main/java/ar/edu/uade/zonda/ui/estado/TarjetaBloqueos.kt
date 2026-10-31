package ar.edu.uade.zonda.ui.estado

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.FilaNavegable
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TituloSeccion
import java.time.Instant
import java.time.ZoneId

/**
 * Los equipos bloqueados y los pedidos de bloqueo abiertos de la red (decisión 51), debajo de Estado del
 * sistema. Solo aparece si hay alguno. Cada fila abre la alerta del bloqueo, que es donde están las acciones.
 */
@Composable
fun TarjetaBloqueos(
    bloqueos: List<Bloqueo>,
    zona: ZoneId,
    ahora: Instant,
    alAbrirAlerta: (alertaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier, espacio = 0.dp) {
        TituloSeccion(stringResource(R.string.bloqueos_titulo))
        bloqueos.forEach { bloqueo ->
            FilaNavegable(titulo = bloqueo.etiqueta, detalle = detalleDe(bloqueo, zona, ahora), alTocar = { alAbrirAlerta(bloqueo.alertaId) })
        }
    }
}

/** "Bloqueado · vence 13:34", "Bloqueado hasta desbloquear" o "Pedido de Laura · espera aprobación". */
@Composable
private fun detalleDe(bloqueo: Bloqueo, zona: ZoneId, ahora: Instant): String {
    if (bloqueo.estado == EstadoBloqueo.PEDIDO) return stringResource(R.string.bloqueos_pedido_por, bloqueo.pedidoPor)
    val vence = bloqueo.venceEn ?: return stringResource(R.string.bloqueo_hasta_desbloquear_titulo)
    val cuando = if (Formato.mismoDia(vence, ahora, zona)) Formato.hora(vence, zona) else Formato.fechaYHora(vence, zona)
    return stringResource(R.string.bloqueo_vence, cuando)
}
