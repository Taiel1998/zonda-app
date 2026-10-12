package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R

/** Un botón de una hoja: su texto y qué hace. */
data class AccionDeHoja(val texto: String, val alTocar: () -> Unit)

/**
 * Hoja inferior de confirmación (Figma: borrar datos locales y quitar este dispositivo). Las
 * confirmaciones quedan abajo, en la zona del pulgar (informe, figura 8): "Cancelar" con borde y la
 * acción de peligro en rojo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaConfirmacion(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit,
    resumen: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (resumen != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = resumen,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = alCerrar, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.accion_cancelar))
                }
                Button(
                    onClick = alConfirmar,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(textoConfirmar)
                }
            }
        }
    }
}

/**
 * Hoja inferior de ayuda con pasos numerados (Figma: "Estar en la red de la empresa" y "¿Dónde
 * encuentro el código?"). Con una acción secundaria, "Entendido" queda con borde y la otra llena.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaAyuda(
    titulo: String,
    introduccion: String,
    pasos: List<String>,
    alCerrar: () -> Unit,
    accion: AccionDeHoja? = null,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = introduccion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            pasos.forEachIndexed { indice, paso ->
                Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    NumeroDePaso(indice + 1)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = paso, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (accion == null) {
                Button(onClick = alCerrar, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.accion_entendido))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = alCerrar, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(stringResource(R.string.accion_entendido))
                    }
                    Button(onClick = accion.alTocar, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(accion.texto)
                    }
                }
            }
        }
    }
}

/** Número de paso en un círculo verde claro. */
@Composable
fun NumeroDePaso(numero: Int, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = numero.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
