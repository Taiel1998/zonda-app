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
    /** Si el firewall está preguntando: de eso depende lo que dice un bloqueo aprobado que todavía no aplicó. */
    firewallConectado: Boolean,
    zona: ZoneId,
    ahora: Instant,
    alAbrirAlerta: (alertaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier, espacio = 0.dp) {
        TituloSeccion(stringResource(R.string.bloqueos_titulo))
        bloqueos.forEach { bloqueo ->
            FilaNavegable(
                titulo = bloqueo.etiqueta,
                detalle = detalleDe(bloqueo, firewallConectado, zona, ahora),
                alTocar = { alAbrirAlerta(bloqueo.alertaId) },
            )
        }
    }
}

/**
 * "Bloqueado · vence 13:34", "Bloqueado hasta desbloquear", "Bloqueando…" (o "Bloqueo sin aplicar" si el
 * firewall no responde) o "Pedido de Laura · espera aprobación". "Bloqueado" sale recién cuando el
 * firewall dijo que lo aplicó.
 */
@Composable
private fun detalleDe(bloqueo: Bloqueo, firewallConectado: Boolean, zona: ZoneId, ahora: Instant): String {
    if (bloqueo.estado == EstadoBloqueo.PEDIDO) return stringResource(R.string.bloqueos_pedido_por, bloqueo.pedidoPor)
    if (bloqueo.estado == EstadoBloqueo.APROBADO) {
        return stringResource(if (firewallConectado) R.string.bloqueo_bloqueando else R.string.bloqueo_sin_aplicar)
    }
    val vence = bloqueo.venceEn ?: return stringResource(R.string.bloqueo_hasta_desbloquear_titulo)
    val cuando = if (Formato.mismoDia(vence, ahora, zona)) Formato.hora(vence, zona) else Formato.fechaYHora(vence, zona)
    return stringResource(R.string.bloqueo_vence, cuando)
}
