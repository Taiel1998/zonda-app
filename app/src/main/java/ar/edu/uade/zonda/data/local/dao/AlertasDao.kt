package ar.edu.uade.zonda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ar.edu.uade.zonda.data.local.entidades.AlertaConDetalles
import ar.edu.uade.zonda.data.local.entidades.AlertaEntity
import ar.edu.uade.zonda.data.local.entidades.ConteoPorSeveridad
import ar.edu.uade.zonda.data.local.entidades.RevisionEntity
import ar.edu.uade.zonda.data.local.entidades.ValorFrecuente
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a las tablas `alertas` y `revisiones`. Los métodos que devuelven [Flow] se actualizan solos
 * cuando cambia la tabla: es lo que hace que la lista y el semáforo cambien sin recargar.
 */
@Dao
abstract class AlertasDao {

    /**
     * Lista filtrada (RF02). Un filtro en null, o con su bandera de "todos" prendida, no filtra; así una
     * sola consulta cubre todas las combinaciones sin armar SQL a mano. [otrosServicios] es el chip "Otros"
     * de Más filtros: los servicios que no están en [serviciosConChip].
     */
    @Transaction
    @Query(
        """
        SELECT * FROM alertas
        WHERE redId = :redId
          AND (:todosLosEstados OR estadoRevision IN (:estados))
          AND (:todasLasSeveridades OR severidad IN (:severidades))
          AND (:desde IS NULL OR inicio >= :desde)
          AND (:servicio IS NULL OR servicio = :servicio)
          AND (NOT :otrosServicios OR servicio NOT IN (:serviciosConChip))
          AND (:destinoIp IS NULL OR destinoIp = :destinoIp)
          AND (:metodo IS NULL
               OR (:metodo = 'IA' AND veredictoIa = 1)
               OR (:metodo = 'UMBRALES' AND veredictoUmbrales = 1)
               OR (:metodo = 'REGLAS' AND veredictoReglas = 1))
        ORDER BY inicio DESC
        """,
    )
    abstract fun observar(
        redId: String,
        todosLosEstados: Boolean,
        estados: List<String>,
        todasLasSeveridades: Boolean,
        severidades: List<String>,
        desde: Long?,
        servicio: String?,
        otrosServicios: Boolean,
        serviciosConChip: List<String>,
        destinoIp: String?,
        metodo: String?,
    ): Flow<List<AlertaConDetalles>>

    @Transaction
    @Query("SELECT * FROM alertas WHERE redId = :redId AND id = :alertaId")
    abstract fun observarUna(redId: String, alertaId: String): Flow<AlertaConDetalles?>

    /** Contadores del semáforo (RF01): solo las sin revisar, agrupadas por severidad. */
    @Query("SELECT severidad, COUNT(*) AS cantidad FROM alertas WHERE redId = :redId AND estadoRevision = 'SIN_REVISAR' GROUP BY severidad")
    abstract fun observarConteoSinRevisar(redId: String): Flow<List<ConteoPorSeveridad>>

    @Query("SELECT COUNT(*) FROM alertas WHERE redId = :redId")
    abstract fun observarCantidad(redId: String): Flow<Int>

    /** Los servicios de las alertas guardadas, del más frecuente al menos (opciones de Más filtros). */
    @Query("SELECT servicio AS valor, NULL AS nombre, COUNT(*) AS cantidad FROM alertas WHERE redId = :redId GROUP BY servicio ORDER BY cantidad DESC, servicio")
    abstract fun observarServicios(redId: String): Flow<List<ValorFrecuente>>

    /** Los equipos destino de las alertas guardadas, con su nombre si el catálogo lo tiene. */
    @Query("SELECT destinoIp AS valor, MAX(destinoNombre) AS nombre, COUNT(*) AS cantidad FROM alertas WHERE redId = :redId GROUP BY destinoIp ORDER BY cantidad DESC, destinoIp")
    abstract fun observarDestinos(redId: String): Flow<List<ValorFrecuente>>

    @Query("SELECT COUNT(*) FROM alertas")
    abstract fun observarCantidadTotal(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM alertas WHERE redId = :redId AND inicio >= :desde ORDER BY inicio DESC")
    abstract suspend fun desde(redId: String, desde: Long): List<AlertaConDetalles>

    @Transaction
    @Query("SELECT * FROM alertas WHERE id = :alertaId")
    abstract suspend fun obtener(alertaId: String): AlertaConDetalles?

    @Upsert
    abstract suspend fun upsertAlertas(alertas: List<AlertaEntity>)

    @Insert
    abstract suspend fun insertarRevision(revision: RevisionEntity): Long

    @Insert
    abstract suspend fun insertarRevisiones(revisiones: List<RevisionEntity>)

    @Query("DELETE FROM revisiones WHERE alertaId = :alertaId AND origen = 'ESTE_TELEFONO'")
    abstract suspend fun borrarRevisionesLocales(alertaId: String)

    @Query("DELETE FROM revisiones WHERE alertaId = :alertaId")
    abstract suspend fun borrarRevisiones(alertaId: String)

    @Query("UPDATE alertas SET estadoRevision = :estado WHERE id = :alertaId")
    abstract suspend fun actualizarEstadoRevision(alertaId: String, estado: String)

    @Query("SELECT * FROM revisiones WHERE alertaId = :alertaId AND origen = 'SERVIDOR' ORDER BY fecha DESC LIMIT 1")
    abstract suspend fun ultimaRevisionDelServidor(alertaId: String): RevisionEntity?

    @Query("DELETE FROM alertas WHERE redId = :redId")
    abstract suspend fun borrarDeRed(redId: String)

    /** Purga de la caché: alertas de más de 30 días (RNF03). Devuelve cuántas borró. */
    @Query("DELETE FROM alertas WHERE redId = :redId AND inicio < :antesDe")
    abstract suspend fun purgarAnterioresA(redId: String, antesDe: Long): Int

    /** De una lista de ids, cuáles ya están en el teléfono (la sincronización cuenta nuevas y modificadas). */
    @Query("SELECT id FROM alertas WHERE redId = :redId AND id IN (:ids)")
    abstract suspend fun idsExistentes(redId: String, ids: List<String>): List<String>

    /**
     * El servidor confirmó la revisión hecha en este teléfono: la revisión local deja de ser
     * provisoria (ya está en el historial que manda el servidor) y la alerta queda como la devolvió la API.
     */
    @Transaction
    open suspend fun confirmarDelServidor(alerta: AlertaEntity, revisiones: List<RevisionEntity>) {
        borrarRevisionesLocales(alerta.id)
        guardarDelServidor(listOf(alerta), mapOf(alerta.id to revisiones))
    }

    /**
     * Revisión hecha en este teléfono: reemplaza la revisión local anterior si había una (nunca
     * llegó al servidor, así que no es historia), agrega la nueva y deja la alerta con ese estado.
     */
    @Transaction
    open suspend fun aplicarRevisionLocal(alertaId: String, revision: RevisionEntity, estado: String) {
        borrarRevisionesLocales(alertaId)
        insertarRevision(revision)
        actualizarEstadoRevision(alertaId, estado)
    }

    /** Deshacer: borra lo local y vuelve al estado de la última revisión que llegó del servidor, o a sin revisar. */
    @Transaction
    open suspend fun restaurarEstadoDelServidor(alertaId: String) {
        borrarRevisionesLocales(alertaId)
        val ultima = ultimaRevisionDelServidor(alertaId)
        actualizarEstadoRevision(alertaId, ultima?.let { estadoDe(it.resultado) } ?: "SIN_REVISAR")
    }

    /**
     * Guarda alertas que llegaron del servidor: la alerta se inserta o actualiza y su historial se
     * reemplaza por el del servidor. Una revisión local pendiente se conserva y sigue mandando sobre
     * el estado que se muestra, porque todavía no se envió.
     */
    @Transaction
    open suspend fun guardarDelServidor(alertas: List<AlertaEntity>, revisionesPorAlerta: Map<String, List<RevisionEntity>>) {
        upsertAlertas(alertas)
        for (alerta in alertas) {
            borrarRevisionesDelServidor(alerta.id)
            revisionesPorAlerta[alerta.id]?.let { insertarRevisiones(it) }
            revisionLocal(alerta.id)?.let { actualizarEstadoRevision(alerta.id, estadoDe(it.resultado)) }
        }
    }

    @Query("DELETE FROM revisiones WHERE alertaId = :alertaId AND origen = 'SERVIDOR'")
    abstract suspend fun borrarRevisionesDelServidor(alertaId: String)

    @Query("SELECT * FROM revisiones WHERE alertaId = :alertaId AND origen = 'ESTE_TELEFONO' ORDER BY fecha DESC LIMIT 1")
    abstract suspend fun revisionLocal(alertaId: String): RevisionEntity?

    private fun estadoDe(resultado: String): String = when (resultado) {
        "ATAQUE_REAL" -> "ATAQUE_REAL"
        "FALSO_POSITIVO" -> "FALSO_POSITIVO"
        else -> "SIN_REVISAR"
    }
}
