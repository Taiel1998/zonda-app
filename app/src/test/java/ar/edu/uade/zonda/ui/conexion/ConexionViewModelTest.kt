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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun viewModel() = ConexionViewModel(ParearRed(redes), SincronizarAlertas(redes, alertas), nombreDelTelefono = "Google Pixel 7")

    private fun ConexionViewModel.escribir(direccion: String, codigo: String) {
        cambiarDireccion(direccion)
        cambiarCodigo(codigo)
    }

    @Test
    fun `a mano normaliza los datos, verifica el servidor, parea y descarga las alertas`() = runTest {
        val viewModel = viewModel()
        viewModel.escribir("zonda-empresa.duckdns.org/", "k7m2qx9p")
        assertTrue((viewModel.estado.value as Formulario).puedeConectar)

        viewModel.conectarManual()

        val conectada = viewModel.estado.value as Conectada
        assertEquals(28, conectada.alertasDescargadas)
        assertEquals(listOf("https://zonda-empresa.duckdns.org"), redes.verificaciones)
        assertEquals(listOf(DatosPareo("https://zonda-empresa.duckdns.org", "K7M2-QX9P")), redes.pareos)
        assertEquals(listOf(conectada.red.id), alertas.sincronizaciones)
    }

    @Test
    fun `sin la direccion y un codigo completo no se puede conectar`() = runTest {
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2")
        assertFalse((viewModel.estado.value as Formulario).puedeConectar)

        viewModel.conectarManual()

        assertTrue(redes.verificaciones.isEmpty())
        viewModel.cambiarCodigo("K7M2-QX9P-DE-MAS")
        assertEquals("K7M2-QX9P", (viewModel.estado.value as Formulario).codigo)
    }

    @Test
    fun `con el QR conecta directo y un QR que no es de Zonda se avisa sin parear`() = runTest {
        val viewModel = viewModel()
        viewModel.leyoQr("zonda:https://zonda.duckdns.org#K7M2-QX9P") // la cámara no está abierta: se ignora
        assertTrue(redes.pareos.isEmpty())

        viewModel.abrirEscaner()
        viewModel.leyoQr("https://otra-cosa.com")
        assertEquals(Escaneando(qrNoValido = true), viewModel.estado.value)
        viewModel.leyoQr("zonda:https://zonda.duckdns.org#K7M2-QX9P")

        assertTrue(viewModel.estado.value is Conectada)
        assertEquals(listOf(DatosPareo("https://zonda.duckdns.org", "K7M2-QX9P")), redes.pareos)
    }

    @Test
    fun `muestra cada paso mientras conecta`() = runTest {
        redes.esperaAlParear = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.abrirEscaner()

        viewModel.leyoQr("zonda:https://zonda.duckdns.org#K7M2-QX9P")
        assertEquals(Conectando("https://zonda.duckdns.org", PasoPareo.REGISTRANDO_TELEFONO), viewModel.estado.value)

        redes.esperaAlParear!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.estado.value is Conectada)
    }

    @Test
    fun `con el codigo vencido, volver a mano conserva la direccion y pide otro codigo`() = runTest {
        redes.errorAlParear = ErrorZonda.CodigoVencido()
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2-QX9P")

        viewModel.conectarManual()
        assertEquals(Fallo("https://zonda.duckdns.org", ErrorPareo.CODIGO_VENCIDO), viewModel.estado.value)
        viewModel.ingresarAMano()

        assertEquals(Formulario(direccion = "zonda.duckdns.org", codigo = ""), viewModel.estado.value)
    }

    @Test
    fun `sin servidor el codigo no se gasta y reintentar prueba de nuevo con los mismos datos`() = runTest {
        redes.errorAlVerificar = ErrorZonda.SinConexion()
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2-QX9P")

        viewModel.conectarManual()
        assertEquals(ErrorPareo.SIN_SERVIDOR, (viewModel.estado.value as Fallo).error)
        assertTrue(redes.pareos.isEmpty())

        redes.errorAlVerificar = null
        viewModel.reintentar()
        assertTrue(viewModel.estado.value is Conectada)
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

    @Test
    fun `si la primera descarga falla el pareo queda hecho igual`() = runTest {
        alertas.errorAlSincronizar = ErrorZonda.SinConexion()
        val viewModel = viewModel()
        viewModel.escribir("zonda.duckdns.org", "K7M2-QX9P")

        viewModel.conectarManual()

        assertNull((viewModel.estado.value as Conectada).alertasDescargadas)
    }
}
