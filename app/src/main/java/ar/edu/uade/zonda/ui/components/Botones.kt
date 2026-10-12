package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** La acción principal de la pantalla: ancha y abajo, en la zona del pulgar (informe, figura 8). */
@Composable
fun BotonPrincipal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
) {
    Button(onClick = alTocar, enabled = habilitado, modifier = modifier.fillMaxWidth().height(48.dp)) {
        if (icono != null) {
            Icon(imageVector = icono, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(texto)
    }
}

/** Botón tonal rosa con texto rojo para lo que borra o revoca ("Borrar datos locales", "Desvincular Telegram"). */
@Composable
fun BotonTonalPeligro(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = alTocar,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier,
    ) {
        Text(texto)
    }
}
