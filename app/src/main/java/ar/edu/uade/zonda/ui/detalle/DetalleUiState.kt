package ar.edu.uade.zonda.ui.detalle

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import java.time.Instant

/** Estado de la pantalla Detalle (RF03). */
sealed interface DetalleUiState {

    /** La alerta no está en el teléfono y se está pidiendo al servidor. */
    data object Cargando : DetalleUiState

    data object SinRed : DetalleUiState

    data class Contenido(
        val alerta: Alerta,
        val sinConexion: Boolean,
        /** Hora de la última sincronización de la red, para el banner de sin conexión. */
        val datosDe: Instant?,
    ) : DetalleUiState

    /**
     * La alerta no está en el teléfono y no se pudo descargar: llegó un enlace del aviso sin red
     * (criterio 3 de RF03) o el servidor respondió con un error. Se reintenta sola cuando vuelve la conexión.
     */
    data class NoDisponible(
        val sinConexion: Boolean,
        val error: ErrorZonda?,
    ) : DetalleUiState
}
