package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.EstadoRed
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Estado de la red activa para la pantalla Estado (RF01): semáforo, contadores de alertas sin
 * revisar por severidad y hora de los datos.
 *
 * LO ESCRIBE TAIEL. Lo que tiene que hacer, según RF01 del informe:
 *
 * 1. Observar la red activa con [RedesRepository.observarRedActiva]. Si es null (no hay red pareada),
 *    emitir null.
 * 2. Para esa red, observar los contadores con [AlertasRepository.observarContadoresSinRevisar]
 *    (mirar cómo lo hace [ObservarAlertas] con `flatMapLatest`).
 * 3. Calcular el semáforo con estas reglas, en este orden:
 *    - si la red nunca se sincronizó (`red.sinDatos`): [ar.edu.uade.zonda.domain.model.Semaforo.SIN_DATOS].
 *      Nunca un semáforo verde sin datos (criterio de aceptación 3);
 *    - si hay alertas altas sin revisar: ROJO;
 *    - si no hay altas pero hay medias: AMARILLO;
 *    - si no hay altas ni medias: VERDE. Las bajas no cambian el semáforo.
 * 4. Emitir un [EstadoRed] con la red, el semáforo, los contadores y `datosDe = red.ultimaSincronizacion`.
 *
 * Conviene poner la regla del semáforo en una función aparte (por ejemplo `calcularSemaforo(red, contadores)`),
 * así se prueba sola. Los tests están en `ObtenerEstadoRedTest` (los escribe Rodrigo).
 */
class ObtenerEstadoRed(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
) {
    operator fun invoke(): Flow<EstadoRed?> {
        TODO("Lo escribe Taiel en la fase 1: ver el comentario de la clase")
    }
}
