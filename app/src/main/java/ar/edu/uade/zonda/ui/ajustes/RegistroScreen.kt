package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.TipoCambio
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.nombreCorto
import ar.edu.uade.zonda.ui.theme.rotulo
import ar.edu.uade.zonda.ui.theme.valor
import ar.edu.uade.zonda.ui.theme.zonda
import java.time.Instant
import java.time.ZoneId

/**
 * Ajustes → Registro de cambios (corrección 3, decisión 40; Figma 468-3283): quién cambió la
 * configuración de la red, cuándo, de qué a qué y por qué, de lo más nuevo a lo más viejo. Lo guarda el
 * servidor y desde la app no se puede borrar ni editar. Usa el ViewModel de Ajustes.
 */
@Composable
fun RegistroScreen(viewModel: AjustesViewModel, alVolver: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    RegistroContenido(red = estado.redActiva?.nombre.orEmpty(), registro = estado.registro, alVolver = alVolver)
}

@Composable
fun RegistroContenido(
    red: String,
    registro: List<CambioRegistro>,
    alVolver: () -> Unit,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
) {
    Scaffold(topBar = { BarraSuperior(titulo = stringResource(R.string.registro_titulo), alVolver = alVolver) }) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MargenAjustes),
            verticalArrangement = EntreTarjetas,
        ) {
            Text(text = stringResource(R.string.registro_intro, red), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Panel(espacio = 12.dp) {
                if (registro.isEmpty()) {
                    Text(text = stringResource(R.string.registro_vacio), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                registro.forEachIndexed { indice, cambio ->
                    if (indice > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                    FilaCambio(cambio, zona, ahora)
                }
            }
        }
    }
}

/** Una línea (Figma 468-3324): el ícono de 20, "Hoy 10:05 · Taiel", qué cambió y el motivo entre comillas. */
@Composable
private fun FilaCambio(cambio: CambioRegistro, zona: ZoneId, ahora: Instant) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(imageVector = cambio.tipo.icono(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${cuandoDelCambio(cambio.fecha, ahora, zona)} · ${cambio.autor}",
                style = MaterialTheme.typography.rotulo,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = tituloDelCambio(cambio), style = MaterialTheme.typography.valor)
            cambio.motivo?.takeIf { it.isNotBlank() }?.let { motivo ->
                Text(text = stringResource(R.string.registro_motivo, motivo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.zonda.textoNota)
            }
        }
    }
}

/** Un ícono por tipo de cambio (Figma 468-3325): severidad, equipo, perfil, persona nueva y persona quitada. */
private fun TipoCambio.icono(): ImageVector = when (this) {
    TipoCambio.SEVERIDAD -> Icons.Outlined.Tune
    TipoCambio.IMPORTANCIA -> Icons.Outlined.Dns
    TipoCambio.PERFIL -> Icons.Outlined.Person
    TipoCambio.PERSONA_NUEVA -> Icons.Outlined.PersonAdd
    TipoCambio.PERSONA_QUITADA -> Icons.Outlined.PersonRemove
}

/** "Hoy 10:05", "Ayer 18:00" o "24/09 18:00". */
@Composable
private fun cuandoDelCambio(fecha: Instant, ahora: Instant, zona: ZoneId): String = when {
    Formato.mismoDia(fecha, ahora, zona) -> stringResource(R.string.registro_hoy, Formato.hora(fecha, zona))
    Formato.ayer(fecha, ahora, zona) -> stringResource(R.string.registro_ayer, Formato.hora(fecha, zona))
    else -> Formato.fechaYHora(fecha, zona)
}

/**
 * Qué cambió, con palabras (Taiel no entendió la flecha): "Fuerza bruta pasó de alta a media",
 * "srv-sueldos pasó de normal a crítico", "Meli pasó de analista a administrador", "Sumó a Laura al
 * equipo como analista" o "Quitó a Laura del equipo".
 */
@Composable
fun tituloDelCambio(cambio: CambioRegistro): String {
    val nombre = cambio.nombre ?: cambio.objeto
    return when (cambio.tipo) {
        TipoCambio.SEVERIDAD -> pasoDe(
            cambio.tipoDeAtaque?.let { stringResource(it.nombreCorto()) } ?: nombre,
            cambio.severidadAntes?.let { stringResource(it.etiqueta()).lowercase() },
            cambio.severidadDespues?.let { stringResource(it.etiqueta()).lowercase() } ?: cambio.despues.orEmpty(),
        )
        TipoCambio.IMPORTANCIA -> pasoDe(
            nombre,
            cambio.importanciaAntes?.let { stringResource(it.etiqueta()).lowercase() },
            cambio.importanciaDespues?.let { stringResource(it.etiqueta()).lowercase() } ?: cambio.despues.orEmpty(),
        )
        TipoCambio.PERFIL -> pasoDe(
            nombre,
            cambio.perfilAntes?.let { stringResource(it.etiqueta()).lowercase() },
            cambio.perfilDespues?.let { stringResource(it.etiqueta()).lowercase() } ?: cambio.despues.orEmpty(),
        )
        TipoCambio.PERSONA_NUEVA -> stringResource(
            R.string.registro_persona_nueva,
            nombre,
            cambio.perfilDespues?.let { stringResource(it.etiqueta()).lowercase() } ?: cambio.despues.orEmpty(),
        )
        TipoCambio.PERSONA_QUITADA ->
            if (cambio.autorId != null && cambio.autorId == cambio.objeto) {
                stringResource(R.string.registro_se_quito)
            } else {
                stringResource(R.string.registro_persona_quitada, nombre)
            }
    }
}

@Composable
private fun pasoDe(nombre: String, antes: String?, despues: String): String =
    if (antes == null) stringResource(R.string.registro_paso_a, nombre, despues) else stringResource(R.string.registro_paso_de, nombre, antes, despues)
