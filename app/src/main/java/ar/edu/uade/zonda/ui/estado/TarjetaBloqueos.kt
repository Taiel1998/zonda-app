package ar.edu.uade.zonda.ui.estado

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.ui.components.FilaNavegable
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TituloSeccion
import ar.edu.uade.zonda.ui.theme.Tonos

/**
 * Los equipos bloqueados y los pedidos de bloqueo abiertos de la red (decisión 51), debajo de Estado del
 * sistema, en una sola línea con cuántos hay, como la pieza Firewall del tablero (decisión 63). Con uno solo,
 * la línea lo nombra y abre su alerta, que es donde están las acciones; con varios, abre la pantalla
 * Bloqueos. Antes era un renglón por bloqueo, y con muchos la lista de alertas quedaba muy abajo. Solo
 * aparece si hay alguno.
 */
@Composable
fun TarjetaBloqueos(
    bloqueos: List<Bloqueo>,
    alAbrirAlerta: (alertaId: String) -> Unit,
    alVerBloqueos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unico = bloqueos.singleOrNull()
    Panel(modifier = modifier, espacio = 0.dp) {
        TituloSeccion(stringResource(R.string.bloqueos_titulo))
        FilaNavegable(
            titulo = resumenDeBloqueos(bloqueos),
            alTocar = { if (unico != null) alAbrirAlerta(unico.alertaId) else alVerBloqueos() },
            // El reloj si alguno todavía no está bloqueado (un pedido o uno que el firewall no aplicó); si no, el candado.
            icono = { IconoDeBloqueo(enCurso = bloqueos.any { it.estado != EstadoBloqueo.APLICADO }) },
        )
    }
}

/**
 * Lo que dice la línea, igual que la pieza Firewall del tablero: "pc-rrhh bloqueado", "Bloqueando pc-rrhh" o
 * "Pedido de bloqueo: pc-rrhh" con uno solo, y "3 bloqueados · 1 bloqueando · 1 pedido" con varios, sin las
 * partes en cero.
 */
@Composable
internal fun resumenDeBloqueos(bloqueos: List<Bloqueo>): String {
    bloqueos.singleOrNull()?.let { bloqueo ->
        return when (bloqueo.estado) {
            EstadoBloqueo.PEDIDO -> stringResource(R.string.bloqueos_uno_pedido, bloqueo.etiqueta)
            EstadoBloqueo.APROBADO -> stringResource(R.string.bloqueos_uno_bloqueando, bloqueo.etiqueta)
            else -> stringResource(R.string.bloqueos_uno_bloqueado, bloqueo.etiqueta)
        }
    }
    val bloqueados = bloqueos.count { it.estado == EstadoBloqueo.APLICADO }
    val bloqueando = bloqueos.count { it.estado == EstadoBloqueo.APROBADO }
    val pedidos = bloqueos.count { it.estado == EstadoBloqueo.PEDIDO }
    val partes = buildList {
        if (bloqueados > 0) add(pluralStringResource(R.plurals.bloqueos_cuenta_bloqueados, bloqueados, bloqueados))
        if (bloqueando > 0) add(stringResource(R.string.bloqueos_cuenta_bloqueando, bloqueando))
        if (pedidos > 0) add(pluralStringResource(R.plurals.bloqueos_cuenta_pedidos, pedidos, pedidos))
    }
    return partes.joinToString(" · ")
}

/** El reloj de arena ámbar de lo que todavía espera (un pedido o uno aprobado sin aplicar) o el candado rojo, como el detalle. */
@Composable
internal fun IconoDeBloqueo(enCurso: Boolean) {
    Icon(
        imageVector = if (enCurso) Icons.Outlined.HourglassEmpty else Icons.Outlined.Lock,
        contentDescription = null,
        tint = if (enCurso) Tonos.Media.texto else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(24.dp),
    )
}
