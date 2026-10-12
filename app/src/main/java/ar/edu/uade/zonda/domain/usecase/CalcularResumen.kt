package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Resumen de actividad de la red activa para 7 o 30 días (RF07). Se calcula sobre las alertas
 * guardadas en el teléfono, por eso funciona sin conexión. Devuelve null si no hay red pareada.
 */
class CalcularResumen(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
    private val reloj: () -> Instant = Instant::now,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend operator fun invoke(periodo: Periodo): ResumenActividad? {
        val red = redes.observarRedActiva().first() ?: return null
        val ahora = reloj()
        val desde = periodo.desde(ahora) ?: Instant.EPOCH
        val lista = alertas.obtenerAlertasDesde(red.id, desde)
        return ResumenActividad.calcular(lista, periodo, ahora, zona())
    }
}
