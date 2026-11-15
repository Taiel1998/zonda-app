package ar.edu.uade.zonda.ui.estado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.FilaNavegable
import ar.edu.uade.zonda.ui.components.Panel
import java.time.Instant
import java.time.ZoneId

/**
 * Bloqueos (decisión 63): los equipos bloqueados y los pedidos de bloqueo de la red activa, cada uno con lo
 * que importa de él: si espera aprobación, si el firewall lo está aplicando o hasta cuándo dura. Se abre
 * desde la línea de Estado cuando hay más de uno, y cada fila abre la alerta del bloqueo, que es donde están
 * las acciones. Usa el ViewModel de Estado, que queda abajo y pregunta al servidor cada 20 s: la lista
 * aparece enseguida y se actualiza sola.
 */
@Composable
fun BloqueosScreen(
    viewModel: EstadoViewModel,
    alAbrirDetalle: (alertaId: String) -> Unit,
    alVolver: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ahora by relojDeLaPantalla()
    val sistema = estado.sistema
    BloqueosContenido(
        bloqueos = sistema?.bloqueos,
        firewallConectado = sistema?.firewall == EstadoPieza.FUNCIONA,
        alAbrirDetalle = alAbrirDetalle,
        alVolver = alVolver,
        ahora = ahora,
    )
}

/** La pantalla sin ViewModel, para probarla con Compose. [bloqueos] es null mientras el servidor no contestó. */
@Composable
fun BloqueosContenido(
    bloqueos: List<Bloqueo>?,
    /** Si el firewall está preguntando: de eso depende lo que dice un bloqueo aprobado que todavía no aplicó. */
    firewallConectado: Boolean,
    alAbrirDetalle: (alertaId: String) -> Unit,
    alVolver: () -> Unit,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
) {
    Scaffold(topBar = { BarraSuperior(titulo = stringResource(R.string.bloqueos_titulo), alVolver = alVolver) }) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                bloqueos == null -> Unit
                // Se desbloqueó el último mientras la pantalla estaba abierta.
                bloqueos.isEmpty() -> Text(
                    text = stringResource(R.string.bloqueos_vacio),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Panel(espacio = 0.dp) {
                    bloqueosEnOrden(bloqueos).forEach { bloqueo ->
                        FilaNavegable(
                            titulo = bloqueo.etiqueta,
                            detalle = detalleDe(bloqueo, firewallConectado, zona, ahora),
                            alTocar = { alAbrirDetalle(bloqueo.alertaId) },
                            icono = { IconoDeBloqueo(enCurso = bloqueo.estado != EstadoBloqueo.APLICADO) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Primero los pedidos, que esperan a alguien (el más viejo arriba); después los aprobados que el firewall está
 * aplicando, y al final los bloqueados: el que vence antes arriba y los que duran hasta desbloquear al final.
 */
internal fun bloqueosEnOrden(bloqueos: List<Bloqueo>): List<Bloqueo> = bloqueos.sortedWith(
    compareBy<Bloqueo>(
        { grupoDe(it.estado) },
        { if (it.estado == EstadoBloqueo.APLICADO) it.venceEn ?: Instant.MAX else it.pedidoEn },
        { it.etiqueta },
    ),
)

private fun grupoDe(estado: EstadoBloqueo): Int = when (estado) {
    EstadoBloqueo.PEDIDO -> 0
    EstadoBloqueo.APROBADO -> 1
    else -> 2
}

/**
 * "Pedido de Laura · espera aprobación", "Bloqueando…" (o "Bloqueo sin aplicar" si el firewall no responde),
 * "Bloqueado · vence 13:34" (con la fecha si no es hoy) o "Bloqueado hasta desbloquear". "Bloqueado" sale
 * recién cuando el firewall dijo que lo aplicó.
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
