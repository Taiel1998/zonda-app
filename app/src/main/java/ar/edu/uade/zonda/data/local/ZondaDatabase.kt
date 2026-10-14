package ar.edu.uade.zonda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ar.edu.uade.zonda.data.local.dao.AccionesPendientesDao
import ar.edu.uade.zonda.data.local.dao.AlertasDao
import ar.edu.uade.zonda.data.local.dao.RedesDao
import ar.edu.uade.zonda.data.local.entidades.AccionPendienteEntity
import ar.edu.uade.zonda.data.local.entidades.AlertaEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.local.entidades.RedEntity
import ar.edu.uade.zonda.data.local.entidades.RevisionEntity
import ar.edu.uade.zonda.data.local.entidades.SeveridadEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Base de datos local de Zonda. Es la fuente de verdad de la interfaz (informe, sección 14).
 *
 * En el teléfono se abre cifrada con SQLCipher (RNF03): la frase de cifrado la aporta
 * [ar.edu.uade.zonda.data.seguridad.ClaveDeBase]. En las pruebas se abre en memoria y sin cifrar,
 * porque las bibliotecas nativas de SQLCipher no cargan en la JVM (docs/decisiones.md, decisión 8).
 */
@Database(
    entities = [
        RedEntity::class,
        SeveridadEntity::class,
        CatalogoEquipoEntity::class,
        CatalogoServicioEntity::class,
        AlertaEntity::class,
        RevisionEntity::class,
        AccionPendienteEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class ZondaDatabase : RoomDatabase() {

    abstract fun alertasDao(): AlertasDao

    abstract fun accionesPendientesDao(): AccionesPendientesDao

    abstract fun redesDao(): RedesDao

    companion object {
        const val NOMBRE_ARCHIVO = "zonda.db"

        /**
         * Versión 2 (fase 8): `redes.accesoVencido`, para pedir un pareo nuevo después de un 401 sin
         * borrar la caché, y cuándo se emitió y cuándo vence el acceso, para Detalles técnicos.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE redes ADD COLUMN accesoVencido INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE redes ADD COLUMN accesoEmitidoEn INTEGER")
                db.execSQL("ALTER TABLE redes ADD COLUMN accesoVenceEn INTEGER")
            }
        }

        /**
         * Versión 3 (paso 4 del plan del Figma): el protocolo de cada alerta, la hora en que se revisó en
         * el teléfono (decisión 21) y los motivos del Figma. Las revisiones y las acciones pendientes con
         * los motivos anteriores pasan al nombre nuevo, igual que en el backend.
         */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alertas ADD COLUMN protocolo TEXT")
                db.execSQL("ALTER TABLE revisiones ADD COLUMN revisadaEn INTEGER")
                db.execSQL("ALTER TABLE acciones_pendientes ADD COLUMN ajenaRevisadaEn INTEGER")
                for ((anterior, actual) in MOTIVOS_ANTERIORES) {
                    db.execSQL("UPDATE revisiones SET motivo = ? WHERE motivo = ?", arrayOf(actual, anterior))
                    db.execSQL("UPDATE acciones_pendientes SET motivo = ? WHERE motivo = ?", arrayOf(actual, anterior))
                    db.execSQL("UPDATE acciones_pendientes SET ajenaMotivo = ? WHERE ajenaMotivo = ?", arrayOf(actual, anterior))
                }
            }
        }

        /**
         * Versión 4 (fase 10, Telegram): cuándo salió el aviso de Telegram de cada alerta, para el
         * historial del detalle, y el usuario del bot de cada red, que dice el servidor.
         */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alertas ADD COLUMN avisoTelegramEn INTEGER")
                db.execSQL("ALTER TABLE redes ADD COLUMN telegramBot TEXT")
            }
        }

        /** Los nombres anteriores y los del Figma, fijos acá: una migración no cambia aunque cambie el enum. */
        private val MOTIVOS_ANTERIORES = mapOf(
            "EQUIPO_AISLADO" to "BLOQUEADO_MANUALMENTE",
            "CREDENCIALES_CAMBIADAS" to "CONTRASENA_CAMBIADA",
            "EN_INVESTIGACION" to "INVESTIGANDO",
            "HERRAMIENTA_AUTORIZADA" to "USUARIO_AUTORIZADO",
            "PRUEBA_DEL_EQUIPO_DE_IT" to "OTRO",
            "SERVICIO_NUEVO" to "EQUIPO_NUEVO",
        )

        /** Abre (o crea) la base cifrada en el almacenamiento interno de la app, migrándola si hace falta. */
        fun cifrada(context: Context, clave: ByteArray): ZondaDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, ZondaDatabase::class.java, NOMBRE_ARCHIVO)
                .openHelperFactory(SupportOpenHelperFactory(clave))
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4)
                .build()
        }
    }
}
