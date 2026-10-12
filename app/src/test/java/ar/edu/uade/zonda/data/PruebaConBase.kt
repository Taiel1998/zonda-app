package ar.edu.uade.zonda.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.data.local.ZondaDatabase
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.seguridad.AlmacenSeguro
import ar.edu.uade.zonda.domain.Fixtures
import org.junit.After
import org.junit.Before

/**
 * Base para las pruebas que usan Room: abre la base en memoria, sin cifrar, sobre la JVM
 * (Robolectric). El cifrado con SQLCipher se prueba en el emulador (docs/decisiones.md, decisión 8).
 */
abstract class PruebaConBase {

    protected lateinit var base: ZondaDatabase

    @Before
    fun abrirBase() {
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZondaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun cerrarBase() {
        base.close()
    }

    protected suspend fun conRed(id: String = "red-1", activa: Boolean = true) {
        base.redesDao().insertar(Fixtures.red(id = id, activa = activa).aEntidad(creadaEn = Fixtures.AHORA))
    }
}

/** Almacén seguro en memoria para las pruebas; el real usa el Android Keystore. */
class FakeAlmacenSeguro : AlmacenSeguro {
    val valores = mutableMapOf<String, String>()

    override fun guardar(clave: String, valor: String) {
        valores[clave] = valor
    }

    override fun leer(clave: String): String? = valores[clave]

    override fun borrar(clave: String) {
        valores.remove(clave)
    }
}
