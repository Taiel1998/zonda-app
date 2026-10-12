package ar.edu.uade.zonda.ui.estado

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.PantallaEnConstruccion

/**
 * Pantalla Estado (RF01): semáforo, contadores por severidad y hora de la última sincronización.
 * Por ahora es un marcador de posición; la pantalla y su ViewModel se construyen en la fase 5.
 */
@Composable
fun EstadoScreen() {
    PantallaEnConstruccion(
        titulo = stringResource(R.string.destino_estado),
        detalle = stringResource(R.string.estado_placeholder),
    )
}
