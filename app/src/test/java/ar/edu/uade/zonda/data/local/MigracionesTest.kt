package ar.edu.uade.zonda.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.mapper.aEntidades
import ar.edu.uade.zonda.data.repository.AlertasRepositoryImpl
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import java.time.Instant
import kotlinx.coroutines.flow.first
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
 * Las migraciones de la base: un teléfono con una versión anterior de la app instala la nueva y no
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
        llevarAVersion2()
        llevarAVersion1()

        val migrada = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO)
            .addMigrations(*MIGRACIONES)
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

    @Test
    fun `la migracion 2 a 3 suma el protocolo y la hora de revision y pasa los motivos anteriores`() = runTest {
        val nueva = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO).allowMainThreadQueries().build()
        nueva.redesDao().insertar(Fixtures.red("red-1").aEntidad(creadaEn = Fixtures.AHORA))
        val alertas = AlertasRepositoryImpl(nueva.alertasDao(), reloj = { Fixtures.AHORA })
        alertas.guardarDelServidor(
            listOf(
                Fixtures.alerta("a-1", revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
                Fixtures.alerta("a-2"),
            ),
        )
        nueva.accionesPendientesDao().insertar(Fixtures.accion(alertaId = "a-2").aEntidad())
        nueva.close()
        llevarAVersion2()

        val migrada = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO)
            .addMigrations(*MIGRACIONES)
            .allowMainThreadQueries()
            .build()
        val revisada = AlertasRepositoryImpl(migrada.alertasDao(), reloj = { Fixtures.AHORA }).observarAlerta("red-1", "a-1").first()!!
        val accion = migrada.accionesPendientesDao().deAlerta("red-1", "a-2")!!.aDominio()

        assertEquals(Motivo.BLOQUEADO_MANUALMENTE, revisada.revisionActual?.motivo)
        assertNull(revisada.revisionActual?.revisadaEn)
        assertNull(revisada.protocolo)
        assertEquals(Motivo.INVESTIGANDO, accion.revision?.motivo)
        migrada.close()
    }

    @Test
    fun `la migracion 3 a 4 suma la hora del aviso de Telegram y el bot de la red`() = runTest {
        val nueva = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO).allowMainThreadQueries().build()
        nueva.redesDao().insertar(Fixtures.red("red-1").aEntidad(creadaEn = Fixtures.AHORA))
        AlertasRepositoryImpl(nueva.alertasDao(), reloj = { Fixtures.AHORA }).guardarDelServidor(listOf(Fixtures.alerta("a-1")))
        nueva.close()
        llevarAVersion3()

        val migrada = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO)
            .addMigrations(*MIGRACIONES)
            .allowMainThreadQueries()
            .build()
        val alerta = AlertasRepositoryImpl(migrada.alertasDao(), reloj = { Fixtures.AHORA }).observarAlerta("red-1", "a-1").first()!!
        assertNull(alerta.avisoTelegram)
        assertNull(migrada.redesDao().obtener("red-1")?.aDominio()?.telegramBot)

        migrada.redesDao().actualizarTelegram("red-1", vinculado = true, bot = "zonda_alertas_bot")
        val red = migrada.redesDao().obtener("red-1")?.aDominio()
        assertEquals("zonda_alertas_bot", red?.telegramBot)
        assertEquals(true, red?.telegramVinculado)
        migrada.close()
    }

    @Test
    fun `la migracion 4 a 5 separa solo reglas o umbrales en servicio no autorizado y volumen inusual`() = runTest {
        val nueva = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO).allowMainThreadQueries().build()
        nueva.redesDao().insertar(Fixtures.red("red-1").aEntidad(creadaEn = Fixtures.AHORA))
        AlertasRepositoryImpl(nueva.alertasDao(), reloj = { Fixtures.AHORA }).guardarDelServidor(
            listOf(
                Fixtures.alerta("reglas", tipoAtaque = TipoAtaque.SERVICIO_NO_AUTORIZADO, metodos = setOf(MetodoDeteccion.REGLAS)),
                Fixtures.alerta("umbrales", tipoAtaque = TipoAtaque.VOLUMEN_INUSUAL, metodos = setOf(MetodoDeteccion.UMBRALES)),
                Fixtures.alerta(
                    "las-dos", tipoAtaque = TipoAtaque.SERVICIO_NO_AUTORIZADO,
                    metodos = setOf(MetodoDeteccion.REGLAS, MetodoDeteccion.UMBRALES),
                ),
                Fixtures.alerta("ia", tipoAtaque = TipoAtaque.ESCANEO),
            ),
        )
        nueva.redesDao().guardarSeveridades("red-1", SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.SERVICIO_NO_AUTORIZADO, Severidad.ALTA).aEntidades("red-1"))
        nueva.accionesPendientesDao().insertar(Fixtures.accion(id = "severidades", alertaId = null, revision = null).aEntidad())
        nueva.close()
        llevarAVersion4()

        val migrada = Room.databaseBuilder(contexto, ZondaDatabase::class.java, ARCHIVO)
            .addMigrations(*MIGRACIONES)
            .allowMainThreadQueries()
            .build()
        val alertas = AlertasRepositoryImpl(migrada.alertasDao(), reloj = { Fixtures.AHORA })
        suspend fun tipoDe(id: String) = alertas.observarAlerta("red-1", id).first()!!

        assertEquals(TipoAtaque.SERVICIO_NO_AUTORIZADO, tipoDe("reglas").tipoAtaque)
        assertEquals(TipoAtaque.VOLUMEN_INUSUAL, tipoDe("umbrales").tipoAtaque)
        // La que vieron los dos queda como de las reglas y el Resumen la cuenta en los dos tipos.
        assertEquals(setOf(TipoAtaque.SERVICIO_NO_AUTORIZADO, TipoAtaque.VOLUMEN_INUSUAL), tipoDe("las-dos").tiposDeAtaque)
        assertEquals(TipoAtaque.ESCANEO, tipoDe("ia").tipoAtaque)
        // Lo que la red había elegido para "solo reglas o umbrales" pasa a los dos tipos nuevos.
        val severidades = migrada.redesDao().observarSeveridades("red-1").first().aDominio()
        assertEquals(Severidad.ALTA, severidades.severidadDe(TipoAtaque.SERVICIO_NO_AUTORIZADO))
        assertEquals(Severidad.ALTA, severidades.severidadDe(TipoAtaque.VOLUMEN_INUSUAL))
        assertEquals(TipoAtaque.entries.toSet(), severidades.valores.keys)
        // También en un cambio de severidades que todavía no se envió.
        val pendiente = migrada.accionesPendientesDao().listar("red-1").single().aDominio().severidades!!
        assertEquals(Severidad.BAJA, pendiente.severidadDe(TipoAtaque.SERVICIO_NO_AUTORIZADO))
        assertEquals(Severidad.BAJA, pendiente.severidadDe(TipoAtaque.VOLUMEN_INUSUAL))
        assertEquals(Severidad.ALTA, pendiente.severidadDe(TipoAtaque.FUERZA_BRUTA))
        migrada.close()
    }

    /**
     * Deja el archivo como lo dejaba la versión 4: con "solo reglas o umbrales" donde ahora van servicio
     * no autorizado y volumen inusual (cambio 9), y un cambio de severidades pendiente con ese nombre.
     */
    private fun llevarAVersion4() {
        val db = SQLiteDatabase.openDatabase(contexto.getDatabasePath(ARCHIVO).path, null, SQLiteDatabase.OPEN_READWRITE)
        db.beginTransaction()
        try {
            db.execSQL("UPDATE alertas SET tipoAtaque = 'SOLO_REGLAS_O_UMBRALES' WHERE tipoAtaque IN ('SERVICIO_NO_AUTORIZADO', 'VOLUMEN_INUSUAL')")
            db.execSQL("DELETE FROM severidades WHERE tipoAtaque = 'VOLUMEN_INUSUAL'")
            db.execSQL("UPDATE severidades SET tipoAtaque = 'SOLO_REGLAS_O_UMBRALES' WHERE tipoAtaque = 'SERVICIO_NO_AUTORIZADO'")
            db.execSQL("UPDATE acciones_pendientes SET severidades = 'FUERZA_BRUTA=ALTA;SOLO_REGLAS_O_UMBRALES=BAJA' WHERE alertaId IS NULL")
            db.version = 4
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    /** Deja el archivo como lo dejaba la versión 3: sin lo que suma Telegram (fase 10). */
    private fun llevarAVersion3() {
        llevarAVersion4()
        val db = SQLiteDatabase.openDatabase(contexto.getDatabasePath(ARCHIVO).path, null, SQLiteDatabase.OPEN_READWRITE)
        db.beginTransaction()
        try {
            quitarColumnas(db, "alertas", listOf("avisoTelegramEn"))
            quitarColumnas(db, "redes", listOf("telegramBot"))
            db.version = 3
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    /**
     * Deja el archivo como lo dejaba la versión 2: sin el protocolo ni las horas de revisión, y con los
     * motivos que tenía la app antes del Figma.
     */
    private fun llevarAVersion2() {
        llevarAVersion3()
        val db = SQLiteDatabase.openDatabase(contexto.getDatabasePath(ARCHIVO).path, null, SQLiteDatabase.OPEN_READWRITE)
        db.beginTransaction()
        try {
            quitarColumnas(db, "alertas", listOf("protocolo"))
            quitarColumnas(db, "revisiones", listOf("revisadaEn"))
            quitarColumnas(db, "acciones_pendientes", listOf("ajenaRevisadaEn"))
            db.execSQL("UPDATE revisiones SET motivo = 'EQUIPO_AISLADO'")
            db.execSQL("UPDATE acciones_pendientes SET motivo = 'EN_INVESTIGACION'")
            db.version = 2
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    /**
     * Rehace una tabla sin algunas columnas, con sus datos y sus índices. El SQLite de las pruebas no
     * tiene `ALTER TABLE ... DROP COLUMN`.
     */
    private fun quitarColumnas(db: SQLiteDatabase, tabla: String, quitar: List<String>) {
        val creacion = db.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tabla)).use {
            it.moveToFirst()
            it.getString(0)
        }
        val indices = db.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL", arrayOf(tabla)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val columnas = db.rawQuery("PRAGMA table_info($tabla)", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }.filterNot { it in quitar }
        val sinColumnas = quitar.fold(creacion) { sql, columna -> sql.replace(Regex(",\\s*`$columna`[^,)]*"), "") }
        val lista = columnas.joinToString { "`$it`" }
        // Una tabla ya rehecha queda con el nombre entre comillas dobles ("alertas"); la de Room, entre acentos.
        db.execSQL(sinColumnas.replaceFirst(Regex("CREATE TABLE [`\"]?$tabla[`\"]?"), "CREATE TABLE `${tabla}_anterior`"))
        db.execSQL("INSERT INTO ${tabla}_anterior ($lista) SELECT $lista FROM $tabla")
        db.execSQL("DROP TABLE $tabla")
        db.execSQL("ALTER TABLE ${tabla}_anterior RENAME TO $tabla")
        indices.forEach(db::execSQL)
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
            db.execSQL(creacionVersion1.replaceFirst(Regex("CREATE TABLE [`\"]?redes[`\"]?"), "CREATE TABLE `redes_v1`"))
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
        val MIGRACIONES = arrayOf(
            ZondaDatabase.MIGRACION_1_2,
            ZondaDatabase.MIGRACION_2_3,
            ZondaDatabase.MIGRACION_3_4,
            ZondaDatabase.MIGRACION_4_5,
        )
        val COLUMNAS_VERSION_2 = listOf("accesoVencido", "accesoEmitidoEn", "accesoVenceEn")
    }
}
