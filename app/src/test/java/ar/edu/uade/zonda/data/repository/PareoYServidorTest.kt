package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.AvisosDto
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.data.remote.errorHttp
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.VinculoTelegram
import java.io.IOException
import java.time.Instant
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
        val pareada = redes.parear(DatosPareo("http://10.0.2.2:8000/", "K7M2-QX9P"), "Pixel de Martín")
        val red = pareada.red

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
        // Detalles técnicos muestra cuándo se emitió el acceso y cuándo vence (90 días).
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), red.accesoEmitido)
        assertEquals(Instant.parse("2027-01-10T00:00:00Z"), red.accesoVence)
        // "N alertas de los últimos 30 días listas para descargar" (Figma 35-692).
        assertEquals(api.salud().alertas, pareada.alertasParaDescargar)
    }

    @Test
    fun `los detalles de una conexion por http no traen TLS ni certificado y el vencimiento sale de lo guardado`() = runTest {
        val red = redes.parear(datos, "Pixel de Martín").red

        val detalles = redes.detallesDeConexion(red.id)

        assertNull(detalles.versionTls)
        assertNull(detalles.emisorCertificado)
        assertNull(detalles.certificadoVence)
        assertEquals(Instant.parse("2027-01-10T00:00:00Z"), detalles.accesoVence)
    }

    @Test
    fun `del emisor del certificado se muestra la organizacion, o el CN si no tiene`() {
        assertEquals("Let's Encrypt", RedesRepositoryImpl.organizacion("CN=R11,O=Let's Encrypt,C=US"))
        assertEquals("Ejemplo, Inc.", RedesRepositoryImpl.organizacion("CN=Ejemplo CA,O=Ejemplo\\, Inc.,C=AR"))
        assertEquals("zonda-local", RedesRepositoryImpl.organizacion("CN=zonda-local"))
        assertNull(RedesRepositoryImpl.organizacion(""))
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

        val red = redes.parear(DatosPareo("http://192.168.0.10:8000", "AAAA-BBBB"), "x").red

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
    fun `reiniciar la sincronizacion deja la red sin cursor ni hora pero pareada`() = runTest {
        redes.parear(datos, "x")
        redes.actualizarSincronizacion("red-oficina", "inst1:5", Fixtures.AHORA)

        redes.reiniciarSincronizacion("red-oficina")

        val red = redes.observarRedActiva().first()!!
        assertNull(red.cursor)
        assertNull(red.ultimaSincronizacion)
        assertEquals("token-1", redes.tokenDe("red-oficina"))
    }

    @Test
    fun `pedir el vinculo de Telegram manda los avisos y devuelve el codigo y el enlace al bot`() = runTest {
        redes.parear(datos, "x")
        val avisos = PreferenciasAvisos(media = ModoAviso.SIN_AVISO)

        val vinculo = redes.pedirVinculoTelegram("red-oficina", avisos)

        assertEquals(VinculoTelegram("ABCD-EFGH", "https://t.me/zonda_alertas_bot?start=ABCD-EFGH"), vinculo)
        assertEquals(AvisosDto("sonido", "sin_aviso", "sin_aviso"), api.vinculosPedidos.single().avisos)
        assertTrue(runCatching { redes.pedirVinculoTelegram("otra-red", avisos) }.exceptionOrNull() is ErrorZonda.NoEncontrado)
    }

    @Test
    fun `consultar Telegram deja la red vinculada y con el usuario del bot`() = runTest {
        redes.parear(datos, "x")
        api.telegramVinculado = true
        api.botTelegram = "zonda_prueba_bot"

        val estado = redes.consultarTelegram("red-oficina")

        assertTrue(estado.vinculado)
        assertEquals(PreferenciasAvisos(), estado.avisos)
        val red = redes.observarRedActiva().first()!!
        assertTrue(red.telegramVinculado)
        assertEquals("zonda_prueba_bot", red.telegramBot)
    }

    @Test
    fun `el aviso de prueba sale si hay chat y sin chat la red queda sin Telegram`() = runTest {
        redes.parear(datos, "x")
        api.telegramVinculado = true
        redes.consultarTelegram("red-oficina")

        redes.enviarAvisoDePrueba("red-oficina")
        assertEquals(1, api.pruebasEnviadas)

        // La persona bloqueó el bot: el servidor lo desvinculó.
        api.telegramVinculado = false
        val error = runCatching { redes.enviarAvisoDePrueba("red-oficina") }.exceptionOrNull()
        assertTrue(error is ErrorZonda.TelegramSinVincular)
        assertFalse(redes.observarRedActiva().first()!!.telegramVinculado)
    }

    @Test
    fun `desvincular Telegram y mandar los avisos al servidor`() = runTest {
        redes.parear(datos, "x")
        api.telegramVinculado = true
        redes.consultarTelegram("red-oficina")

        redes.desvincularTelegram("red-oficina")
        redes.enviarAvisosTelegram("red-oficina", PreferenciasAvisos(baja = ModoAviso.SONIDO))

        assertFalse(api.telegramVinculado)
        assertFalse(redes.observarRedActiva().first()!!.telegramVinculado)
        assertEquals("sonido", api.avisosTelegram.baja)
    }

    @Test
    fun `sin el bot en el servidor, vincular Telegram lo dice`() = runTest {
        redes.parear(datos, "x")
        api.errores["vinculo"] = errorHttp(503)

        val error = runCatching { redes.pedirVinculoTelegram("red-oficina", PreferenciasAvisos()) }.exceptionOrNull()

        assertTrue(error is ErrorZonda.TelegramNoDisponible)
    }
}
