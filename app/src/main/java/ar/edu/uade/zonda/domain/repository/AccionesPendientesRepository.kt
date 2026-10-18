package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.AccionPendiente
import java.time.Instant
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

    /**
     * Encola el cambio de responsable de una alerta (decisión 42). Hay uno por alerta: si se asigna de
     * nuevo antes de que salga, queda el último. [responsableId] null deja la alerta sin asignar.
     */
    suspend fun encolarAsignacion(redId: String, alertaId: String, accionId: String, responsableId: String?, creadaEn: Instant)

    suspend fun borrarDeRed(redId: String)
}
