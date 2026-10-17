package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.OpcionesDeFiltro
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.Severidad
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
    /**
     * Las alertas que llegaron nuevas, de cualquier severidad, con si ya tenían aviso de Telegram al
     * bajarlas: Zonda avisa las que Telegram no avisó (cambio 13 y 1.0.4).
     */
    val alertasNuevas: List<AlertaNueva> = emptyList(),
    /**
     * Alertas cuya revisión de este teléfono no se aplicó en esta sincronización porque otra persona la
     * revisó antes (409). Pasa una sola vez por revisión: el envío queda en conflicto y no se reintenta.
     */
    val conflictosNuevos: List<String> = emptyList(),
) {
    /** Las altas nuevas: no entran en el recordatorio de esta misma vuelta. */
    val idsAltasNuevas: List<String>
        get() = alertasNuevas.filter { it.severidad == Severidad.ALTA }.map { it.id }
}

/** Una alerta que llegó nueva al sincronizar: su severidad y si el bot ya la avisó por Telegram. */
data class AlertaNueva(val id: String, val severidad: Severidad, val conAvisoTelegram: Boolean = false)

/**
 * Acceso a las alertas de cada red. La base local es la fuente de verdad: todo lo que se observa
 * sale de ella, y la red solo la actualiza (informe, sección 14). La implementación está en la capa
 * de datos; esta interfaz vive en el dominio para que los casos de uso se prueben con una falsa.
 */
interface AlertasRepository {

    /** Alertas de una red, de la más reciente a la más antigua, con el filtro aplicado. Se actualiza solo. */
    fun observarAlertas(redId: String, filtro: FiltroAlertas): Flow<List<Alerta>>

    /** Los servicios y los equipos destino de las alertas guardadas, para las opciones de Más filtros. */
    fun observarOpcionesDeFiltro(redId: String): Flow<OpcionesDeFiltro>

    /** Una alerta por id, o null si todavía no está en el teléfono. */
    fun observarAlerta(redId: String, alertaId: String): Flow<Alerta?>

    /** Contadores de alertas sin revisar por severidad, para el semáforo. */
    fun observarContadoresSinRevisar(redId: String): Flow<ContadoresSinRevisar>

    /** Cuántas alertas de la red hay guardadas en el teléfono (Ajustes, Datos locales). */
    fun observarCantidad(redId: String): Flow<Int>

    /** Cuántas alertas hay guardadas en total, de todas las redes (Borrar datos locales). */
    fun observarCantidadTotal(): Flow<Int>

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

    /**
     * Cómo va la primera descarga de la red (la que baja los últimos 30 días después del pareo), o
     * null si no hay ninguna en curso.
     */
    fun observarProgresoDescarga(redId: String): Flow<ProgresoDescarga?>

    /** Borra todas las alertas de una red (al quitar el dispositivo o borrar los datos locales). */
    suspend fun borrarDeRed(redId: String)
}
