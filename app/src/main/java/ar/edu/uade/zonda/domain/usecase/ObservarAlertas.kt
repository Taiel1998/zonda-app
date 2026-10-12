package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Lista de alertas de la red activa con un filtro (RF02).
 *
 * Es el ejemplo completo de un caso de uso que observa datos: combina la red activa con las alertas
 * de esa red, y cuando la persona cambia de red (RF06) la lista cambia sola, porque [flatMapLatest]
 * cancela la observación de la red anterior y empieza la de la nueva.
 */
class ObservarAlertas(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(filtro: FiltroAlertas = FiltroAlertas.TODAS): Flow<List<Alerta>> =
        redes.observarRedActiva().flatMapLatest { red ->
            if (red == null) flowOf(emptyList()) else alertas.observarAlertas(red.id, filtro)
        }
}
