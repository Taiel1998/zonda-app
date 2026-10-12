package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository

/**
 * Deshacer una revisión durante los cinco segundos del aviso (RF04, criterio 1): quita la acción
 * pendiente antes de que salga y vuelve la alerta al último estado que llegó del servidor.
 */
class DeshacerRevision(
    private val alertas: AlertasRepository,
    private val acciones: AccionesPendientesRepository,
) {
    suspend operator fun invoke(redId: String, alertaId: String) {
        acciones.deAlerta(redId, alertaId)?.let { acciones.quitar(it.id) }
        alertas.restaurarEstadoDelServidor(redId, alertaId)
    }
}
