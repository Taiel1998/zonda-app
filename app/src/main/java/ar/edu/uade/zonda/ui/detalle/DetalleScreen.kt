package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.PantallaEnConstruccion

/**
 * Pantalla Detalle (RF03 y RF04). Por ahora solo muestra el id con el que se abrió, para probar el
 * enlace del aviso (fase 0b). El contenido real se construye en las fases 5 y 6.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleScreen(
    alertaId: String,
    alVolver: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detalle_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accion_volver),
                        )
                    }
                },
            )
        },
    ) { relleno ->
        PantallaEnConstruccion(
            titulo = stringResource(R.string.detalle_alerta_id, alertaId),
            detalle = stringResource(R.string.detalle_placeholder),
            modifier = Modifier.padding(relleno),
        )
    }
}
