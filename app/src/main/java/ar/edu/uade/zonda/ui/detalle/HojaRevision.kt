package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.ui.comentarioPropuesto
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.EstadoRevisada
import ar.edu.uade.zonda.ui.theme.SeveridadAlta

/** Hoja inferior Revisar (Figma, figura 12). Cerrarla deslizando equivale a Cancelar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaRevision(formulario: FormularioRevision, acciones: AccionesDetalle) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = acciones.alCancelarRevision, sheetState = estadoHoja) {
        ContenidoHojaRevision(formulario, acciones)
    }
}

/** El contenido de la hoja, separado para dibujarlo en las pruebas sin la ventana de la hoja. */
@Composable
fun ContenidoHojaRevision(formulario: FormularioRevision, acciones: AccionesDetalle, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        val resultado = formulario.resultado
        if (formulario.paso == PasoRevision.MOTIVO && resultado != null) {
            PasoMotivo(formulario, resultado, acciones)
        } else {
            PasoResultado(formulario, acciones)
        }
    }
}

@Composable
private fun ColumnScope.PasoResultado(formulario: FormularioRevision, acciones: AccionesDetalle) {
    Text(text = stringResource(R.string.revisar_paso1_titulo), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.revisar_paso1_detalle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(text = stringResource(R.string.revisar_paso_1_de_2), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(10.dp))
    OpcionResultado(
        titulo = stringResource(R.string.resultado_ataque_real),
        detalle = stringResource(R.string.revisar_ataque_real_detalle),
        color = SeveridadAlta,
        seleccionada = formulario.resultado == ResultadoRevision.ATAQUE_REAL,
        alElegir = { acciones.alElegirResultado(ResultadoRevision.ATAQUE_REAL) },
    )
    Spacer(modifier = Modifier.height(10.dp))
    OpcionResultado(
        titulo = stringResource(R.string.resultado_falso_positivo),
        detalle = stringResource(R.string.revisar_falso_positivo_detalle),
        color = EstadoRevisada,
        seleccionada = formulario.resultado == ResultadoRevision.FALSO_POSITIVO,
        alElegir = { acciones.alElegirResultado(ResultadoRevision.FALSO_POSITIVO) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = acciones.alCancelarRevision, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.revisar_cancelar))
    }
}

@Composable
private fun OpcionResultado(titulo: String, detalle: String, color: Color, seleccionada: Boolean, alElegir: () -> Unit) {
    OutlinedCard(
        onClick = alElegir,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (seleccionada) 2.dp else 1.dp,
            color = if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(color, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = titulo, style = MaterialTheme.typography.titleMedium)
                Text(text = detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasoMotivo(formulario: FormularioRevision, resultado: ResultadoRevision, acciones: AccionesDetalle) {
    val titulo = if (resultado == ResultadoRevision.ATAQUE_REAL) R.string.revisar_paso2_titulo_ataque else R.string.revisar_paso2_titulo_falso
    Text(text = stringResource(titulo), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = stringResource(R.string.revisar_paso_2_de_2), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.revisar_motivo),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    val propuestos = formulario.motivos.associateWith { stringResource(it.comentarioPropuesto()) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        formulario.motivos.forEach { motivo ->
            FilterChip(
                selected = formulario.motivo == motivo,
                onClick = { acciones.alElegirMotivo(motivo, propuestos.getValue(motivo)) },
                label = { Text(stringResource(motivo.etiqueta())) },
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = formulario.comentario,
        onValueChange = acciones.alCambiarComentario,
        label = { Text(stringResource(R.string.revisar_comentario)) },
        supportingText = { Text(stringResource(R.string.revisar_comentario_ayuda)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = acciones.alVolverAlResultado, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.revisar_volver))
        }
        Button(onClick = acciones.alConfirmarRevision, enabled = formulario.puedeConfirmar, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.revisar_confirmar))
        }
    }
}
