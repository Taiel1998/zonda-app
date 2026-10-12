package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.flow.first

/**
 * Borrar datos locales en un toque (RNF03): las alertas, las revisiones y las acciones pendientes
 * de todas las redes. Las redes siguen pareadas (el token no se toca) y cada una vuelve a bajar
 * todo en la sincronización siguiente. Para dejar de estar pareado está [QuitarDispositivo].
 */
class BorrarDatosLocales(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
    private val acciones: AccionesPendientesRepository,
) {
    suspend operator fun invoke() {
        for (red in redes.observarRedes().first()) {
            acciones.borrarDeRed(red.id)
            alertas.borrarDeRed(red.id)
            redes.reiniciarSincronizacion(red.id)
        }
    }
}
