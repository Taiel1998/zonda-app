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
import ar.edu.uade.zonda.data.local.entidades.AsignacionEntity
import ar.edu.uade.zonda.data.local.entidades.AsignacionPendienteEntity
import ar.edu.uade.zonda.data.local.entidades.CambioRegistroEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.local.entidades.PersonaEntity
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
        PersonaEntity::class,
        CambioRegistroEntity::class,
        AsignacionEntity::class,
        AsignacionPendienteEntity::class,
    ],
    version = 8,
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

        /**
         * Versión 5 (1.0.3, cambio 9): "solo reglas o umbrales" se separa en servicio no autorizado (las
         * reglas) y volumen inusual (los umbrales). Cada alerta guardada pasa al tipo de su detector (con
         * los dos, al de las reglas: el Resumen la cuenta igual en los dos) y la severidad que la red había
         * elegido pasa a los dos tipos nuevos, también en un cambio de severidades pendiente de envío. Los
         * nombres van fijos, como en la migración de los motivos.
         */
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE alertas SET tipoAtaque = CASE WHEN veredictoUmbrales = 1 AND veredictoReglas = 0 " +
                        "THEN 'VOLUMEN_INUSUAL' ELSE 'SERVICIO_NO_AUTORIZADO' END WHERE tipoAtaque = 'SOLO_REGLAS_O_UMBRALES'",
                )
                for (tipo in listOf("SERVICIO_NO_AUTORIZADO", "VOLUMEN_INUSUAL")) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO severidades (redId, tipoAtaque, severidad) " +
                            "SELECT redId, ?, severidad FROM severidades WHERE tipoAtaque = 'SOLO_REGLAS_O_UMBRALES'",
                        arrayOf(tipo),
                    )
                }
                db.execSQL("DELETE FROM severidades WHERE tipoAtaque = 'SOLO_REGLAS_O_UMBRALES'")
                // Un cambio de severidades que no llegó a enviarse las guarda como texto ("SOLO_REGLAS_O_UMBRALES=MEDIA").
                for (severidad in listOf("ALTA", "MEDIA", "BAJA")) {
                    db.execSQL(
                        "UPDATE acciones_pendientes SET severidades = REPLACE(severidades, ?, ?) WHERE severidades IS NOT NULL",
                        arrayOf("SOLO_REGLAS_O_UMBRALES=$severidad", "SERVICIO_NO_AUTORIZADO=$severidad;VOLUMEN_INUSUAL=$severidad"),
                    )
                }
            }
        }

        /**
         * Versión 6 (1.0.5, correcciones de Peña): el perfil de este teléfono y su id en el servidor (los
         * pareados antes quedan como administradores, como en el servidor), la importancia de cada equipo,
         * la persona de cada revisión, el responsable de cada alerta y el motivo de los cambios de
         * configuración; y las tablas de personas, registro de cambios, asignaciones y asignaciones
         * pendientes. Las tablas nuevas van como las arma Room (decisiones 39 a 42).
         */
        val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE redes ADD COLUMN perfil TEXT NOT NULL DEFAULT 'ADMINISTRADOR'")
                db.execSQL("ALTER TABLE redes ADD COLUMN dispositivoId TEXT")
                db.execSQL("ALTER TABLE catalogo_equipos ADD COLUMN importancia TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE revisiones ADD COLUMN persona TEXT")
                for (columna in listOf("responsableId TEXT", "responsable TEXT", "asignadaPorId TEXT", "asignadaPor TEXT", "asignadaEn INTEGER")) {
                    db.execSQL("ALTER TABLE alertas ADD COLUMN $columna")
                }
                for (columna in listOf("motivoCambio TEXT", "equipoIp TEXT", "importancia TEXT")) {
                    db.execSQL("ALTER TABLE acciones_pendientes ADD COLUMN $columna")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personas` (`redId` TEXT NOT NULL, `id` TEXT NOT NULL, `nombre` TEXT, `telefono` TEXT NOT NULL, " +
                        "`perfil` TEXT NOT NULL, `ultimoUso` INTEGER, `vos` INTEGER NOT NULL, `orden` INTEGER NOT NULL, PRIMARY KEY(`redId`, `id`), " +
                        "FOREIGN KEY(`redId`) REFERENCES `redes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `registro_cambios` (`redId` TEXT NOT NULL, `id` INTEGER NOT NULL, `fecha` INTEGER NOT NULL, " +
                        "`tipo` TEXT NOT NULL, `autorId` TEXT, `autor` TEXT NOT NULL, `objeto` TEXT NOT NULL, `nombre` TEXT, `antes` TEXT, " +
                        "`despues` TEXT, `motivo` TEXT, PRIMARY KEY(`redId`, `id`), " +
                        "FOREIGN KEY(`redId`) REFERENCES `redes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `asignaciones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alertaId` TEXT NOT NULL, " +
                        "`responsableId` TEXT, `responsable` TEXT, `porId` TEXT NOT NULL, `por` TEXT NOT NULL, `fecha` INTEGER NOT NULL, " +
                        "`origen` TEXT NOT NULL, FOREIGN KEY(`alertaId`) REFERENCES `alertas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asignaciones_alertaId` ON `asignaciones` (`alertaId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `asignaciones_pendientes` (`alertaId` TEXT NOT NULL, `redId` TEXT NOT NULL, `accionId` TEXT NOT NULL, " +
                        "`responsableId` TEXT, `creadaEn` INTEGER NOT NULL, `intentos` INTEGER NOT NULL, `estado` TEXT NOT NULL, `ultimoError` TEXT, " +
                        "PRIMARY KEY(`alertaId`), FOREIGN KEY(`redId`) REFERENCES `redes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`alertaId`) REFERENCES `alertas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asignaciones_pendientes_redId` ON `asignaciones_pendientes` (`redId`)")
            }
        }

        /**
         * Versión 7 (1.0.9, decisión 51): si cada persona es un navegador con el tablero web. Las guardadas
         * quedan como teléfonos hasta la sincronización siguiente, que trae la lista nueva.
         */
        val MIGRACION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE personas ADD COLUMN navegador INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Versión 8 (1.0.9, decisión 51): el permiso de bloquear de este teléfono y de cada persona, si son
         * analistas. Los administradores bloquean por su perfil; un analista pide aprobación hasta la
         * sincronización siguiente, que trae lo que dice el servidor.
         */
        val MIGRACION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE redes ADD COLUMN permisoDeBloquear INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE personas ADD COLUMN permisoDeBloquear INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6, MIGRACION_6_7, MIGRACION_7_8)
                .build()
        }
    }
}
