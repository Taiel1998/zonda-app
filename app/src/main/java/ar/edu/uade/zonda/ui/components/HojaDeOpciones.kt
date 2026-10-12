package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R

/** Una opción de la hoja: el valor que representa, su título y, si hace falta, una aclaración. */
data class Opcion<T>(
    val valor: T,
    val titulo: String,
    val detalle: String? = null,
)

/**
 * Hoja inferior con opciones excluyentes y el botón Aplicar (Figma, figura 11: hoja de Estado).
 * La elección se confirma con Aplicar; cerrar la hoja la descarta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HojaDeOpciones(
    titulo: String,
    opciones: List<Opcion<T>>,
    seleccionada: T,
    alCerrar: () -> Unit,
    alAplicar: (T) -> Unit,
) {
    val estadoHoja = rememberModalBottomSheetState()
    var elegida by remember(seleccionada) { mutableStateOf(seleccionada) }

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estadoHoja) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .selectableGroup(),
        ) {
            Text(text = titulo, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            opciones.forEach { opcion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = elegida == opcion.valor,
                            onClick = { elegida = opcion.valor },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = elegida == opcion.valor, onClick = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = opcion.titulo, style = MaterialTheme.typography.bodyLarge)
                        if (opcion.detalle != null) {
                            Text(
                                text = opcion.detalle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { alAplicar(elegida) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.filtro_aplicar))
            }
        }
    }
}
