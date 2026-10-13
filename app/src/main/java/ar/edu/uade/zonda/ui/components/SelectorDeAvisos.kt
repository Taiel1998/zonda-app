package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.opcion

/**
 * Una opción para elegir (Figma 51-1688 y 66-3212): 28 de alto, esquinas de 8 y texto Medium 13. La
 * elegida va en verde claro con texto verde; las otras, blancas con borde gris.
 */
@Composable
fun Opcion(texto: String, elegida: Boolean, alElegir: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(8.dp)
    val fondo = if (elegida) {
        Modifier.background(MaterialTheme.colorScheme.secondaryContainer, forma)
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surface, forma)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(forma)
            .then(fondo)
            .selectable(selected = elegida, onClick = alElegir, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.opcion,
            color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Cómo avisa Telegram cada severidad: con sonido, vibración o sin aviso (Figma 35-392 y 51-1688).
 * Cada severidad lleva su etiqueta y tres opciones del mismo ancho, con 10 entre piezas. Lo usan el
 * primer inicio y Ajustes.
 */
@Composable
fun SelectorDeAvisos(avisos: PreferenciasAvisos, alCambiar: (Severidad, ModoAviso) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Severidad.entries.forEach { severidad ->
            IndicadorSeveridad(severidad)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ModoAviso.entries.forEach { modo ->
                    Opcion(
                        texto = stringResource(modo.etiqueta()),
                        elegida = avisos.para(severidad) == modo,
                        alElegir = { alCambiar(severidad, modo) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
