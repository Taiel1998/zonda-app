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

    /**
     * Las que todavía pueden viajar: pendientes y las que quedaron "enviando" por un corte. Las de
     * error esperan el reintento manual y las de conflicto, una decisión de la persona.
     */
    @Query("SELECT * FROM acciones_pendientes WHERE redId = :redId AND estado IN ('PENDIENTE', 'ENVIANDO') ORDER BY creadaEn ASC")
    abstract suspend fun listarParaEnviar(redId: String): List<AccionPendienteEntity>

    /** El servidor respondió 409: se guarda la revisión de la otra persona para mostrarla en el detalle. */
    @Query(
        """
        UPDATE acciones_pendientes
        SET estado = 'CONFLICTO', ajenaResultado = :resultado, ajenaMotivo = :motivo,
            ajenaComentario = :comentario, ajenaAutor = :autor, ajenaFecha = :fecha, ajenaRevisadaEn = :revisadaEn
        WHERE id = :id
        """,
    )
    abstract suspend fun marcarConflicto(
        id: String,
        resultado: String,
        motivo: String,
        comentario: String,
        autor: String?,
        fecha: Long,
        revisadaEn: Long?,
    )

    /** Reintento manual de una acción con error: vuelve a pendiente con los intentos en cero. */
    @Query("UPDATE acciones_pendientes SET estado = 'PENDIENTE', intentos = 0, ultimoError = NULL WHERE id = :id")
    abstract suspend fun reintentar(id: String)

    /** Como máximo hay una acción por alerta: la nueva reemplaza a la anterior. */
    @Transaction
    open suspend fun encolar(accion: AccionPendienteEntity) {
        accion.alertaId?.let { borrarDeAlerta(accion.redId, it) }
        insertar(accion)
    }
}
