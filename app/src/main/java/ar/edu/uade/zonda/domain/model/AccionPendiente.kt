package ar.edu.uade.zonda.domain.model

import java.time.Instant

/** Estado de envío de una acción hecha en el teléfono (informe, figura 24). */
enum class EstadoEnvio {
    /** Todavía no viajó; se envía cuando haya conexión. */
    PENDIENTE,
    ENVIANDO,

    /** Falló cinco veces; queda visible con reintento manual. */
    ERROR,

    /** El servidor respondió 409: otra persona revisó la alerta antes. La app pregunta qué hacer. */
    CONFLICTO,
}

enum class TipoAccion { REVISAR_ALERTA, CAMBIAR_SEVERIDADES }

/**
 * Acción del usuario que todavía no llegó al servidor. Tiene un id único por acción para que la API
 * no la aplique dos veces si se reenvía (RF04, criterio 5).
 */
data class AccionPendiente(
    val id: String,
    val redId: String,
    val tipo: TipoAccion,
    val alertaId: String?,
    val revision: Revision?,
    val severidades: SeveridadPorTipo?,
    /** Versión de la alerta que la persona vio al revisar; el servidor la compara para detectar conflictos. */
    val versionVista: Int?,
    val intentos: Int,
    val estado: EstadoEnvio,
    val creadaEn: Instant,
    val ultimoError: String?,
    /** La revisión de la otra persona, cuando el estado es [EstadoEnvio.CONFLICTO]. */
    val revisionAjena: Revision?,
) {
    companion object {
        const val MAXIMO_INTENTOS = 5
    }
}
