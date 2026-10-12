package ar.edu.uade.zonda.ui.alertas

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.PantallaEnConstruccion

/**
 * Pantalla Alertas (RF02): lista de alertas con filtros. Marcador de posición hasta la fase 5.
 */
@Composable
fun AlertasScreen() {
    PantallaEnConstruccion(
        titulo = stringResource(R.string.destino_alertas),
        detalle = stringResource(R.string.alertas_placeholder),
    )
}
