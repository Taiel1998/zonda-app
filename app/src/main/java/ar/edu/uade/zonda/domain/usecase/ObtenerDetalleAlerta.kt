package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.Conectividad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Detalle de una alerta (RF03). Se observa desde la base local; si la alerta todavía no está en el
 * teléfono (llegó un enlace del aviso antes de sincronizar, criterio 3), se pide al servidor.
 */
class ObtenerDetalleAlerta(
    private val alertas: AlertasRepository,
    private val conectividad: Conectividad,
) {
    fun observar(redId: String, alertaId: String): Flow<Alerta?> = alertas.observarAlerta(redId, alertaId)

    /**
     * Si la alerta no está en el teléfono, la descarga. Devuelve la alerta, o null si no hay conexión
     * (la pantalla muestra lo que trajo el aviso y reintenta cuando vuelva la red).
     */
    suspend fun descargarSiFalta(redId: String, alertaId: String): Alerta? {
        alertas.observarAlerta(redId, alertaId).first()?.let { return it }
        if (!conectividad.hayConexion()) return null
        return try {
            alertas.descargarDetalle(redId, alertaId)
        } catch (e: ErrorZonda.SinConexion) {
            null
        }
    }
}
