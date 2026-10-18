package ar.edu.uade.zonda.domain.model

import java.time.Instant

/**
 * Un cambio de responsable de una alerta (corrección 1, decisión 42): a quién (null si quedó sin
 * asignar), quién lo hizo y cuándo. La alerta guarda el de ahora y la línea de tiempo para el historial.
 */
data class Asignacion(
    val responsableId: String?,
    val responsable: String?,
    val porId: String,
    val por: String,
    val fecha: Instant,
) {
    /** Se la asignó la misma persona ("Laura se la asignó"); no avisa a nadie. */
    val propia: Boolean
        get() = responsableId != null && responsableId == porId
}

/** Lo que puede hacer una persona con el responsable de una alerta, según su perfil (Figma 468-880 y 468-983). */
enum class AccionResponsable {
    /** Administrador, sin responsable: "Asignar" abre la hoja con la gente. */
    ASIGNAR,

    /** Administrador, con responsable: "Cambiar" abre la misma hoja, con "Sacar la asignación". */
    CAMBIAR,

    /** Analista, sin responsable: "Me la asigno". */
    ASIGNARME,

    /** Analista, es la responsable: "Soltarla". */
    SOLTARLA,

    /** Analista, la tiene otra persona: sin botón (Taiel: los mensajes sueltos no van). */
    NINGUNA;

    companion object {
        /**
         * El administrador asigna a cualquiera; el analista toma una sin asignar o suelta la suya, y no le
         * puede sacar una alerta a otra persona. El servidor aplica las mismas reglas.
         */
        fun para(perfil: Perfil, responsableId: String?, miId: String?): AccionResponsable = when {
            perfil.esAdministrador -> if (responsableId == null) ASIGNAR else CAMBIAR
            responsableId == null -> ASIGNARME
            responsableId == miId -> SOLTARLA
            else -> NINGUNA
        }
    }
}

/**
 * Una alerta que otra persona le asignó a este teléfono, que llegó al sincronizar: si la red no tiene
 * Telegram, Zonda lo avisa con una notificación (hasta 15 minutos después).
 */
data class AsignacionNueva(
    val alertaId: String,
    val por: String,
    val severidad: Severidad,
    val servicio: String,
    val inicio: Instant,
)
