package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.etiquetaBarra
import ar.edu.uade.zonda.ui.theme.nota
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// Las piezas de la gente y de los equipos de la 1.0.5 (decisiones 39, 41 y 42): el círculo con la
// inicial, la fila de una persona y la etiqueta de la importancia de un equipo.

/** El círculo de 30 con la inicial SemiBold 13 en verde sobre verde claro (Figma 468-2610). */
@Composable
fun Avatar(inicial: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = inicial, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** El círculo gris con la persona de 16 de "Sin asignar" (Figma 468-510). */
@Composable
fun AvatarSinAsignar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/** "Taiel (vos)" o "Laura". */
@Composable
fun nombreDePersona(persona: Persona): String =
    if (persona.vos) stringResource(R.string.personas_vos, persona.nombreVisible) else persona.nombreVisible

/**
 * "Administrador · moto g(9) plus · hoy 14:33" (Figma 468-2614): el perfil, el teléfono y su último uso. Un
 * navegador con el tablero web empieza por "Navegador", para que no se confunda con una persona, y un
 * analista con permiso de bloquear dice "Bloquea" después del perfil (decisión 51).
 */
@Composable
fun detalleDePersona(persona: Persona, ahora: Instant, zona: ZoneId): String {
    val partes = mutableListOf<String>()
    if (persona.navegador) partes += stringResource(R.string.personas_navegador)
    partes += stringResource(persona.perfil.etiqueta())
    if (!persona.perfil.esAdministrador && persona.puedeBloquear) partes += stringResource(R.string.personas_bloquea)
    // Los teléfonos pareados antes de la 1.0.5 se llaman como el teléfono: no se repite.
    if (persona.nombre != null) partes += persona.telefono
    persona.ultimoUso?.let { partes += ultimoUso(it, ahora, zona) }
    return partes.joinToString(" · ")
}

/** "hoy 14:33", "ayer 18:02", "hace 3 días" o "24/09". */
@Composable
private fun ultimoUso(cuando: Instant, ahora: Instant, zona: ZoneId): String {
    val dias = Duration.between(cuando.atZone(zona).toLocalDate().atStartOfDay(zona), ahora.atZone(zona).toLocalDate().atStartOfDay(zona)).toDays()
    return when {
        dias <= 0 -> stringResource(R.string.personas_hoy, Formato.hora(cuando, zona))
        dias == 1L -> stringResource(R.string.personas_ayer, Formato.hora(cuando, zona))
        dias < 7 -> pluralStringResource(R.plurals.personas_hace_dias, dias.toInt(), dias.toInt())
        else -> Formato.fechaYHora(cuando, zona).substringBefore(' ')
    }
}

/**
 * Una persona de la lista (Figma 468-2609): el círculo, el nombre Medium 15 y el detalle Regular 12, con 8
 * arriba y abajo y 10 entre las piezas. Con [alTocar], la flecha a la derecha (la ve el administrador).
 */
@Composable
fun FilaPersona(
    persona: Persona,
    ahora: Instant,
    zona: ZoneId,
    modifier: Modifier = Modifier,
    alTocar: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(persona.inicial)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = nombreDePersona(persona), style = MaterialTheme.typography.titleSmall)
            Text(text = detalleDePersona(persona, ahora, zona), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (alTocar != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * La importancia de un equipo (Figma 468-3134): SemiBold 12 sin mayúsculas, esquinas de 6 y 8 por 3 de
 * margen. Crítico en azul; normal en gris cálido y poco importante en crema, los dos con letra gris.
 */
@Composable
fun EtiquetaImportancia(importancia: Importancia, modifier: Modifier = Modifier) {
    val (fondo, texto) = when (importancia) {
        Importancia.CRITICO -> Tonos.Critico.fondo to Tonos.Critico.texto
        Importancia.NORMAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        Importancia.POCO_IMPORTANTE -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = fondo, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = stringResource(importancia.etiqueta()),
            style = MaterialTheme.typography.etiquetaBarra,
            color = texto,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
