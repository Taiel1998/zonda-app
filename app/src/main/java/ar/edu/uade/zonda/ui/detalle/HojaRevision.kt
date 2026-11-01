package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.ui.comentarioPropuesto
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.CampoTexto
import ar.edu.uade.zonda.ui.components.ChipOpcion
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.components.LeyendaObligatorio
import ar.edu.uade.zonda.ui.components.RellenoBotonAngosto
import ar.edu.uade.zonda.ui.components.descripcionDeCampo
import ar.edu.uade.zonda.ui.components.etiquetaConAsterisco
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.etiquetaBarra
import ar.edu.uade.zonda.ui.theme.tituloHoja

/** Hoja inferior Revisar (Figma 66-2769 a 66-2942). Cerrarla deslizando equivale a Cancelar. */
@Composable
fun HojaRevision(formulario: FormularioRevision, alerta: Alerta, acciones: AccionesDetalle) {
    HojaInferior(alCerrar = acciones.alCancelarRevision) {
        ContenidoHojaRevision(formulario, alerta, acciones)
    }
}

/**
 * El contenido de la hoja: cada pieza va directo en la columna de la hoja, con 12 entre piezas como
 * en el Figma. Aparte para dibujarlo en las pruebas sin la ventana de la hoja.
 */
@Composable
fun ContenidoHojaRevision(formulario: FormularioRevision, alerta: Alerta, acciones: AccionesDetalle) {
    val resultado = formulario.resultado
    if (formulario.paso == PasoRevision.MOTIVO && resultado != null) {
        PasoMotivo(formulario, resultado, alerta, acciones)
    } else {
        PasoResultado(formulario, acciones)
    }
}

/** Figma 66-2834: título, "Paso 1 de 2", las dos opciones y Cancelar; sin el párrafo de explicación (decisión 48). */
@Composable
private fun PasoResultado(formulario: FormularioRevision, acciones: AccionesDetalle) {
    Text(text = stringResource(R.string.revisar_paso1_titulo), style = MaterialTheme.typography.tituloHoja, modifier = Modifier.semantics { heading() })
    Text(
        text = stringResource(R.string.revisar_paso_1_de_2),
        style = MaterialTheme.typography.etiquetaBarra,
        color = MaterialTheme.colorScheme.primary,
        // Al pasar de un paso al otro cambia todo el contenido de la hoja: TalkBack anuncia en cuál está.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    OpcionResultado(
        titulo = stringResource(R.string.resultado_ataque_real),
        detalle = stringResource(R.string.revisar_ataque_real_detalle),
        color = Tonos.Alta.texto,
        elegida = formulario.resultado == ResultadoRevision.ATAQUE_REAL,
        alElegir = { acciones.alElegirResultado(ResultadoRevision.ATAQUE_REAL) },
    )
    OpcionResultado(
        titulo = stringResource(R.string.resultado_falso_positivo),
        detalle = stringResource(R.string.revisar_falso_positivo_detalle),
        color = Tonos.Baja.texto,
        elegida = formulario.resultado == ResultadoRevision.FALSO_POSITIVO,
        alElegir = { acciones.alElegirResultado(ResultadoRevision.FALSO_POSITIVO) },
    )
    BotonEnlace(texto = stringResource(R.string.revisar_cancelar), alTocar = acciones.alCancelarRevision, modifier = Modifier.fillMaxWidth())
}

/**
 * Figma 66-2840: borde gris, esquinas de 12 y 16 por 14 de margen; el punto de 12 y el título SemiBold
 * 16 con 10 entre sí, y la explicación Regular 13 gris debajo. La elegida al volver del paso 2 lleva el
 * borde verde.
 */
@Composable
private fun OpcionResultado(titulo: String, detalle: String, color: Color, elegida: Boolean, alElegir: () -> Unit) {
    val borde = if (elegida) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Surface(
        onClick = alElegir,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = borde,
        // La elegida se ve por el borde verde; TalkBack la dice "seleccionada".
        modifier = Modifier.fillMaxWidth().semantics { selected = elegida },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp + borde.width, vertical = 14.dp + borde.width),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, CircleShape),
                )
                Text(text = titulo, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Text(text = detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Figma 66-2917: el motivo y el comentario son obligatorios (RF04, criterio 2) y lo dicen con el
 * asterisco rojo y "* Obligatorio" (decisión 48). Cada motivo propone el comentario de su pantalla del
 * Figma, en forma impersonal, que la persona puede cambiar; "Confirmar revisión" se habilita con
 * resultado, motivo y comentario.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasoMotivo(formulario: FormularioRevision, resultado: ResultadoRevision, alerta: Alerta, acciones: AccionesDetalle) {
    val titulo = if (resultado == ResultadoRevision.ATAQUE_REAL) R.string.revisar_paso2_titulo_ataque else R.string.revisar_paso2_titulo_falso
    val recursos = LocalResources.current
    Text(text = stringResource(titulo), style = MaterialTheme.typography.tituloHoja, modifier = Modifier.semantics { heading() })
    Text(
        text = stringResource(R.string.revisar_paso_2_de_2),
        style = MaterialTheme.typography.etiquetaBarra,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    val etiquetaMotivo = stringResource(R.string.revisar_motivo)
    val descripcionMotivo = descripcionDeCampo(etiquetaMotivo, obligatorio = true)
    Text(
        text = etiquetaConAsterisco(etiquetaMotivo),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // TalkBack dice "Motivo, obligatorio", como los campos, y no el asterisco.
        modifier = Modifier.semantics { contentDescription = descripcionMotivo },
    )
    // El motivo es uno solo: TalkBack lo anuncia como opción ("1 de 4"), no como casilla.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectableGroup(),
    ) {
        formulario.motivos.forEach { motivo ->
            ChipOpcion(
                texto = stringResource(motivo.etiqueta()),
                elegido = formulario.motivo == motivo,
                alTocar = { acciones.alElegirMotivo(motivo, motivo.comentarioPropuesto(recursos, alerta)) },
                rol = Role.RadioButton,
            )
        }
    }
    CampoTexto(
        valor = formulario.comentario,
        alCambiar = acciones.alCambiarComentario,
        etiqueta = stringResource(R.string.revisar_comentario),
        ejemplo = "",
        ayuda = stringResource(R.string.revisar_comentario_ayuda),
        lineasMinimas = 2,
        ayudaSiempre = true,
        obligatorio = true,
    )
    LeyendaObligatorio()
    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonContorno(
            texto = stringResource(R.string.revisar_volver),
            alTocar = acciones.alVolverAlResultado,
            modifier = Modifier.weight(1f),
            relleno = RellenoBotonAngosto,
        )
        BotonPrincipal(
            texto = stringResource(R.string.revisar_confirmar),
            alTocar = acciones.alConfirmarRevision,
            habilitado = formulario.puedeConfirmar,
            modifier = Modifier.weight(1f),
        )
    }
}
