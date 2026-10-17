package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.zonda
import kotlinx.coroutines.launch

/**
 * El "?" que explica un término con un globito de una frase (correcciones 5 y 6; Figma 468-627): el
 * ícono de ayuda de Android de 18, gris, y arriba el `PlainTooltip` de Material 3 con su flechita, de
 * hasta 200 de ancho, gris oscuro como el aviso inferior y con letra de 12. Ocupa lo que se ve y el
 * toque cercano completa los 48 dp, como [BotonAyuda]. Uno por vez: todos los globitos comparten el
 * candado de Material, así que abrir otro cierra el anterior; tocar en cualquier otro lado lo cierra.
 * Con TalkBack el "?" se lee "Qué es [termino]" y el globito se anuncia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyudaGlobito(termino: String, ayuda: String, modifier: Modifier = Modifier) {
    val estado = rememberTooltipState(isPersistent = true)
    val alcance = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above, SEPARACION_GLOBITO),
        tooltip = {
            PlainTooltip(
                caretShape = TooltipDefaults.caretShape(TAMANIO_FLECHITA),
                shape = RoundedCornerShape(4.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.zonda.textoGlobito,
            ) {
                // Material deja 4 arriba y abajo; el Figma, 6.
                Text(text = ayuda, style = MaterialTheme.typography.nota, modifier = Modifier.padding(vertical = 2.dp))
            }
        },
        state = estado,
        modifier = modifier,
        // Se abre con un toque en el "?", no manteniendo apretado.
        enableUserInput = false,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.ayuda_que_es, termino),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { alcance.launch { estado.show() } },
        )
    }
}

/** Un término con su "?" a 4 de distancia ("Etiqueta con ayuda" del Figma 468-675). */
@Composable
fun EtiquetaConAyuda(
    texto: String,
    ayuda: String,
    estilo: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = texto, style = estilo, color = color)
        AyudaGlobito(termino = texto, ayuda = ayuda)
    }
}

/** El cuerpo del globito queda a 8 del "?" y la flechita de 12 por 6 llega hasta 2 de él (Figma 468-722 y 468-724). */
private val SEPARACION_GLOBITO = 8.dp
private val TAMANIO_FLECHITA = DpSize(12.dp, 6.dp)
