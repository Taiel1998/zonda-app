package ar.edu.uade.zonda.ui.detalle

import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Una revisión recién confirmada que todavía se puede deshacer. */
data class RevisionPorDeshacer(
    val redId: String,
    val alertaId: String,
    /** "pc-rrhh → srv-sueldos": el aviso nombra la alerta porque ya no se está en su detalle. */
    val alerta: String,
    /** Lo que se eligió en la hoja, para volver a abrirla como estaba si se deshace. */
    val formulario: FormularioRevision,
    /** Si había conexión al confirmar: sin ella, el aviso dice que se envía cuando haya. */
    val conConexion: Boolean,
)

/**
 * La ventana de Deshacer de las revisiones (corrección 7, decisión 38). Vive en la app y no en el
 * detalle: al confirmar, el detalle se cierra y la lista de la que se vino muestra "Revisaste ..." con
 * Deshacer durante [espera]. Si nadie deshace, la revisión sale al terminar, en [alcance] (el de toda
 * la app), aunque ya no haya ninguna pantalla abierta. Deshacer la descarta antes de que salga y guarda
 * lo elegido, para que el detalle vuelva a abrir la hoja en el paso 2 como estaba.
 *
 * Hay una sola ventana abierta: si se confirma otra revisión antes de que termine, la anterior ya no
 * se puede deshacer y sale con la nueva.
 */
class VentanaDeshacer(
    private val deshacerRevision: DeshacerRevision,
    private val sincronizarAlertas: SincronizarAlertas,
    private val conectividad: Conectividad,
    private val alcance: CoroutineScope,
    private val espera: Long = ESPERA_DESHACER_MS,
) {
    private val _abierta = MutableStateFlow<RevisionPorDeshacer?>(null)
    private var envio: Job? = null
    private var aRecuperar: RevisionPorDeshacer? = null

    /** La revisión que todavía se puede deshacer, o null si no hay ninguna. */
    val abierta: StateFlow<RevisionPorDeshacer?> = _abierta.asStateFlow()

    /** Abre la ventana de una revisión que ya quedó guardada en el teléfono; sale cuando termina. */
    fun abrir(revision: RevisionPorDeshacer) {
        val anterior = _abierta.value
        envio?.cancel()
        // La anterior ya no se puede deshacer: si es de la misma red sale con esta; si no, ahora.
        if (anterior != null && anterior.redId != revision.redId) alcance.launch { enviar(anterior.redId) }
        _abierta.value = revision
        envio = alcance.launch {
            delay(espera)
            if (_abierta.compareAndSet(revision, null)) enviar(revision.redId)
        }
    }

    /**
     * Descarta la revisión abierta antes de que salga: la alerta vuelve a como estaba (RF04, criterio 1).
     * Devuelve la revisión deshecha, para abrir otra vez su detalle, o null si la ventana ya se cerró.
     */
    fun deshacer(): RevisionPorDeshacer? {
        val revision = _abierta.value ?: return null
        if (!_abierta.compareAndSet(revision, null)) return null
        envio?.cancel()
        envio = null
        aRecuperar = revision
        alcance.launch {
            try {
                deshacerRevision(revision.redId, revision.alertaId)
            } catch (e: ErrorZonda) {
                // La alerta ya no está (se borraron los datos): no hay nada que deshacer.
            }
        }
        return revision
    }

    /** Lo elegido en la hoja de una revisión deshecha de [alertaId], una sola vez, para que el detalle la reabra. */
    fun recuperarFormulario(alertaId: String): FormularioRevision? {
        val revision = aRecuperar?.takeIf { it.alertaId == alertaId } ?: return null
        aRecuperar = null
        return revision.formulario
    }

    /** Manda la cola y trae las novedades. Sin conexión no hace nada: sale cuando vuelva la red. */
    private suspend fun enviar(redId: String) {
        if (!conectividad.hayConexion()) return
        try {
            sincronizarAlertas(redId)
        } catch (e: ErrorZonda) {
            // Queda en la cola: el detalle y la lista muestran si no salió, con Reintentar.
        }
    }

    companion object {
        /** Cuánto dura el aviso con Deshacer antes de enviar la revisión (RF04, criterio 1). */
        const val ESPERA_DESHACER_MS = 5_000L
    }
}
