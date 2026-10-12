package ar.edu.uade.zonda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.local.entidades.RedEntity
import ar.edu.uade.zonda.data.local.entidades.SeveridadEntity
import kotlinx.coroutines.flow.Flow

/** Redes pareadas, su severidad por tipo y su catálogo (tablas `redes`, `severidades`, `catalogo_*`). */
@Dao
abstract class RedesDao {

    @Query("SELECT * FROM redes ORDER BY creadaEn ASC")
    abstract fun observar(): Flow<List<RedEntity>>

    @Query("SELECT * FROM redes WHERE activa = 1 LIMIT 1")
    abstract fun observarActiva(): Flow<RedEntity?>

    @Query("SELECT * FROM redes WHERE id = :id")
    abstract suspend fun obtener(id: String): RedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertar(red: RedEntity)

    @Query("UPDATE redes SET activa = 0")
    abstract suspend fun desactivarTodas()

    @Query("UPDATE redes SET activa = 1 WHERE id = :id")
    abstract suspend fun marcarActiva(id: String)

    /** Solo una red puede estar activa a la vez. */
    @Transaction
    open suspend fun activar(id: String) {
        desactivarTodas()
        marcarActiva(id)
    }

    /** Una red recién pareada queda activa. */
    @Transaction
    open suspend fun insertarComoActiva(red: RedEntity) {
        desactivarTodas()
        insertar(red.copy(activa = true))
    }

    @Query("DELETE FROM redes WHERE id = :id")
    abstract suspend fun borrar(id: String)

    @Query("UPDATE redes SET cursor = :cursor, ultimaSincronizacion = :hora WHERE id = :id")
    abstract suspend fun actualizarSincronizacion(id: String, cursor: String?, hora: Long)

    @Query("UPDATE redes SET telegramVinculado = :vinculado WHERE id = :id")
    abstract suspend fun marcarTelegram(id: String, vinculado: Boolean)

    @Query("SELECT * FROM severidades WHERE redId = :redId")
    abstract fun observarSeveridades(redId: String): Flow<List<SeveridadEntity>>

    @Query("DELETE FROM severidades WHERE redId = :redId")
    abstract suspend fun borrarSeveridades(redId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarSeveridades(filas: List<SeveridadEntity>)

    @Transaction
    open suspend fun guardarSeveridades(redId: String, filas: List<SeveridadEntity>) {
        borrarSeveridades(redId)
        insertarSeveridades(filas)
    }

    @Query("SELECT * FROM catalogo_equipos WHERE redId = :redId")
    abstract fun observarEquipos(redId: String): Flow<List<CatalogoEquipoEntity>>

    @Query("SELECT * FROM catalogo_servicios WHERE redId = :redId ORDER BY servicio ASC")
    abstract fun observarServicios(redId: String): Flow<List<CatalogoServicioEntity>>

    @Query("DELETE FROM catalogo_equipos WHERE redId = :redId")
    abstract suspend fun borrarEquipos(redId: String)

    @Query("DELETE FROM catalogo_servicios WHERE redId = :redId")
    abstract suspend fun borrarServicios(redId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarEquipos(equipos: List<CatalogoEquipoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarServicios(servicios: List<CatalogoServicioEntity>)

    @Transaction
    open suspend fun guardarCatalogo(redId: String, equipos: List<CatalogoEquipoEntity>, servicios: List<CatalogoServicioEntity>) {
        borrarEquipos(redId)
        borrarServicios(redId)
        insertarEquipos(equipos)
        insertarServicios(servicios)
    }
}
