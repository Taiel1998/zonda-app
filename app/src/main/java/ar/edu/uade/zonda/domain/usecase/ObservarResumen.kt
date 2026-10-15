package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Resumen de actividad de la red activa para 7 o 30 días (RF07). Se calcula en el teléfono con las
 * alertas guardadas, por eso funciona sin conexión (RNF01), y se vuelve a calcular solo cada vez que
 * cambia la base: llega una sincronización o se revisa una alerta. Emite null si no hay red pareada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObservarResumen(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
    private val reloj: () -> Instant = Instant::now,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
) {
    operator fun invoke(periodo: PeriodoResumen, grupo: GrupoResumen? = null): Flow<ResumenActividad?> =
        redes.observarRedActiva()
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { redId ->
                if (redId == null) {
                    flowOf(null)
                } else {
                    // Todo lo guardado (son 30 días como mucho): el período se corta por días al calcular.
                    alertas.observarAlertas(redId, FiltroAlertas(periodo = Periodo.TODO))
                        .map { lista -> ResumenActividad.calcular(lista, periodo, reloj(), zona(), grupo) }
                }
            }
}
