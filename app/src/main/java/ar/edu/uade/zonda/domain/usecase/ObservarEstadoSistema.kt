package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * La tarjeta "Estado del sistema" de Estado: mientras alguien la mira, pregunta al servidor de la red
 * activa cada [cada] cómo están el sensor, el análisis en vivo y el servidor. Sin red, null.
 *
 * Si el servidor deja de contestar se conserva la última corrida conocida, así "Última corrida hace 3 min"
 * sigue contando y se ve desde cuándo no hay noticias. No hay botón para reiniciar nada desde el teléfono.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObservarEstadoSistema(private val redes: RedesRepository, private val cada: Duration = CADA) {

    operator fun invoke(): Flow<EstadoSistema?> = redes.observarRedActiva()
        .map { it?.id }
        // La red activa cambia con cada sincronización (su hora); la consulta se reinicia solo si cambia de red.
        .distinctUntilChanged()
        .flatMapLatest { redId -> if (redId == null) flowOf(null) else consultarCada(redId) }

    private fun consultarCada(redId: String): Flow<EstadoSistema> = flow {
        var anterior: EstadoSistema? = null
        while (true) {
            val actual = redes.estadoDelSistema(redId)
            val conLoConocido = if (actual.servidor == EstadoPieza.NO_RESPONDE && anterior != null) {
                actual.copy(ultimaCorrida = anterior.ultimaCorrida, ultimoFlujo = anterior.ultimoFlujo)
            } else {
                actual
            }
            emit(conLoConocido)
            anterior = conLoConocido
            delay(cada)
        }
    }

    companion object {
        /** Lo mismo que tarda una vuelta del análisis en vivo. */
        val CADA: Duration = 20.seconds
    }
}
