package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.Revision
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Resultado de una sincronización (RF01, criterio 5 y RNF08). */
data class ResultadoSincronizacion(
    val nuevas: Int,
    val modificadas: Int,
    /** Alertas de severidad alta que llegaron nuevas; disparan la notificación local. */
    val altasNuevas: Int,
    val accionesEnviadas: Int,
    val accionesConError: Int,
    val hora: Instant,
    /** Nuevas por severidad media y baja, para la notificación local según la preferencia de cada una (RF05). */
    val mediasNuevas: Int = 0,
    val bajasNuevas: Int = 0,
)

/**
 * Acceso a las alertas de cada red. La base local es la fuente de verdad: todo lo que se observa
 * sale de ella, y la red solo la actualiza (informe, sección 14). La implementación está en la capa
 * de datos; esta interfaz vive en el dominio para que los casos de uso se prueben con una falsa.
 */
interface AlertasRepository {

    /** Alertas de una red, de la más reciente a la más antigua, con el filtro aplicado. Se actualiza solo. */
    fun observarAlertas(redId: String, filtro: FiltroAlertas): Flow<List<Alerta>>

    /** Una alerta por id, o null si todavía no está en el teléfono. */
    fun observarAlerta(redId: String, alertaId: String): Flow<Alerta?>

    /** Contadores de alertas sin revisar por severidad, para el semáforo. */
    fun observarContadoresSinRevisar(redId: String): Flow<ContadoresSinRevisar>

    /** Alertas que empezaron desde [desde], para calcular el Resumen. */
    suspend fun obtenerAlertasDesde(redId: String, desde: Instant): List<Alerta>

    /** Guarda la revisión en el teléfono y deja la alerta con ese estado. No toca el servidor. */
    suspend fun aplicarRevisionLocal(redId: String, alertaId: String, revision: Revision): Alerta

    /** Deshacer: vuelve la alerta al último estado que llegó del servidor. */
    suspend fun restaurarEstadoDelServidor(redId: String, alertaId: String)

    /** Pide el detalle al servidor (`GET /alertas/{id}`) y lo guarda. Lanza [ar.edu.uade.zonda.domain.model.ErrorZonda]. */
    suspend fun descargarDetalle(redId: String, alertaId: String): Alerta

    /**
     * Envía la cola de acciones pendientes en orden y después trae las alertas nuevas o modificadas
     * desde el cursor de la red. Lanza [ar.edu.uade.zonda.domain.model.ErrorZonda] si no pudo ni empezar.
     */
    suspend fun sincronizar(redId: String): ResultadoSincronizacion

    /** Borra todas las alertas de una red (al quitar el dispositivo o borrar los datos locales). */
    suspend fun borrarDeRed(redId: String)
}
