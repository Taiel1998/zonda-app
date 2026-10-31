package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.AccionResponsable
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.CalculoSeveridad
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.Avatar
import ar.edu.uade.zonda.ui.components.AvatarSinAsignar
import ar.edu.uade.zonda.ui.components.BotonChico
import ar.edu.uade.zonda.ui.components.EtiquetaImportancia
import ar.edu.uade.zonda.ui.components.IndicadorSeveridad
import ar.edu.uade.zonda.ui.components.MarcaRadio
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TituloSeccion
import ar.edu.uade.zonda.ui.components.nombreDePersona
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.nombreCorto
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloHoja
import java.time.Instant
import java.time.ZoneId

/**
 * "Responsable" (decisión 42; Figma 468-507, 468-880 y 468-983), debajo del encabezado: "Sin asignar" o
 * la persona con quién y cuándo se la asignó, y el botón que le toca a cada perfil. El administrador
 * asigna o cambia; el analista se asigna una libre o suelta la suya, y en la de otra persona no tiene
 * botón. Asignar no traba nada: cualquiera la puede revisar.
 */
@Composable
fun TarjetaResponsable(
    asignacion: Asignacion?,
    perfil: Perfil,
    miId: String?,
    zona: ZoneId,
    ahora: Instant,
    alAbrirAsignar: () -> Unit,
    alAsignarme: () -> Unit,
    alSoltar: () -> Unit,
) {
    Panel {
        TituloSeccion(stringResource(R.string.detalle_responsable))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (asignacion?.responsableId == null) {
                AvatarSinAsignar()
                Text(
                    text = stringResource(R.string.detalle_sin_asignar),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                val nombre = asignacion.responsable.orEmpty()
                Avatar(nombre.trim().firstOrNull()?.uppercase() ?: "?")
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (asignacion.responsableId == miId) stringResource(R.string.personas_vos, nombre) else nombre,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = quienYCuando(asignacion, miId, zona, ahora),
                        style = MaterialTheme.typography.nota,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (AccionResponsable.para(perfil, asignacion?.responsableId, miId)) {
                AccionResponsable.ASIGNAR -> BotonChico(stringResource(R.string.detalle_asignar), alAbrirAsignar)
                AccionResponsable.CAMBIAR -> BotonChico(stringResource(R.string.detalle_cambiar_responsable), alAbrirAsignar)
                AccionResponsable.ASIGNARME -> if (miId != null) BotonChico(stringResource(R.string.detalle_me_la_asigno), alAsignarme)
                AccionResponsable.SOLTARLA -> BotonChico(stringResource(R.string.detalle_soltarla), alSoltar)
                AccionResponsable.NINGUNA -> Unit
            }
        }
    }
}

/** "Asignada por Taiel · hoy 10:05", "Asignada por vos · hoy 10:05", "Te la asignaste · 10:05" o "Se la asignó · 10:05". */
@Composable
private fun quienYCuando(asignacion: Asignacion, miId: String?, zona: ZoneId, ahora: Instant): String {
    val cuando = "${Formato.diaRelativo(asignacion.fecha, ahora, zona)} ${Formato.hora(asignacion.fecha, zona)}"
    return when {
        asignacion.propia && asignacion.porId == miId -> stringResource(R.string.detalle_te_la_asignaste, cuando)
        asignacion.propia -> stringResource(R.string.detalle_se_la_asigno, cuando)
        asignacion.porId == miId -> stringResource(R.string.detalle_asignada_por_vos, cuando)
        else -> stringResource(R.string.detalle_asignada_por, asignacion.por, cuando)
    }
}

/**
 * "Asignar a" (Figma 468-726): la gente de la red con su perfil; elegir a alguien la asigna y cierra la
 * hoja. Si ya estaba asignada, abajo "Sacar la asignación". Los navegadores con el tablero web no están:
 * no son una persona y no reciben el aviso de la asignación (decisión 51).
 */
@Composable
fun ColumnScope.ContenidoHojaAsignar(
    personas: List<Persona>,
    responsableId: String?,
    alElegir: (Persona?) -> Unit,
) {
    Text(text = stringResource(R.string.asignar_titulo), style = MaterialTheme.typography.tituloHoja)
    Text(text = stringResource(R.string.asignar_texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(modifier = Modifier.selectableGroup()) {
        personas.filterNot { it.navegador }.forEach { persona ->
            val elegida = persona.id == responsableId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = elegida, onClick = { alElegir(persona) }, role = Role.RadioButton)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MarcaRadio(elegida)
                Avatar(persona.inicial)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = nombreDePersona(persona), style = MaterialTheme.typography.titleSmall)
                    Text(text = stringResource(persona.perfil.etiqueta()), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (responsableId != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { alElegir(null) }, role = Role.Button)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Text(text = stringResource(R.string.asignar_sacar), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * "Cómo se calculó la severidad" (decisión 41; Figma 468-1181): el tipo de ataque con su severidad, el
 * equipo destino con su importancia y lo que hace, y el resultado. Aparece solo si el equipo no es normal.
 */
@Composable
fun TarjetaCalculo(calculo: CalculoSeveridad, alerta: Alerta) {
    val tipos = alerta.tiposDeAtaque.map { stringResource(it.nombreCorto()).lowercase() }
    val efecto = when {
        calculo.cambio > 0 -> stringResource(R.string.calculo_sube)
        calculo.cambio < 0 -> stringResource(R.string.calculo_baja)
        else -> stringResource(R.string.calculo_ya_era, stringResource(calculo.delTipo.etiqueta()).lowercase())
    }
    Panel {
        TituloSeccion(stringResource(R.string.calculo_titulo))
        FilaCalculo(stringResource(R.string.calculo_tipo, tipos.joinToString(" y "))) { IndicadorSeveridad(calculo.delTipo) }
        FilaCalculo(stringResource(R.string.calculo_equipo, calculo.equipo, efecto)) { EtiquetaImportancia(calculo.importancia) }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.calculo_resultado), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            IndicadorSeveridad(calculo.final)
        }
    }
}

@Composable
private fun FilaCalculo(texto: String, etiqueta: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        etiqueta()
    }
}
