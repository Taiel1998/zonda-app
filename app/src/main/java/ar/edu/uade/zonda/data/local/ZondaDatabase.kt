package ar.edu.uade.zonda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false,
)
abstract class ZondaDatabase : RoomDatabase() {

    abstract fun alertasDao(): AlertasDao

    abstract fun accionesPendientesDao(): AccionesPendientesDao

    abstract fun redesDao(): RedesDao

    companion object {
        const val NOMBRE_ARCHIVO = "zonda.db"

        /** Abre (o crea) la base cifrada en el almacenamiento interno de la app. */
        fun cifrada(context: Context, clave: ByteArray): ZondaDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, ZondaDatabase::class.java, NOMBRE_ARCHIVO)
                .openHelperFactory(SupportOpenHelperFactory(clave))
                .build()
        }
    }
}
