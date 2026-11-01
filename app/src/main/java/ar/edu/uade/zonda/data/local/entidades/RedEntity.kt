package ar.edu.uade.zonda.data.local.entidades

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Una red pareada (tabla `redes`). Las fechas se guardan como milisegundos desde 1970 (UTC).
 * El token no está acá: va al almacenamiento seguro con la clave `token_<id>`.
 */
@Entity(tableName = "redes")
data class RedEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val urlBase: String,
    val cursor: String?,
    val ultimaSincronizacion: Long?,
    val telegramVinculado: Boolean,
    val activa: Boolean,
    val creadaEn: Long,
    // Columnas agregadas en la versión 2 de la base (ZondaDatabase.MIGRACION_1_2).
    @ColumnInfo(defaultValue = "0") val accesoVencido: Boolean = false,
    val accesoEmitidoEn: Long? = null,
    val accesoVenceEn: Long? = null,
    // Versión 4 (ZondaDatabase.MIGRACION_3_4): el usuario del bot de Telegram de la red, sin la arroba.
    val telegramBot: String? = null,
    // Versión 6 (ZondaDatabase.MIGRACION_5_6): el perfil de este teléfono en la red y su id en el servidor.
    @ColumnInfo(defaultValue = "ADMINISTRADOR") val perfil: String = "ADMINISTRADOR",
    val dispositivoId: String? = null,
    // Versión 8 (ZondaDatabase.MIGRACION_7_8): el permiso de bloquear de este teléfono si es analista (decisión 51).
    @ColumnInfo(defaultValue = "0") val permisoDeBloquear: Boolean = false,
)

/** Severidad elegida por la red para cada tipo de ataque. Si no hay filas, rige la recomendada. */
@Entity(
    tableName = "severidades",
    primaryKeys = ["redId", "tipoAtaque"],
    foreignKeys = [ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE)],
)
data class SeveridadEntity(
    val redId: String,
    val tipoAtaque: String,
    val severidad: String,
)

/**
 * Nombre de un equipo de la red (`GET /catalogo`), para mostrar nombres en vez de IP, y su importancia
 * (`GET /equipos`, versión 6 de la base; decisión 41).
 */
@Entity(
    tableName = "catalogo_equipos",
    primaryKeys = ["redId", "ip"],
    foreignKeys = [ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE)],
)
data class CatalogoEquipoEntity(
    val redId: String,
    val ip: String,
    val nombre: String,
    @ColumnInfo(defaultValue = "NORMAL") val importancia: String = "NORMAL",
)

/** Una persona del equipo de la red (`GET /personas`, versión 6; decisión 39). */
@Entity(
    tableName = "personas",
    primaryKeys = ["redId", "id"],
    foreignKeys = [ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE)],
)
data class PersonaEntity(
    val redId: String,
    val id: String,
    val nombre: String?,
    val telefono: String,
    val perfil: String,
    val ultimoUso: Long?,
    val vos: Boolean,
    /** El orden en que se sumaron, como lo manda el servidor. */
    val orden: Int,
    // Versión 7 (ZondaDatabase.MIGRACION_6_7): si es un navegador con el tablero web (decisión 51).
    @ColumnInfo(defaultValue = "0")
    val navegador: Boolean = false,
    // Versión 8 (ZondaDatabase.MIGRACION_7_8): el permiso de bloquear de un analista (decisión 51).
    @ColumnInfo(defaultValue = "0")
    val permisoDeBloquear: Boolean = false,
)

/** Una línea del registro de cambios de la red (`GET /registro`, versión 6; decisión 40). */
@Entity(
    tableName = "registro_cambios",
    primaryKeys = ["redId", "id"],
    foreignKeys = [ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE)],
)
data class CambioRegistroEntity(
    val redId: String,
    val id: Long,
    val fecha: Long,
    val tipo: String,
    val autorId: String?,
    val autor: String,
    val objeto: String,
    val nombre: String?,
    val antes: String?,
    val despues: String?,
    val motivo: String?,
)

/** Servicios conocidos de la red, para el filtro de la lista. */
@Entity(
    tableName = "catalogo_servicios",
    primaryKeys = ["redId", "servicio"],
    foreignKeys = [ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE)],
)
data class CatalogoServicioEntity(
    val redId: String,
    val servicio: String,
)
