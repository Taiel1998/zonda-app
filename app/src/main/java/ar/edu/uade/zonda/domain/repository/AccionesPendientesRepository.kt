package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.AccionPendiente
import kotlinx.coroutines.flow.Flow

/** Cola de acciones hechas en el teléfono que todavía no llegaron al servidor (informe, sección 14.1). */
interface AccionesPendientesRepository {

    /** Acciones de una red, de la más antigua a la más nueva (se envían en ese orden). */
    fun observar(redId: String): Flow<List<AccionPendiente>>

    /** La acción pendiente sobre una alerta, si hay una (como máximo hay una por alerta). */
    suspend fun deAlerta(redId: String, alertaId: String): AccionPendiente?

    /** Agrega una acción a la cola; si ya había una para la misma alerta, la reemplaza. */
    suspend fun encolar(accion: AccionPendiente)

    suspend fun quitar(accionId: String)

    suspend fun borrarDeRed(redId: String)
}
