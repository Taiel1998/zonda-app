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
 * Motivo de la revisión, los del Figma (66-2852 y 66-2942). Cada motivo corresponde a un resultado y
 * propone un comentario (los textos están en `strings.xml`; acá solo va la regla).
 */
enum class Motivo(val resultado: ResultadoRevision) {
    ESCALADO_AL_PROVEEDOR(ResultadoRevision.ATAQUE_REAL),
    BLOQUEADO_MANUALMENTE(ResultadoRevision.ATAQUE_REAL),
    CONTRASENA_CAMBIADA(ResultadoRevision.ATAQUE_REAL),
    INVESTIGANDO(ResultadoRevision.ATAQUE_REAL),
    TAREA_PROGRAMADA(ResultadoRevision.FALSO_POSITIVO),
    BACKUP(ResultadoRevision.FALSO_POSITIVO),
    EQUIPO_NUEVO(ResultadoRevision.FALSO_POSITIVO),
    USUARIO_AUTORIZADO(ResultadoRevision.FALSO_POSITIVO),
    OTRO(ResultadoRevision.FALSO_POSITIVO);

    companion object {
        /** Los motivos que tuvo la app antes del Figma y su equivalente actual (los mismos que pasa el backend). */
        val ANTERIORES: Map<String, Motivo> = mapOf(
            "EQUIPO_AISLADO" to BLOQUEADO_MANUALMENTE,
            "CREDENCIALES_CAMBIADAS" to CONTRASENA_CAMBIADA,
            "EN_INVESTIGACION" to INVESTIGANDO,
            "HERRAMIENTA_AUTORIZADA" to USUARIO_AUTORIZADO,
            "PRUEBA_DEL_EQUIPO_DE_IT" to OTRO,
            "SERVICIO_NUEVO" to EQUIPO_NUEVO,
        )

        /** Motivos que se pueden elegir para un resultado, en el orden en que se muestran. */
        fun para(resultado: ResultadoRevision): List<Motivo> = entries.filter { it.resultado == resultado }

        /** El motivo por su nombre, aceptando también los anteriores. Uno desconocido lanza IllegalArgumentException. */
        fun desdeNombre(nombre: String): Motivo =
            entries.firstOrNull { it.name == nombre } ?: ANTERIORES[nombre] ?: throw IllegalArgumentException("Motivo desconocido: $nombre")
    }
}

/** De dónde salió una revisión: de este teléfono (todavía puede estar pendiente de envío) o del servidor. */
enum class OrigenRevision { ESTE_TELEFONO, SERVIDOR }

/**
 * Una revisión registrada sobre una alerta. El historial de la alerta es una lista de estas.
 *
 * Tiene dos horas (decisión 21): [revisadaEn], cuándo se revisó en el teléfono que la hizo, y [fecha],
 * cuándo llegó al servidor. Sin conexión la revisión sale después, así que pueden diferir. En una
 * revisión de este teléfono que todavía no salió, [fecha] es la hora en que se hizo.
 */
data class Revision(
    val resultado: ResultadoRevision,
    val motivo: Motivo,
    val comentario: String,
    val autor: String?,
    val fecha: Instant,
    val origen: OrigenRevision,
    /** Null en las revisiones del servidor anteriores a este dato. */
    val revisadaEn: Instant? = null,
) {
    /** La hora en que la persona la hizo, la mejor que se conoce. */
    val hechaEn: Instant
        get() = revisadaEn ?: fecha
}
