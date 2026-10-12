package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import kotlinx.coroutines.flow.first

/**
 * Qué hacer con una revisión que no pudo llegar al servidor (RF04, criterio 4; informe, sección 14.3):
 *
 * - En conflicto (409, otra persona la revisó antes): aplicar la propia igual o quedarse con la otra.
 * - En error (cinco intentos fallidos o datos rechazados): reintentar a mano.
 *
 * No envía nada: deja la cola lista y la sincronización siguiente la manda (docs/decisiones.md, decisión 15).
 */
class ResolverEnvioRevision(
    private val alertas: AlertasRepository,
    private val acciones: AccionesPendientesRepository,
) {

    /**
     * La persona vio la revisión de la otra persona y aplica la suya igual. La acción vuelve a la cola
     * con la versión que tiene ahora la alerta (la que incluye la revisión ajena): el servidor la acepta
     * porque ya no está desactualizada, y si mientras tanto revisó una tercera persona, vuelve a avisar.
     * Es la misma acción (mismo id): la API respondió 409 y no la aplicó.
     */
    suspend fun aplicarLaMiaIgual(redId: String, alertaId: String): AccionPendiente {
        val accion = acciones.deAlerta(redId, alertaId) ?: throw ErrorZonda.NoEncontrado()
        if (accion.estado != EstadoEnvio.CONFLICTO) {
            throw ErrorZonda.Validacion("estado", "La revisión no está en conflicto")
        }
        val alerta = alertas.observarAlerta(redId, alertaId).first() ?: throw ErrorZonda.NoEncontrado()
        val reenviada = accion.copy(
            versionVista = alerta.version,
            estado = EstadoEnvio.PENDIENTE,
            intentos = 0,
            ultimoError = null,
            revisionAjena = null,
        )
        acciones.encolar(reenviada)
        return reenviada
    }

    /** La persona se queda con la revisión de la otra persona: la propia se descarta sin enviarse. */
    suspend fun quedarmeConLaOtra(redId: String, alertaId: String) {
        acciones.deAlerta(redId, alertaId)?.let { acciones.quitar(it.id) }
        alertas.restaurarEstadoDelServidor(redId, alertaId)
    }

    /** Una acción que quedó en error vuelve a la cola con los intentos en cero. */
    suspend fun reintentar(redId: String, alertaId: String): AccionPendiente {
        val accion = acciones.deAlerta(redId, alertaId) ?: throw ErrorZonda.NoEncontrado()
        if (accion.estado != EstadoEnvio.ERROR) {
            throw ErrorZonda.Validacion("estado", "La revisión no está en error")
        }
        val reintentada = accion.copy(estado = EstadoEnvio.PENDIENTE, intentos = 0, ultimoError = null)
        acciones.encolar(reintentada)
        return reintentada
    }
}
