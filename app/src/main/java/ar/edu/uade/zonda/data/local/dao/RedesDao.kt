package ar.edu.uade.zonda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ar.edu.uade.zonda.data.local.entidades.CambioRegistroEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.local.entidades.PersonaEntity
import ar.edu.uade.zonda.data.local.entidades.RedEntity
import ar.edu.uade.zonda.data.local.entidades.SeveridadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Redes pareadas, su severidad por tipo, su catálogo con la importancia de cada equipo, su gente y su
 * registro de cambios (tablas `redes`, `severidades`, `catalogo_*`, `personas` y `registro_cambios`).
 */
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

    /**
     * Solo una red puede estar activa a la vez. Un id que no es de ninguna red no cambia nada: el enlace
     * de un aviso trae el id de su red, y un enlace armado a mano no puede dejar la app sin red activa.
     */
    @Transaction
    open suspend fun activar(id: String) {
        if (obtener(id) == null) return
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

    /** Solo el cursor, página por página; la hora se registra cuando la sincronización termina. */
    @Query("UPDATE redes SET cursor = :cursor WHERE id = :id")
    abstract suspend fun actualizarCursor(id: String, cursor: String?)

    /** Pareo repetido de una red que ya estaba (token vencido o revocado): se actualizan los datos sin perder la caché. */
    @Query(
        "UPDATE redes SET nombre = :nombre, urlBase = :urlBase, accesoVencido = 0, " +
            "accesoEmitidoEn = :emitido, accesoVenceEn = :vence WHERE id = :id",
    )
    abstract suspend fun actualizarDatosDePareo(id: String, nombre: String, urlBase: String, emitido: Long?, vence: Long?)

    @Query("UPDATE redes SET accesoVencido = :vencido WHERE id = :id")
    abstract suspend fun marcarAccesoVencido(id: String, vencido: Boolean)

    /** Borrar datos locales: sin cursor ni hora, la sincronización siguiente baja todo de nuevo. */
    @Query("UPDATE redes SET cursor = NULL, ultimaSincronizacion = NULL WHERE id = :id")
    abstract suspend fun reiniciarSincronizacion(id: String)

    @Query("UPDATE redes SET telegramVinculado = :vinculado WHERE id = :id")
    abstract suspend fun marcarTelegram(id: String, vinculado: Boolean)

    /** Lo que dijo el servidor de Telegram (`GET /telegram`): si hay un chat vinculado y con qué bot. */
    @Query("UPDATE redes SET telegramVinculado = :vinculado, telegramBot = :bot WHERE id = :id")
    abstract suspend fun actualizarTelegram(id: String, vinculado: Boolean, bot: String)

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

    /** Los nombres del catálogo (`GET /catalogo`). La importancia de cada equipo que ya estaba se conserva. */
    @Transaction
    open suspend fun guardarCatalogo(redId: String, equipos: List<CatalogoEquipoEntity>, servicios: List<CatalogoServicioEntity>) {
        val importancias = equiposDe(redId).associate { it.ip to it.importancia }
        borrarEquipos(redId)
        borrarServicios(redId)
        insertarEquipos(equipos.map { equipo -> importancias[equipo.ip]?.let { equipo.copy(importancia = it) } ?: equipo })
        insertarServicios(servicios)
    }

    @Query("SELECT * FROM catalogo_equipos WHERE redId = :redId")
    abstract suspend fun equiposDe(redId: String): List<CatalogoEquipoEntity>

    /** Los equipos con su importancia (`GET /equipos`): reemplazan nombre e importancia de cada uno. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarEquipos(equipos: List<CatalogoEquipoEntity>)

    @Query("UPDATE catalogo_equipos SET importancia = :importancia WHERE redId = :redId AND ip = :ip")
    abstract suspend fun actualizarImportancia(redId: String, ip: String, importancia: String)

    // ---- Personas y registro de cambios (decisiones 39 y 40) ----

    @Query("UPDATE redes SET perfil = :perfil, dispositivoId = :dispositivoId WHERE id = :id")
    abstract suspend fun actualizarPerfil(id: String, perfil: String, dispositivoId: String?)

    @Query("SELECT * FROM personas WHERE redId = :redId ORDER BY orden")
    abstract fun observarPersonas(redId: String): Flow<List<PersonaEntity>>

    @Query("DELETE FROM personas WHERE redId = :redId")
    abstract suspend fun borrarPersonas(redId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarPersonas(filas: List<PersonaEntity>)

    /** La gente que dijo el servidor reemplaza a la guardada. */
    @Transaction
    open suspend fun guardarPersonas(redId: String, filas: List<PersonaEntity>) {
        borrarPersonas(redId)
        insertarPersonas(filas)
    }

    @Query("SELECT * FROM registro_cambios WHERE redId = :redId ORDER BY fecha DESC, id DESC")
    abstract fun observarRegistro(redId: String): Flow<List<CambioRegistroEntity>>

    @Query("DELETE FROM registro_cambios WHERE redId = :redId")
    abstract suspend fun borrarRegistro(redId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarRegistro(filas: List<CambioRegistroEntity>)

    @Transaction
    open suspend fun guardarRegistro(redId: String, filas: List<CambioRegistroEntity>) {
        borrarRegistro(redId)
        insertarRegistro(filas)
    }
}
