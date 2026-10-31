package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.BloqueosDeLaRed
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.repository.BloqueosRepository
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds

/**
 * Bloquear el origen de una alerta y lo que sigue (decisión 51): desbloquear, cambiar la duración y, para un
 * administrador, aprobar o rechazar los pedidos. Todo sale en el momento y necesita conexión. Cada acción
 * lleva su motivo, que queda en el registro de cambios; el servidor decide quién puede hacer qué.
 */
class GestionarBloqueos(
    private val bloqueos: BloqueosRepository,
    private val ids: GeneradorDeIds,
) {

    suspend fun consultar(redId: String): BloqueosDeLaRed = bloqueos.consultar(redId)

    /** El id de la acción hace que el mismo bloqueo, mandado dos veces, quede una sola. */
    suspend fun bloquear(redId: String, alertaId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo =
        bloqueos.bloquear(redId, ids.nuevo(), alertaId, duracion, conMotivo(motivo))

    suspend fun aprobar(redId: String, bloqueoId: String): Bloqueo = bloqueos.aprobar(redId, bloqueoId)

    suspend fun rechazar(redId: String, bloqueoId: String, motivo: String): Bloqueo =
        bloqueos.rechazar(redId, bloqueoId, conMotivo(motivo))

    suspend fun desbloquear(redId: String, bloqueoId: String, motivo: String): Bloqueo =
        bloqueos.desbloquear(redId, bloqueoId, conMotivo(motivo))

    suspend fun cambiarDuracion(redId: String, bloqueoId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo =
        bloqueos.cambiarDuracion(redId, bloqueoId, duracion, conMotivo(motivo))

    private fun conMotivo(motivo: String): String =
        motivo.trim().ifEmpty { throw ErrorZonda.Validacion("motivo", "Falta el motivo") }
}
