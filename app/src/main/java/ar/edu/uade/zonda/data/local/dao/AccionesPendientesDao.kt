package ar.edu.uade.zonda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ar.edu.uade.zonda.data.local.entidades.AccionPendienteEntity
import kotlinx.coroutines.flow.Flow

/** Cola de acciones pendientes de envío (tabla `acciones_pendientes`). */
@Dao
abstract class AccionesPendientesDao {

    /** De la más antigua a la más nueva: en ese orden se envían. */
    @Query("SELECT * FROM acciones_pendientes WHERE redId = :redId ORDER BY creadaEn ASC")
    abstract fun observar(redId: String): Flow<List<AccionPendienteEntity>>

    @Query("SELECT * FROM acciones_pendientes WHERE redId = :redId ORDER BY creadaEn ASC")
    abstract suspend fun listar(redId: String): List<AccionPendienteEntity>

    @Query("SELECT * FROM acciones_pendientes WHERE redId = :redId AND alertaId = :alertaId LIMIT 1")
    abstract suspend fun deAlerta(redId: String, alertaId: String): AccionPendienteEntity?

    @Query("DELETE FROM acciones_pendientes WHERE redId = :redId AND alertaId = :alertaId")
    abstract suspend fun borrarDeAlerta(redId: String, alertaId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertar(accion: AccionPendienteEntity)

    @Query("DELETE FROM acciones_pendientes WHERE id = :id")
    abstract suspend fun borrar(id: String)

    @Query("DELETE FROM acciones_pendientes WHERE redId = :redId")
    abstract suspend fun borrarDeRed(redId: String)

    @Query("UPDATE acciones_pendientes SET estado = :estado, intentos = :intentos, ultimoError = :ultimoError WHERE id = :id")
    abstract suspend fun actualizarEnvio(id: String, estado: String, intentos: Int, ultimoError: String?)

    /** Como máximo hay una acción por alerta: la nueva reemplaza a la anterior. */
    @Transaction
    open suspend fun encolar(accion: AccionPendienteEntity) {
        accion.alertaId?.let { borrarDeAlerta(accion.redId, it) }
        insertar(accion)
    }
}
