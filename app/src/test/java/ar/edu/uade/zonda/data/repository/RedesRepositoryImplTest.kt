package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RedesRepositoryImplTest : PruebaConBase() {

    private val almacen = FakeAlmacenSeguro()
    private lateinit var redes: RedesRepositoryImpl

    @Before
    fun preparar() {
        // La API en memoria: estas pruebas miran la base; lo que habla con el servidor está en PareoYServidorTest.
        redes = RedesRepositoryImpl(base.redesDao(), almacen, ProveedorDeApiFalso(ApiFalsa()), reloj = { Fixtures.AHORA })
    }

    @Test
    fun `una red pareada queda activa y su token en el almacen seguro, no en la base`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "token-secreto-1")

        assertEquals("red-1", redes.observarRedActiva().first()?.id)
        assertEquals("token-secreto-1", redes.tokenDe("red-1"))
        assertEquals(mapOf("token_red-1" to "token-secreto-1"), almacen.valores)
    }

    @Test
    fun `parear una segunda red la deja activa y la primera deja de estarlo`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), "t1")
        redes.guardarRedPareada(Fixtures.red("red-2"), "t2")

        assertEquals("red-2", redes.observarRedActiva().first()?.id)
        assertEquals(listOf(false, true), redes.observarRedes().first().map { it.activa })

        redes.activar("red-1")

        assertEquals("red-1", redes.observarRedActiva().first()?.id)
    }

    @Test
    fun `activar una red que no existe no deja la app sin red activa`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), "t1")

        // El enlace de un aviso trae el id de su red; uno armado a mano puede traer cualquiera.
        redes.activar("red-que-no-existe")

        assertEquals("red-1", redes.observarRedActiva().first()?.id)
    }

    @Test
    fun `sin severidades guardadas rige la recomendada y la guardada manda despues`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), "t1")

        assertEquals(SeveridadPorTipo.RECOMENDADA, redes.observarSeveridades("red-1").first())

        redes.guardarSeveridadesLocal("red-1", SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.DDOS, Severidad.ALTA))

        assertEquals(Severidad.ALTA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.DDOS))
        assertEquals(Severidad.BAJA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.PROBABLE_FALSA_ALARMA))
    }

    @Test
    fun `quitar el dispositivo borra la red, su token y sus alertas`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), "t1")
        val alertas = AlertasRepositoryImpl(base.alertasDao())
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1")))

        redes.quitarDispositivo("red-1")

        assertNull(redes.observarRedActiva().first())
        assertNull(redes.tokenDe("red-1"))
        assertTrue(alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().isEmpty())
    }

    @Test
    fun `el catalogo se guarda y se observa por red`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), "t1")
        val catalogo = Catalogo(equipos = mapOf("192.168.10.50" to "srv-web", "192.168.10.3" to "dc-01"), servicios = listOf("http", "ssh"))

        redes.guardarCatalogo("red-1", catalogo)

        assertEquals(catalogo, redes.observarCatalogo("red-1").first())
        assertEquals(Catalogo.VACIO, redes.observarCatalogo("otra").first())
    }

    @Test
    fun `la sincronizacion actualiza el cursor y la hora de los datos`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1", ultimaSincronizacion = null), "t1")
        assertTrue(redes.observarRedActiva().first()!!.sinDatos)

        redes.actualizarSincronizacion("red-1", cursor = "c-42", hora = Fixtures.AHORA)

        val red = redes.observarRedActiva().first()!!
        assertEquals("c-42", red.cursor)
        assertEquals(Fixtures.AHORA, red.ultimaSincronizacion)
    }
}
