package ar.edu.uade.zonda.ui.estado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.CampoTexto
import ar.edu.uade.zonda.ui.components.FilaNavegable
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TituloSeccion
import java.text.Normalizer
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
    var consulta by rememberSaveable { mutableStateOf("") }
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
                else -> {
                    // Desde 9, un buscador por nombre o IP, como el de Equipos del tablero; con algo escrito se queda
                    // aunque la lista se acorte.
                    if (bloqueos.size > UMBRAL_BUSCADOR || consulta.isNotBlank()) {
                        CampoTexto(
                            valor = consulta,
                            alCambiar = { consulta = it },
                            etiqueta = stringResource(R.string.bloqueos_buscar),
                            ejemplo = stringResource(R.string.bloqueos_buscar_ejemplo),
                            opcionesTeclado = KeyboardOptions(imeAction = ImeAction.Search),
                        )
                    }
                    val ordenados = bloqueosEnOrden(bloqueos).filter { it.coincideCon(consulta) }
                    if (ordenados.isEmpty()) {
                        Text(
                            text = stringResource(R.string.bloqueos_sin_coincidencias, consulta.trim()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Un grupo por estado, en el orden en que piden atención; un grupo vacío no aparece, ni su título.
                    val grupos = listOf(
                        R.string.bloqueos_grupo_pedidos to EstadoBloqueo.PEDIDO,
                        (if (firewallConectado) R.string.bloqueos_grupo_bloqueando else R.string.bloqueos_grupo_sin_aplicar) to EstadoBloqueo.APROBADO,
                        R.string.bloqueos_grupo_bloqueados to EstadoBloqueo.APLICADO,
                    )
                    grupos.forEach { (titulo, estado) ->
                        val delGrupo = ordenados.filter { it.estado == estado }
                        if (delGrupo.isNotEmpty()) {
                            GrupoDeBloqueos(stringResource(titulo), delGrupo, firewallConectado, zona, ahora, alAbrirDetalle)
                        }
                    }
                }
            }
        }
    }
}

/** Una tarjeta con el título del grupo («Esperan aprobación», «Bloqueando», «Bloqueados») y una fila por bloqueo. */
@Composable
private fun GrupoDeBloqueos(
    titulo: String,
    bloqueos: List<Bloqueo>,
    firewallConectado: Boolean,
    zona: ZoneId,
    ahora: Instant,
    alAbrirDetalle: (alertaId: String) -> Unit,
) {
    Panel(espacio = 0.dp) {
        TituloSeccion(titulo)
        bloqueos.forEach { bloqueo ->
            FilaNavegable(
                titulo = bloqueo.etiqueta,
                detalle = detalleDe(bloqueo, firewallConectado, zona, ahora),
                alTocar = { alAbrirDetalle(bloqueo.alertaId) },
                icono = { IconoDeBloqueo(enCurso = bloqueo.estado != EstadoBloqueo.APLICADO) },
            )
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
 * Desde 9 bloqueos la pantalla suma el buscador: es más o menos lo que entra en la pantalla de un teléfono. El
 * servidor acepta hasta 20 a la vez.
 */
internal const val UMBRAL_BUSCADOR = 8

/** Si [consulta] está en el nombre (sin importar tildes ni mayúsculas) o en la IP. Vacía, coinciden todos. */
internal fun Bloqueo.coincideCon(consulta: String): Boolean {
    val buscado = sinTildes(consulta.trim())
    if (buscado.isEmpty()) return true
    return ip.contains(buscado) || nombre?.let { sinTildes(it).contains(buscado) } == true
}

private val MARCAS = Regex("\\p{M}+")

/** "Dirección" y "DIRECCION" se buscan igual: sin tildes y en minúsculas. */
private fun sinTildes(texto: String): String = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(MARCAS, "").lowercase()

/**
 * Lo que agrega cada fila al título de su grupo, con la dirección delante si el equipo tiene nombre:
 * "192.168.30.50 · Hasta desbloquear". El firewall bloquea por dirección, y dos direcciones pueden tener el
 * mismo nombre en el catálogo (en la demo, "pc-rrhh" son 192.168.30.50 y 172.16.0.1): la dirección las distingue.
 */
@Composable
private fun detalleDe(bloqueo: Bloqueo, firewallConectado: Boolean, zona: ZoneId, ahora: Instant): String {
    val estado = estadoDe(bloqueo, firewallConectado, zona, ahora)
    return if (bloqueo.nombre != null) stringResource(R.string.bloqueos_fila_con_ip, bloqueo.ip, estado) else estado
}

/**
 * "Pedido de Laura"; "El firewall lo aplica en unos segundos" (o "El firewall no responde"); "Vence 13:34" (con la
 * fecha si no es hoy) o "Hasta desbloquear".
 */
@Composable
private fun estadoDe(bloqueo: Bloqueo, firewallConectado: Boolean, zona: ZoneId, ahora: Instant): String {
    if (bloqueo.estado == EstadoBloqueo.PEDIDO) return stringResource(R.string.bloqueos_fila_pedido, bloqueo.pedidoPor)
    if (bloqueo.estado == EstadoBloqueo.APROBADO) {
        return stringResource(if (firewallConectado) R.string.bloqueo_bloqueando_detalle else R.string.bloqueo_firewall_no_responde)
    }
    val vence = bloqueo.venceEn ?: return stringResource(R.string.bloqueos_fila_hasta_desbloquear)
    val cuando = if (Formato.mismoDia(vence, ahora, zona)) Formato.hora(vence, zona) else Formato.fechaYHora(vence, zona)
    return stringResource(R.string.bloqueos_fila_vence, cuando)
}
