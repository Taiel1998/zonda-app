package ar.edu.uade.zonda.domain.model

import java.time.Instant

/** Lo que la persona decide al revisar una alerta (RF04). */
enum class ResultadoRevision { ATAQUE_REAL, FALSO_POSITIVO }

/** Estado de revisión de una alerta tal como se ve en la lista (RF02). */
enum class EstadoRevision {
    SIN_REVISAR,
    ATAQUE_REAL,
    FALSO_POSITIVO;

    companion object {
        fun de(resultado: ResultadoRevision): EstadoRevision = when (resultado) {
            ResultadoRevision.ATAQUE_REAL -> ATAQUE_REAL
            ResultadoRevision.FALSO_POSITIVO -> FALSO_POSITIVO
        }
    }
}

/**
 * Motivo de la revisión. Cada motivo corresponde a un resultado y propone un comentario (el texto
 * del motivo y del comentario propuesto están en `strings.xml`; acá solo va la regla).
 */
enum class Motivo(val resultado: ResultadoRevision) {
    ESCALADO_AL_PROVEEDOR(ResultadoRevision.ATAQUE_REAL),
    EQUIPO_AISLADO(ResultadoRevision.ATAQUE_REAL),
    CREDENCIALES_CAMBIADAS(ResultadoRevision.ATAQUE_REAL),
    EN_INVESTIGACION(ResultadoRevision.ATAQUE_REAL),
    TAREA_PROGRAMADA(ResultadoRevision.FALSO_POSITIVO),
    HERRAMIENTA_AUTORIZADA(ResultadoRevision.FALSO_POSITIVO),
    PRUEBA_DEL_EQUIPO_DE_IT(ResultadoRevision.FALSO_POSITIVO),
    SERVICIO_NUEVO(ResultadoRevision.FALSO_POSITIVO);

    companion object {
        /** Motivos que se pueden elegir para un resultado, en el orden en que se muestran. */
        fun para(resultado: ResultadoRevision): List<Motivo> = entries.filter { it.resultado == resultado }
    }
}

/** De dónde salió una revisión: de este teléfono (todavía puede estar pendiente de envío) o del servidor. */
enum class OrigenRevision { ESTE_TELEFONO, SERVIDOR }

/** Una revisión registrada sobre una alerta. El historial de la alerta es una lista de estas. */
data class Revision(
    val resultado: ResultadoRevision,
    val motivo: Motivo,
    val comentario: String,
    val autor: String?,
    val fecha: Instant,
    val origen: OrigenRevision,
)
