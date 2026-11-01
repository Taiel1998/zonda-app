package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.BloqueosDeLaRed
import ar.edu.uade.zonda.domain.model.DuracionBloqueo

/**
 * Los bloqueos del firewall de una red (decisión 51). Todo habla con el servidor en el momento y lanza
 * [ar.edu.uade.zonda.domain.model.ErrorZonda]: un bloqueo no entra en la cola de acciones, porque si
 * llega tarde ya es otra decisión.
 */
interface BloqueosRepository {

    /** Los bloqueos de la red y su firewall (`GET /bloqueos`). */
    suspend fun consultar(redId: String): BloqueosDeLaRed

    /** Bloquea el origen de [alertaId] (`POST /bloqueos`): un administrador lo deja aprobado; un analista, pedido. */
    suspend fun bloquear(redId: String, accionId: String, alertaId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo

    suspend fun aprobar(redId: String, bloqueoId: String): Bloqueo

    suspend fun rechazar(redId: String, bloqueoId: String, motivo: String): Bloqueo

    suspend fun desbloquear(redId: String, bloqueoId: String, motivo: String): Bloqueo

    /** La duración nueva corre desde ahora. */
    suspend fun cambiarDuracion(redId: String, bloqueoId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo
}
