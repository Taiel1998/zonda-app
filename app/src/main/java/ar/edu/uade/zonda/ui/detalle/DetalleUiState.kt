package ar.edu.uade.zonda.ui.detalle

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import java.time.Instant

/** Estado de la pantalla Detalle (RF03 y RF04). */
sealed interface DetalleUiState {

    /** La alerta no está en el teléfono y se está pidiendo al servidor. */
    data object Cargando : DetalleUiState

    data object SinRed : DetalleUiState

    data class Contenido(
        val alerta: Alerta,
        /** La revisión de este teléfono que todavía no llegó al servidor, con su estado de envío. */
        val accion: AccionPendiente?,
        val sinConexion: Boolean,
        /** Hora de la última sincronización de la red, para el banner de sin conexión. */
        val datosDe: Instant?,
        /**
         * La persona la acaba de revisar en esta pantalla: la revisión va abajo, como en los cuadros
         * "revisada con éxito" del Figma. Al volver a abrir la alerta, arriba con el historial (51-2379).
         */
        val recienRevisada: Boolean = false,
        /** El nombre con el que quedó registrado este teléfono: firma sus revisiones que todavía no salieron. */
        val nombreDelTelefono: String = "",
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

/** Los dos pasos de la hoja Revisar (Figma, figura 12). */
enum class PasoRevision { RESULTADO, MOTIVO }

/**
 * Lo que la persona va eligiendo en la hoja Revisar. Vive en el ViewModel para que no se pierda al
 * rotar el teléfono. [comentarioEditado] distingue el comentario propuesto por el motivo del que
 * escribió la persona: al cambiar de motivo, solo se reemplaza el propuesto.
 */
data class FormularioRevision(
    val paso: PasoRevision = PasoRevision.RESULTADO,
    val resultado: ResultadoRevision? = null,
    val motivo: Motivo? = null,
    val comentario: String = "",
    val comentarioEditado: Boolean = false,
) {
    /** Motivos que corresponden al resultado elegido, en el orden en que se muestran. */
    val motivos: List<Motivo>
        get() = resultado?.let { Motivo.para(it) } ?: emptyList()

    /** No se puede confirmar sin resultado, motivo y comentario (RF04, criterio 2). */
    val puedeConfirmar: Boolean
        get() = resultado != null && motivo?.resultado == resultado && comentario.isNotBlank()
}

/** Avisos de un solo uso del detalle (snackbar). */
sealed interface AvisoDetalle {

    /** Revisión guardada en el teléfono; mientras se muestra, Deshacer la descarta (RF04, criterio 1). */
    data class RevisionGuardada(val conConexion: Boolean) : AvisoDetalle

    data class Error(val error: ErrorZonda) : AvisoDetalle
}
