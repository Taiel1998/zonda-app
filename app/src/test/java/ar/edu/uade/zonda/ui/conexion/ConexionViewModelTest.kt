package ar.edu.uade.zonda.ui.conexion

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.domain.usecase.ParearRed
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Conectada
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Conectando
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Escaneando
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Fallo
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Formulario
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConexionViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository()
    private val alertas = FakeAlertasRepository().apply {
        resultadoSincronizacion = ResultadoSincronizacion(nuevas = 28, modificadas = 0, altasNuevas = 4, accionesEnviadas = 0, accionesConError = 0, hora = Fixtures.AHORA)
    }

    /** El código del QR de ejemplo vence 7 minutos después de [Fixtures.AHORA]. */
    private val vence = Fixtures.AHORA.plus(Duration.ofMinutes(7))
    private val qr = "zonda:https://zonda.duckdns.org#K7M2-QX9P;vence=2026-10-12T12:07:00+00:00"

    private fun TestScope.viewModel(alcance: CoroutineScope = backgroundScope, ahora: Instant = Fixtures.AHORA) =
        ConexionViewModel(ParearRed(redes), SincronizarAlertas(redes, alertas), nombreDelTelefono = "Google Pixel 7", alcanceDeLaApp = alcance, reloj = { ahora })

    private fun ConexionViewModel.escribir(direccion: String, codigo: String) {
        cambiarDireccion(direccion)
        cambiarCodigo(codigo)
    }

    @Test
    fun `a mano normaliza los datos, verifica el servidor y parea sin descargar todavia`() = runTest {
        val viewModel = viewModel()
        viewModel.escribir("zonda-empresa.duckdns.org/", "k7m2qx9p")
        assertTrue((viewModel.estado.value as Formulario).puedeConectar)

        viewModel.conectarManual()

        val conectada = viewModel.estado.value as Conectada
        // "213 alertas de los últimos 30 días listas para descargar" y el nombre con el que quedó (Figma 35-692).
        assertEquals(213, conectada.alertasParaDescargar)
        assertEquals("Google Pixel 7", conectada.nombreDelTelefono)
        assertEquals(listOf("https://zonda-empresa.duckdns.org"), redes.verificaciones)
        assertEquals(listOf(DatosPareo("https://zonda-empresa.duckdns.org", "K7M2-QX9P")), redes.pareos)
        // La primera descarga pasa en Estado, después de "Continuar".
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `continuar arranca la primera descarga y devuelve la red para seguir`() = runTest {
        val viewModel = viewModel(alcance = this)
        viewModel.escribir("zonda.duckdns.org", "K7M2QX9P")
        viewModel.conectarManual()
        val red = (viewModel.estado.value as Conectada).red

        assertEquals(red, viewModel.continuar())
        advanceUntilIdle()

        assertEquals(listOf(red.id), alertas.sincronizaciones)
    }

    @Test
    fun `si la primera descarga falla, continuar sigue igual y la sincronizacion siguiente la retoma`() = runTest {
        alertas.errorAlSincronizar = ErrorZonda.SinConexion()
        val viewModel = viewModel(alcance = this)
        viewModel.escribir("zonda.duckdns.org", "K7M2QX9P")
        viewModel.conectarManual()

        val red = viewModel.continuar()
        advanceUntilIdle()

        assertEquals((viewModel.estado.value as Conectada).red, red)
    }

    @Test
    fun `el codigo se guarda como se escribe y no pasa de ocho letras y numeros`() = runTest {
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2")
        assertFalse((viewModel.estado.value as Formulario).puedeConectar)

        viewModel.conectarManual()
        assertTrue(redes.verificaciones.isEmpty())

        // Sin pasarlo a mayúsculas mientras se escribe: el teclado no pierde letras.
        viewModel.cambiarCodigo("k7m2-qx9p")
        assertEquals("k7m2-qx9p", (viewModel.estado.value as Formulario).codigo)
        assertTrue((viewModel.estado.value as Formulario).puedeConectar)
        viewModel.cambiarCodigo("k7m2-qx9p-z")
        assertEquals("k7m2-qx9p", (viewModel.estado.value as Formulario).codigo)
    }

    @Test
    fun `pegar el contenido del QR completa la direccion y el codigo, y pegar el codigo solo completa el codigo`() = runTest {
        val viewModel = viewModel()

        viewModel.pegar("K7M2-QX9P")
        assertEquals(Formulario(codigo = "K7M2QX9P"), viewModel.estado.value)

        viewModel.pegar(qr)
        assertEquals(Formulario(direccion = "https://zonda.duckdns.org", codigo = "K7M2QX9P"), viewModel.estado.value)

        viewModel.pegar(null)
        assertEquals(Formulario(direccion = "https://zonda.duckdns.org", codigo = "K7M2QX9P"), viewModel.estado.value)
    }

    @Test
    fun `con el QR conecta directo y un QR que no es de Zonda se avisa sin parear`() = runTest {
        val viewModel = viewModel()
        viewModel.leyoQr(qr) // la cámara no está abierta: se ignora
        assertTrue(redes.pareos.isEmpty())

        viewModel.abrirEscaner()
        viewModel.leyoQr("https://otra-cosa.com")
        assertEquals(Escaneando(qrNoValido = true), viewModel.estado.value)
        viewModel.leyoQr(qr)

        assertTrue(viewModel.estado.value is Conectada)
        assertEquals(listOf(DatosPareo("https://zonda.duckdns.org", "K7M2-QX9P", vence)), redes.pareos)
    }

    @Test
    fun `muestra cada paso mientras conecta`() = runTest {
        redes.esperaAlParear = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.abrirEscaner()

        viewModel.leyoQr(qr)
        assertEquals(Conectando("https://zonda.duckdns.org", PasoPareo.REGISTRANDO_TELEFONO), viewModel.estado.value)

        redes.esperaAlParear!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.estado.value is Conectada)
    }

    @Test
    fun `sin servidor el QR dice hasta cuando sirve el codigo, que no se gasto`() = runTest {
        redes.errorAlVerificar = ErrorZonda.SinConexion()
        val viewModel = viewModel()
        viewModel.abrirEscaner()

        viewModel.leyoQr(qr)

        // "El código sigue siendo válido hasta las HH:MM" (Figma 35-720).
        assertEquals(Fallo("https://zonda.duckdns.org", ErrorPareo.SIN_SERVIDOR, codigoValidoHasta = vence), viewModel.estado.value)
        assertTrue(redes.pareos.isEmpty())
    }

    @Test
    fun `si el codigo ya vencio o el error es otro, no se promete que el codigo sigue sirviendo`() = runTest {
        redes.errorAlVerificar = ErrorZonda.SinConexion()
        val tarde = viewModel(ahora = vence.plusSeconds(1))
        tarde.abrirEscaner()
        tarde.leyoQr(qr)
        assertNull((tarde.estado.value as Fallo).codigoValidoHasta)

        redes.errorAlVerificar = ErrorZonda.ServidorNoVerificado()
        val certificado = viewModel()
        certificado.abrirEscaner()
        certificado.leyoQr(qr)
        assertEquals(Fallo("https://zonda.duckdns.org", ErrorPareo.SERVIDOR_NO_VERIFICADO), certificado.estado.value)

        // A mano no hay hora de vencimiento.
        redes.errorAlVerificar = ErrorZonda.SinConexion()
        val aMano = viewModel()
        aMano.escribir("zonda.duckdns.org", "K7M2QX9P")
        aMano.conectarManual()
        assertNull((aMano.estado.value as Fallo).codigoValidoHasta)
    }

    @Test
    fun `con el codigo vencido, volver a mano conserva la direccion y pide otro codigo`() = runTest {
        redes.errorAlParear = ErrorZonda.CodigoVencido()
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2QX9P")

        viewModel.conectarManual()
        assertEquals(Fallo("https://zonda.duckdns.org", ErrorPareo.CODIGO_VENCIDO), viewModel.estado.value)
        viewModel.ingresarAMano()

        assertEquals(Formulario(direccion = "zonda.duckdns.org", codigo = ""), viewModel.estado.value)
    }

    @Test
    fun `sin servidor el codigo no se gasta y reintentar prueba de nuevo con los mismos datos`() = runTest {
        redes.errorAlVerificar = ErrorZonda.SinConexion()
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2QX9P")

        viewModel.conectarManual()
        assertEquals(ErrorPareo.SIN_SERVIDOR, (viewModel.estado.value as Fallo).error)
        assertTrue(redes.pareos.isEmpty())

        redes.errorAlVerificar = null
        viewModel.reintentar()
        assertTrue(viewModel.estado.value is Conectada)
    }

    @Test
    fun `corregir la direccion vuelve al formulario con los datos que se usaron`() = runTest {
        redes.errorAlVerificar = ErrorZonda.SinConexion()
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2QX9P")
        viewModel.conectarManual()

        viewModel.ingresarAMano()

        assertEquals(Formulario(direccion = "zonda.duckdns.org", codigo = "K7M2QX9P"), viewModel.estado.value)
    }

    @Test
    fun `cada error del pareo tiene su salida`() {
        assertEquals(ErrorPareo.CODIGO_VENCIDO, ErrorPareo.de(ErrorZonda.CodigoVencido()))
        assertEquals(ErrorPareo.CODIGO_INVALIDO, ErrorPareo.de(ErrorZonda.CodigoInvalido()))
        assertEquals(ErrorPareo.SIN_SERVIDOR, ErrorPareo.de(ErrorZonda.SinConexion()))
        assertEquals(ErrorPareo.SERVIDOR_NO_VERIFICADO, ErrorPareo.de(ErrorZonda.ServidorNoVerificado()))
        assertEquals(ErrorPareo.OTRO, ErrorPareo.de(ErrorZonda.ErrorDelServidor(500)))
        assertTrue(ErrorPareo.CODIGO_VENCIDO.esDelCodigo && ErrorPareo.CODIGO_INVALIDO.esDelCodigo)
        assertFalse(ErrorPareo.SIN_SERVIDOR.esDelCodigo)
    }
}
