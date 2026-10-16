package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.tituloHoja
import ar.edu.uade.zonda.ui.tituloHojaAvisos

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
 * Cómo avisa Telegram cada severidad (cambio 6; Figma 51-1688 y 35-392): una fila por severidad con
 * su etiqueta, lo elegido debajo en gris y un interruptor. Apagado es "Sin aviso". Tocar la fila, o
 * prender el interruptor, abre la hoja para elegir "Con sonido" o "En silencio", que vuelve sola al
 * elegir. Un bot de Telegram solo manda con sonido o en silencio (decisión 25): la vibración y el
 * volumen los decide el teléfono. Lo usan el primer inicio y Ajustes.
 */
@Composable
fun AvisosDeTelegram(avisos: PreferenciasAvisos, alCambiar: (Severidad, ModoAviso) -> Unit, modifier: Modifier = Modifier) {
    var eligiendo by rememberSaveable { mutableStateOf<Severidad?>(null) }
    Column(modifier = modifier) {
        Severidad.entries.forEach { severidad ->
            FilaDeAviso(
                severidad = severidad,
                modo = avisos.para(severidad),
                alTocar = { eligiendo = severidad },
                alCambiarInterruptor = { prendido ->
                    if (prendido) eligiendo = severidad else alCambiar(severidad, ModoAviso.SIN_AVISO)
                },
            )
        }
    }
    eligiendo?.let { severidad ->
        HojaInferior(alCerrar = { eligiendo = null }) {
            ContenidoHojaModoAviso(
                severidad = severidad,
                elegido = avisos.para(severidad),
                alElegir = { modo ->
                    eligiendo = null
                    alCambiar(severidad, modo)
                },
            )
        }
    }
}

/** Figma 51-1688: la etiqueta de color, el modo en Regular 13 gris a 6 y el interruptor, con 8 arriba y abajo. */
@Composable
private fun FilaDeAviso(severidad: Severidad, modo: ModoAviso, alTocar: () -> Unit, alCambiarInterruptor: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.avisos_cambiar_modo), role = Role.Button, onClick = alTocar)
            .padding(vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            IndicadorSeveridad(severidad)
            Text(text = stringResource(modo.etiqueta()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Interruptor(activo = modo != ModoAviso.SIN_AVISO, alCambiar = alCambiarInterruptor)
    }
}

/**
 * La hoja "¿Cómo te avisamos las medias?" (Figma 443-3668): las dos formas en que puede avisar un bot,
 * de un toque y sin "Aplicar". Aparte para dibujarla en las pruebas sin la ventana de la hoja.
 */
@Composable
fun ColumnScope.ContenidoHojaModoAviso(severidad: Severidad, elegido: ModoAviso, alElegir: (ModoAviso) -> Unit) {
    Text(text = stringResource(severidad.tituloHojaAvisos()), style = MaterialTheme.typography.tituloHoja)
    Column(modifier = Modifier.selectableGroup()) {
        listOf(ModoAviso.SONIDO, ModoAviso.SILENCIO).forEach { modo ->
            FilaRadio(
                opcion = Opcion(valor = modo, titulo = stringResource(modo.etiqueta())),
                elegida = elegido == modo,
                alElegir = { alElegir(modo) },
            )
        }
    }
}

/** El consejo del sonido propio (Figma 51-1688): Regular 13 gris en el recuadro crema, abajo de las filas. */
@Composable
fun ConsejoSonidoDeTelegram(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp), modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.avisos_consejo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
