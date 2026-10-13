package ar.edu.uade.zonda.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.domain.Fixtures
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La migración de la base: un teléfono con la app de la fase 7 (versión 1) instala la nueva y no
 * pierde nada. Room valida el esquema migrado contra las entidades al abrir la base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracionesTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @After
    fun borrarArchivo() {
        contexto.deleteDatabase(ARCHIVO)
    }

    @Test
    fun `la migracion 1 a 2 suma las columnas del acceso y conserva las redes`() = runTest {
        val nueva = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO).allowMainThreadQueries().build()
        nueva.redesDao().insertar(Fixtures.red("red-1").aEntidad(creadaEn = Fixtures.AHORA))
        nueva.close()
        llevarAVersion1()

        val migrada = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO)
            .addMigrations(ZondaDatabase.MIGRACION_1_2)
            .allowMainThreadQueries()
            .build()
        val red = migrada.redesDao().obtener("red-1")?.aDominio()

        assertEquals("Oficina", red?.nombre)
        assertFalse(red!!.accesoVencido)
        assertNull(red.accesoEmitido)
        assertNull(red.accesoVence)

        val emitido = Instant.parse("2026-10-12T00:00:00Z")
        val vence = Instant.parse("2027-01-10T00:00:00Z")
        migrada.redesDao().actualizarDatosDePareo("red-1", red.nombre, red.urlBase, emitido.toEpochMilli(), vence.toEpochMilli())
        val renovada = migrada.redesDao().obtener("red-1")?.aDominio()
        assertEquals(emitido, renovada?.accesoEmitido)
        assertEquals(vence, renovada?.accesoVence)
        migrada.close()
    }

    /** Deja el archivo como lo dejaba la fase 7: la tabla de redes sin las columnas de la versión 2. */
    private fun llevarAVersion1() {
        val db = SQLiteDatabase.openDatabase(contexto.getDatabasePath(ARCHIVO).path, null, SQLiteDatabase.OPEN_READWRITE)
        val creacion = db.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'redes'", null).use {
            it.moveToFirst()
            it.getString(0)
        }
        val columnas = db.rawQuery("PRAGMA table_info(redes)", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }.filterNot { it in COLUMNAS_VERSION_2 }
        val creacionVersion1 = COLUMNAS_VERSION_2.fold(creacion) { sql, columna -> sql.replace(Regex(",\\s*`$columna`[^,]*"), "") }
        val lista = columnas.joinToString { "`$it`" }

        db.beginTransaction()
        try {
            db.execSQL(creacionVersion1.replace("`redes`", "`redes_v1`"))
            db.execSQL("INSERT INTO redes_v1 ($lista) SELECT $lista FROM redes")
            db.execSQL("DROP TABLE redes")
            db.execSQL("ALTER TABLE redes_v1 RENAME TO redes")
            db.version = 1
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    private companion object {
        const val ARCHIVO = "migracion.db"
        val COLUMNAS_VERSION_2 = listOf("accesoVencido", "accesoEmitidoEn", "accesoVenceEn")
    }
}
