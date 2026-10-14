package ar.edu.uade.zonda.ui.estado

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.ui.components.BarraEstado
import ar.edu.uade.zonda.ui.components.PantallaEnConstruccion
import ar.edu.uade.zonda.ui.components.TarjetaDescarga

/**
 * Pantalla Estado (RF01): semáforo, contadores por severidad y hora de la última sincronización.
 * La pantalla y su ViewModel los escribe Taiel desde el 26/10; las piezas ya están en
 * ui/components (BarraEstado, Semaforo, ContadorSeveridad, LeyendaSemaforo, TarjetaDescarga,
 * TarjetaAlerta y BannerSinConexion). Mientras tanto queda esta pantalla provisoria, con la barra
 * del Figma (el ícono del Resumen ya funciona) y la tarjeta de la primera descarga arriba mientras
 * baja (Figma 50-616).
 */
@Composable
fun EstadoScreen(
    estado: EstadoUiState = EstadoUiState.Cargando,
    progreso: ProgresoDescarga? = null,
    alAbrirResumen: () -> Unit = {},
) {
    Column {
        BarraEstado(alAbrirResumen = alAbrirResumen)
        if (progreso != null) {
            TarjetaDescarga(
                descargadas = progreso.descargadas,
                total = progreso.total,
                modifier = Modifier.padding(16.dp),
            )
        }
        PantallaEnConstruccion(
            titulo = stringResource(R.string.destino_estado),
            detalle = stringResource(R.string.estado_placeholder),
        )
    }
}
