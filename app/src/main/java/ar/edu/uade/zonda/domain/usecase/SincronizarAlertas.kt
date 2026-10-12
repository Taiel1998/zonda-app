package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import kotlinx.coroutines.flow.first

/**
 * Sincronizar la red activa (o una red dada): primero se envía lo pendiente y después se traen las
 * novedades (informe, sección 14.2). Lo disparan abrir la app, deslizar, "Sincronizar ahora", cambiar
 * de red, llegar un enlace y la tarea de cada 15 minutos.
 *
 * Devuelve null si no hay red pareada. Los errores de red salen como [ar.edu.uade.zonda.domain.model.ErrorZonda].
 */
class SincronizarAlertas(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
) {
    suspend operator fun invoke(redId: String? = null): ResultadoSincronizacion? {
        val id = redId ?: redes.observarRedActiva().first()?.id ?: return null
        return alertas.sincronizar(id)
    }
}
