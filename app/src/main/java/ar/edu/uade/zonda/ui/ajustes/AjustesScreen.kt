package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.PantallaEnConstruccion

/**
 * Pantalla Ajustes (RF05 y RF06): redes, Telegram, avisos y severidad por tipo de ataque.
 * Marcador de posición hasta la fase 8.
 */
@Composable
fun AjustesScreen() {
    PantallaEnConstruccion(
        titulo = stringResource(R.string.destino_ajustes),
        detalle = stringResource(R.string.ajustes_placeholder),
    )
}
