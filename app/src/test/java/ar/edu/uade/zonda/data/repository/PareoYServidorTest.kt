package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.VinculoTelegram
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que el repositorio de redes hace contra el servidor: pareo, quitar el dispositivo y el vínculo con Telegram. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PareoYServidorTest : PruebaConBase() {

    private val api = ApiFalsa()
    private val proveedor = ProveedorDeApiFalso(api)
    private val almacen = FakeAlmacenSeguro()
    private lateinit var redes: RedesRepositoryImpl

    private val datos = DatosPareo("http://10.0.2.2:8000", "K7M2-QX9P")

    @Before
    fun preparar() {
        redes = RedesRepositoryImpl(base.redesDao(), almacen, proveedor, reloj = { Fixtures.AHORA })
        api.codigosDePareo += "K7M2-QX9P"
    }

    @Test
    fun `parear verifica el servidor, canjea el codigo y deja la red activa con su token guardado`() = runTest {
        val red = redes.parear(DatosPareo("http://10.0.2.2:8000/", "K7M2-QX9P"), "Pixel de Martín")

        assertEquals("red-oficina", red.id)
        assertEquals("Oficina", red.nombre)
        assertEquals("http://10.0.2.2:8000", red.urlBase)
        assertTrue(red.activa)
        assertTrue(red.sinDatos)
        assertNull(red.cursor)
        assertEquals(red, redes.observarRedActiva().first())
        assertEquals("token-1", redes.tokenDe("red-oficina"))
        assertEquals(setOf("token_red-oficina"), almacen.valores.keys)
        assertEquals(setOf("K7M2-QX9P"), api.codigosUsados)
    }

    @Test
    fun `un codigo que no existe es CodigoInvalido y uno ya usado CodigoVencido`() = runTest {
        assertTrue(runCatching { redes.parear(DatosPareo("http://10.0.2.2:8000", "ZZZZ-ZZZZ"), "x") }.exceptionOrNull() is ErrorZonda.CodigoInvalido)

        redes.parear(datos, "x")

        assertTrue(runCatching { redes.parear(datos, "x") }.exceptionOrNull() is ErrorZonda.CodigoVencido)
    }

    @Test
    fun `sin servidor es SinConexion y con certificado invalido ServidorNoVerificado, sin mandar el codigo`() = runTest {
        api.errores["salud"] = IOException("apagado")
        assertTrue(runCatching { redes.parear(datos, "x") }.exceptionOrNull() is ErrorZonda.SinConexion)

        api.errores["salud"] = SSLHandshakeException("certificado desconocido")
        assertTrue(runCatching { redes.parear(datos, "x") }.exceptionOrNull() is ErrorZonda.ServidorNoVerificado)

        assertTrue(api.codigosUsados.isEmpty())
        assertNull(redes.observarRedActiva().first())
        assertTrue(almacen.valores.isEmpty())
    }

    @Test
    fun `volver a parear la misma red conserva sus alertas y su cursor y renueva el token`() = runTest {
        redes.parear(datos, "x")
        AlertasRepositoryImpl(base.alertasDao()).guardarDelServidor(listOf(Fixtures.alerta("a-1", redId = "red-oficina")))
        base.redesDao().actualizarCursor("red-oficina", "inst1:5")
        api.codigosDePareo += "AAAA-BBBB"

        val red = redes.parear(DatosPareo("http://192.168.0.10:8000", "AAAA-BBBB"), "x")

        assertEquals("token-2", redes.tokenDe("red-oficina"))
        assertEquals("http://192.168.0.10:8000", red.urlBase)
        assertEquals("inst1:5", red.cursor)
        assertTrue(red.activa)
        assertNotNull(base.alertasDao().obtener("a-1"))
        assertEquals(1, redes.observarRedes().first().size)
    }

    @Test
    fun `quitar el dispositivo revoca el token en el servidor y borra token y datos`() = runTest {
        redes.parear(datos, "x")

        redes.quitarDispositivo("red-oficina")

        assertTrue(api.tokenRevocado)
        assertNull(redes.observarRedActiva().first())
        assertTrue(almacen.valores.isEmpty())
        assertEquals(listOf("red-oficina"), proveedor.olvidadas)
    }

    @Test
    fun `quitar el dispositivo sin conexion borra igual lo local`() = runTest {
        redes.parear(datos, "x")
        api.errores["quitar"] = IOException("sin red")

        redes.quitarDispositivo("red-oficina")

        assertFalse(api.tokenRevocado)
        assertNull(redes.observarRedActiva().first())
        assertNull(redes.tokenDe("red-oficina"))
    }

    @Test
    fun `verificar el servidor no gasta el codigo y traduce los errores`() = runTest {
        redes.verificarServidor("http://10.0.2.2:8000")
        assertTrue(api.codigosUsados.isEmpty())

        api.errores["salud"] = IOException("apagado")
        assertTrue(runCatching { redes.verificarServidor("http://10.0.2.2:8000") }.exceptionOrNull() is ErrorZonda.SinConexion)

        api.errores["salud"] = SSLHandshakeException("certificado desconocido")
        assertTrue(runCatching { redes.verificarServidor("https://zonda.duckdns.org") }.exceptionOrNull() is ErrorZonda.ServidorNoVerificado)
    }

    @Test
    fun `pedir el vinculo de Telegram devuelve el codigo y el enlace al bot`() = runTest {
        redes.parear(datos, "x")

        val vinculo = redes.pedirVinculoTelegram("red-oficina")

        assertEquals(VinculoTelegram("ABCD-EFGH", "https://t.me/zonda_alertas_bot?start=ABCD-EFGH"), vinculo)
        assertTrue(runCatching { redes.pedirVinculoTelegram("otra-red") }.exceptionOrNull() is ErrorZonda.NoEncontrado)
    }
}
