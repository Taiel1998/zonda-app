package ar.edu.uade.zonda.ui.estado

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.PantallaEnConstruccion

/**
 * Pantalla Estado (RF01): semáforo, contadores por severidad y hora de la última sincronización.
 * La pantalla y su ViewModel los escribe Taiel desde el 26/10; las piezas ya están en
 * ui/components (Semaforo, ContadorSeveridad, LeyendaSemaforo, TarjetaDescarga, TarjetaAlerta y
 * BannerSinConexion). Mientras tanto queda esta pantalla provisoria.
 */
@Composable
fun EstadoScreen(estado: EstadoUiState = EstadoUiState.Cargando) {
    PantallaEnConstruccion(
        titulo = stringResource(R.string.destino_estado),
        detalle = stringResource(R.string.estado_placeholder),
        modifier = Modifier.statusBarsPadding(),
    )
}
