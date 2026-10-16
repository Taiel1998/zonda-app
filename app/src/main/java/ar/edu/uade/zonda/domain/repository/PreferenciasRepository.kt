package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Preferencias de este teléfono (no de la red): avisos, qué partes del primer inicio ya se hicieron y
 * hasta dónde llegaron los recordatorios de altas.
 */
interface PreferenciasRepository {

    fun observarAvisos(): Flow<PreferenciasAvisos>

    suspend fun guardarAvisos(preferencias: PreferenciasAvisos)

    /** El interruptor "Notificaciones del sistema" de Ajustes: si la app muestra la notificación local de altas nuevas. */
    fun observarNotificacionesActivas(): Flow<Boolean>

    suspend fun guardarNotificacionesActivas(activas: Boolean)

    /** La bienvenida, el tutorial y las preferencias ya se mostraron: no vuelven a aparecer (RF05). */
    fun observarPrimerInicioHecho(): Flow<Boolean>

    suspend fun marcarPrimerInicioHecho()

    /** El recorrido de once pasos por las pantallas ya se hizo o se omitió. */
    fun observarRecorridoHecho(): Flow<Boolean>

    suspend fun marcarRecorridoHecho()

    /** Hasta qué hora de llegada ya se recordaron las altas sin revisar de una red (cambio 13); null si nunca. */
    suspend fun recordatoriosHasta(redId: String): Instant?

    suspend fun guardarRecordatoriosHasta(redId: String, hasta: Instant)
}

/** Si el teléfono tiene conexión. Decide si una acción se envía en el momento o se encola. */
interface Conectividad {

    val conectado: Flow<Boolean>

    suspend fun hayConexion(): Boolean
}

/** Genera los identificadores únicos de las acciones pendientes. Es una interfaz para poder fijarlos en las pruebas. */
fun interface GeneradorDeIds {
    fun nuevo(): String
}
