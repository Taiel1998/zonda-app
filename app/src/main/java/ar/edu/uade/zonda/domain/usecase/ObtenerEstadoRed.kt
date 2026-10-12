package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.EstadoRed
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Estado de la red activa para la pantalla Estado (RF01): semáforo, contadores de alertas sin
 * revisar por severidad y hora de los datos.
 *
 * Emite null si no hay red pareada. Como es un Flow sobre la base local, cuando la persona revisa
 * la última alerta alta el semáforo cambia solo, sin volver a llamar al caso de uso.
 */
class ObtenerEstadoRed(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<EstadoRed?> =
        redes.observarRedActiva().flatMapLatest { red ->
            if (red == null) {
                flowOf(null)
            } else {
                alertas.observarContadoresSinRevisar(red.id).map { contadores ->
                    EstadoRed(
                        red = red,
                        semaforo = calcularSemaforo(red, contadores),
                        sinRevisar = contadores,
                        datosDe = red.ultimaSincronizacion,
                    )
                }
            }
        }

    companion object {
        /**
         * Regla del semáforo (RF01). El orden importa: sin datos manda sobre todo lo demás, porque
         * nunca se muestra un semáforo verde si la red no se sincronizó (criterio de aceptación 3).
         * Las bajas no cambian el color.
         */
        fun calcularSemaforo(red: Red, contadores: ContadoresSinRevisar): Semaforo = when {
            red.sinDatos -> Semaforo.SIN_DATOS
            contadores.altas > 0 -> Semaforo.ROJO
            contadores.medias > 0 -> Semaforo.AMARILLO
            else -> Semaforo.VERDE
        }
    }
}
