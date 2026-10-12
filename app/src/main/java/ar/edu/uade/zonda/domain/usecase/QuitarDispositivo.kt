package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository

/**
 * Quitar este dispositivo de una red (RF06): revoca el token y borra todo lo de esa red en el
 * teléfono (alertas, acciones pendientes, catálogo y token). Después la app vuelve al pareo si no
 * queda ninguna red.
 */
class QuitarDispositivo(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
    private val acciones: AccionesPendientesRepository,
) {
    suspend operator fun invoke(redId: String) {
        acciones.borrarDeRed(redId)
        alertas.borrarDeRed(redId)
        redes.quitarDispositivo(redId)
    }
}
