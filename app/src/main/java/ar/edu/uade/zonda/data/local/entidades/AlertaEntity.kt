package ar.edu.uade.zonda.data.local.entidades

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Una alerta (episodio) tal como se guarda en la tabla `alertas`. Es la fuente de verdad de la
 * interfaz: lo que llega de la API se vuelca acá y las pantallas leen de acá.
 *
 * Los enums del dominio se guardan por nombre (texto) y las fechas en milisegundos desde 1970.
 * Al borrar la red se borran sus alertas (clave foránea con borrado en cascada).
 */
@Entity(
    tableName = "alertas",
    foreignKeys = [ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("redId", "inicio"),
        Index("redId", "estadoRevision"),
        Index("redId", "severidad"),
    ],
)
data class AlertaEntity(
    @PrimaryKey val id: String,
    val redId: String,
    val creadaEn: Long,
    val inicio: Long,
    val fin: Long,
    val cantidadFlujos: Int,
    val uidReferencia: String,
    val origenIp: String,
    val origenNombre: String?,
    val destinoIp: String,
    val destinoNombre: String?,
    val servicio: String,
    val protocolo: String? = null,
    val puerto: Int,
    val puertoReferencia: Int,
    val puertosDistintos: Int,
    val tipoAtaque: String,
    val confianzaTipo: Double?,
    val severidad: String,
    val veredictoIa: Boolean,
    val errorReconstruccion: Double,
    val umbralIa: Double,
    val veredictoUmbrales: Boolean,
    val contexto60s: Int,
    val umbralServicio: Int,
    val servicioSinLineaBase: Boolean,
    val veredictoReglas: Boolean,
    val parDestino: String,
    val parEnCatalogo: Boolean,
    /** Estado actual, que puede venir de una revisión local todavía no enviada. */
    val estadoRevision: String,
    val version: Int,
    val actualizadaEn: Long,
    /** Cuándo salió el primer aviso de Telegram (versión 4 de la base). */
    val avisoTelegramEn: Long? = null,
    // Versión 6: el responsable de ahora (decisión 42), que puede venir de una asignación local todavía no enviada.
    val responsableId: String? = null,
    val responsable: String? = null,
    val asignadaPorId: String? = null,
    val asignadaPor: String? = null,
    val asignadaEn: Long? = null,
)

/**
 * Un cambio de responsable de una alerta (tabla `asignaciones`, versión 6), para el historial. Las de
 * origen `ESTE_TELEFONO` todavía no las confirmó el servidor, como las revisiones.
 */
@Entity(
    tableName = "asignaciones",
    foreignKeys = [ForeignKey(entity = AlertaEntity::class, parentColumns = ["id"], childColumns = ["alertaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("alertaId")],
)
data class AsignacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertaId: String,
    val responsableId: String?,
    val responsable: String?,
    val porId: String,
    val por: String,
    val fecha: Long,
    val origen: String,
)

/**
 * Una asignación hecha en el teléfono que todavía no llegó al servidor (tabla `asignaciones_pendientes`,
 * versión 6). Hay como mucho una por alerta: si dos asignan a la vez queda la última (decisión 42).
 */
@Entity(
    tableName = "asignaciones_pendientes",
    foreignKeys = [
        ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AlertaEntity::class, parentColumns = ["id"], childColumns = ["alertaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("redId")],
)
data class AsignacionPendienteEntity(
    @PrimaryKey val alertaId: String,
    val redId: String,
    /** El id único de la acción: el servidor no la aplica dos veces si se reenvía. */
    val accionId: String,
    val responsableId: String?,
    val creadaEn: Long,
    val intentos: Int,
    val estado: String,
    val ultimoError: String?,
)

/**
 * Una revisión registrada sobre una alerta (tabla `revisiones`). Las que tienen origen
 * `ESTE_TELEFONO` todavía no fueron confirmadas por el servidor; Deshacer las borra.
 */
@Entity(
    tableName = "revisiones",
    foreignKeys = [ForeignKey(entity = AlertaEntity::class, parentColumns = ["id"], childColumns = ["alertaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("alertaId")],
)
data class RevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertaId: String,
    val resultado: String,
    val motivo: String,
    val comentario: String,
    val autor: String?,
    /** Cuándo llegó al servidor; en una revisión local que no salió, cuándo se hizo. */
    val fecha: Long,
    val origen: String,
    /** Cuándo se revisó en el teléfono que la hizo (decisión 21). */
    val revisadaEn: Long? = null,
    /** La persona de ese teléfono (versión 6; decisión 39). */
    val persona: String? = null,
)

/** Acción hecha en el teléfono que todavía no llegó al servidor (tabla `acciones_pendientes`). */
@Entity(
    tableName = "acciones_pendientes",
    foreignKeys = [
        ForeignKey(entity = RedEntity::class, parentColumns = ["id"], childColumns = ["redId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AlertaEntity::class, parentColumns = ["id"], childColumns = ["alertaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("redId"), Index(value = ["alertaId"], unique = true)],
)
data class AccionPendienteEntity(
    @PrimaryKey val id: String,
    val redId: String,
    val tipo: String,
    val alertaId: String?,
    val resultado: String?,
    val motivo: String?,
    val comentario: String?,
    val fechaRevision: Long?,
    /** Severidades codificadas como texto (ver `SeveridadesCodec`), solo para CAMBIAR_SEVERIDADES. */
    val severidades: String?,
    val versionVista: Int?,
    val intentos: Int,
    val estado: String,
    val creadaEn: Long,
    val ultimoError: String?,
    val ajenaResultado: String?,
    val ajenaMotivo: String?,
    val ajenaComentario: String?,
    val ajenaAutor: String?,
    val ajenaFecha: Long?,
    val ajenaRevisadaEn: Long? = null,
    // Versión 6: el motivo de un cambio de configuración y, para la importancia, el equipo (decisiones 40 y 41).
    val motivoCambio: String? = null,
    val equipoIp: String? = null,
    val importancia: String? = null,
)

/** Una alerta con su historial, sus asignaciones y la acción pendiente que tenga, en una sola lectura. */
data class AlertaConDetalles(
    @Embedded val alerta: AlertaEntity,
    @Relation(parentColumn = "id", entityColumn = "alertaId")
    val revisiones: List<RevisionEntity>,
    @Relation(parentColumn = "id", entityColumn = "alertaId")
    val accion: AccionPendienteEntity?,
    @Relation(parentColumn = "id", entityColumn = "alertaId")
    val asignaciones: List<AsignacionEntity> = emptyList(),
)

/** Resultado de la consulta de contadores: cantidad de alertas sin revisar por severidad. */
data class ConteoPorSeveridad(
    val severidad: String,
    val cantidad: Int,
)
