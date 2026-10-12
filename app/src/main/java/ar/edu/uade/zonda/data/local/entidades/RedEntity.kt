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
    /** Columna agregada en la versión 2 de la base (ZondaDatabase.MIGRACION_1_2). */
    @ColumnInfo(defaultValue = "0") val accesoVencido: Boolean = false,
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

/** Nombre de un equipo de la red (`GET /catalogo`), para mostrar nombres en vez de IP. */
@Entity(
    tableName = "catalogo_equipos",
    primaryKeys = ["redId", "ip"],
    foreignKeys = [ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE)],
)
data class CatalogoEquipoEntity(
    val redId: String,
    val ip: String,
    val nombre: String,
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
