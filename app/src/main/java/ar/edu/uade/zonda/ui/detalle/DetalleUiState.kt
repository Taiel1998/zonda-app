package ar.edu.uade.zonda.ui.detalle

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.AvisoRecibido
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.CalculoSeveridad
import ar.edu.uade.zonda.domain.model.CambioDeSeveridad
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoFirewall
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
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
        /** El nombre con el que quedó registrado este teléfono: firma sus revisiones que todavía no salieron. */
        val nombreDelTelefono: String = "",
        /** La red de la alerta: el texto que se comparte dice de qué red viene, como el aviso. */
        val nombreDeLaRed: String = "",
        /** La gente de la red, vos primero: la hoja "Asignar a" (decisión 42). Vacía con un servidor anterior. */
        val personas: List<Persona> = emptyList(),
        /** El perfil de este teléfono: decide qué botón tiene la tarjeta Responsable. */
        val perfil: Perfil = Perfil.ADMINISTRADOR,
        /** El id de este teléfono en el servidor: "Laura (vos)", "Me la asigno" y "Soltarla". */
        val miId: String? = null,
        /** Cómo se calculó la severidad con la importancia del equipo destino (decisión 41). */
        val calculo: CalculoSeveridad? = null,
        /** Los cambios de configuración que cambiaron el color de la alerta, para el historial (decisión 40). */
        val cambiosDeSeveridad: List<CambioDeSeveridad> = emptyList(),
        /** El bloqueo del origen (decisión 51); null si la red no tiene firewall o todavía no se sabe. */
        val bloqueo: SeccionBloqueo? = null,
    ) : DetalleUiState

    /**
     * La alerta no está en el teléfono y no se pudo descargar: llegó un enlace del aviso sin red
     * (criterio 3 de RF03) o el servidor respondió con un error. Se reintenta sola cuando vuelve la conexión.
     */
    data class NoDisponible(
        val sinConexion: Boolean,
        val error: ErrorZonda?,
        /** Lo que trajo el enlace del aviso (severidad, servicio y hora), si se abrió desde Telegram (Figma 3-139). */
        val aviso: AvisoRecibido? = null,
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

/**
 * Avisos de un solo uso del detalle (snackbar). La revisión guardada ya no se avisa acá: el detalle se
 * cierra y lo dice la lista con "Revisaste ..." ([VentanaDeshacer]).
 */
sealed interface AvisoDetalle {

    data class Error(val error: ErrorZonda) : AvisoDetalle

    /** Sin conexión el bloqueo no sale ni queda en la cola: el aviso lo dice, para que nadie lo dé por hecho. */
    data object BloqueoSinConexion : AvisoDetalle
}

/**
 * La tarjeta del bloqueo del origen (decisión 51). Solo con un firewall conectado al servidor: en modo de
 * observación la red no bloquea y el detalle no la muestra.
 */
data class SeccionBloqueo(
    val firewall: EstadoFirewall,
    /** El bloqueo o el pedido abierto del origen; null si no tiene. */
    val actual: Bloqueo?,
    /** El origen está en la lista de "nunca bloquear" del firewall: no hay botón. */
    val nuncaSeBloquea: Boolean,
)

/** Qué confirma la hoja del bloqueo. */
enum class AccionBloqueo { BLOQUEAR, CAMBIAR_DURACION, DESBLOQUEAR, RECHAZAR }

/** Lo que la persona va eligiendo en la hoja del bloqueo; vive en el ViewModel, como la de Revisar. */
data class FormularioBloqueo(
    val accion: AccionBloqueo,
    val duracion: DuracionBloqueo = DuracionBloqueo.PREDETERMINADA,
    val motivo: String = "",
    /** Se tocó confirmar sin motivo. */
    val conError: Boolean = false,
    /** Esperando al servidor: el botón muestra la rueda y no se manda dos veces. */
    val enviando: Boolean = false,
) {
    val conDuracion: Boolean
        get() = accion == AccionBloqueo.BLOQUEAR || accion == AccionBloqueo.CAMBIAR_DURACION
}
