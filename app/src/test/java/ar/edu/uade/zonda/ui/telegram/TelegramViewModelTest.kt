package ar.edu.uade.zonda.ui.telegram

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.usecase.VincularTelegram
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
    private val preferencias = FakePreferenciasRepository(primerInicioHecho = true)
    private var ahora: Instant = Fixtures.AHORA

    private fun TestScope.viewModel(): TelegramViewModel {
        val viewModel = TelegramViewModel(redes, VincularTelegram(redes, preferencias), preferencias, "Google Pixel 7", reloj = { ahora })
        // El estado se arma mientras alguien lo observa, como hace la pantalla.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.estado.collect {} }
        return viewModel
    }

    @Test
    fun `al abrir pregunta el estado de Telegram y muestra la red, el bot y los avisos`() = runTest {
        preferencias.avisos.value = PreferenciasAvisos(baja = ModoAviso.SONIDO)

        val estado = viewModel().estado.value

        assertEquals(1, redes.consultasTelegram)
        assertEquals("zonda_alertas_bot", estado.red?.telegramBot)
        assertEquals(ModoAviso.SONIDO, estado.avisos.baja)
        assertEquals("Google Pixel 7", estado.nombreDelTelefono)
        assertFalse(estado.vinculado)
        assertFalse(estado.esperandoInicio)
    }

    @Test
    fun `abrir Telegram pide un codigo con los avisos y deja el enlace para abrir una sola vez`() = runTest {
        val viewModel = viewModel()

        viewModel.abrirTelegram()
        runCurrent()

        val estado = viewModel.estado.value
        assertEquals(redes.vinculoTelegram.enlaceBot, estado.enlaceParaAbrir)
        assertTrue(estado.esperandoInicio)
        assertEquals(PreferenciasAvisos(), redes.vinculosPedidos.single().second)
        viewModel.enlaceAbierto()
        assertNull(viewModel.estado.value.enlaceParaAbrir)
    }

    @Test
    fun `mientras espera pregunta cada tres segundos y cuando la persona toca Iniciar queda vinculado`() = runTest {
        val viewModel = viewModel()
        viewModel.abrirTelegram()
        runCurrent()
        val espera = launch { viewModel.esperarVinculo() }
        runCurrent()
        assertEquals(2, redes.consultasTelegram) // la de al abrir y la de enseguida

        advanceTimeBy(TelegramViewModel.CADA_CUANTO_PREGUNTAR + 1)
        assertEquals(3, redes.consultasTelegram)

        redes.telegramEnElServidor = true
        advanceTimeBy(TelegramViewModel.CADA_CUANTO_PREGUNTAR + 1)

        assertTrue(viewModel.estado.value.vinculado)
        assertFalse(viewModel.estado.value.esperandoInicio)
        assertTrue(espera.isCompleted)
    }

    @Test
    fun `cuando vence el codigo deja de preguntar`() = runTest {
        val viewModel = viewModel()
        viewModel.abrirTelegram()
        runCurrent()

        ahora = ahora.plus(Duration.ofMinutes(11))
        viewModel.esperarVinculo()

        assertFalse(viewModel.estado.value.esperandoInicio)
        assertEquals(1, redes.consultasTelegram)
    }

    @Test
    fun `sin el bot en el servidor abrir Telegram lo avisa y no abre nada`() = runTest {
        val viewModel = viewModel()
        redes.errorTelegram = ErrorZonda.TelegramNoDisponible()

        viewModel.abrirTelegram()
        runCurrent()

        val estado = viewModel.estado.value
        assertTrue((estado.aviso as AvisoTelegram.Error).error is ErrorZonda.TelegramNoDisponible)
        assertNull(estado.enlaceParaAbrir)
        assertFalse(estado.esperandoInicio)
        assertFalse(estado.ocupado)
        viewModel.avisoMostrado()
        assertNull(viewModel.estado.value.aviso)
    }

    @Test
    fun `el aviso de prueba sale y desvincular deja la red sin Telegram`() = runTest {
        redes.telegramEnElServidor = true
        val viewModel = viewModel()
        assertTrue(viewModel.estado.value.vinculado)

        viewModel.enviarPrueba()
        runCurrent()
        assertEquals(1, redes.pruebasEnviadas)
        assertEquals(AvisoTelegram.PruebaEnviada, viewModel.estado.value.aviso)

        viewModel.desvincular()
        runCurrent()
        assertEquals(1, redes.desvinculos)
        assertEquals(AvisoTelegram.Desvinculado, viewModel.estado.value.aviso)
        assertFalse(viewModel.estado.value.vinculado)
    }

    @Test
    fun `si el telefono no puede abrir el enlace lo avisa y deja de esperar`() = runTest {
        val viewModel = viewModel()
        viewModel.abrirTelegram()
        runCurrent()

        viewModel.noSePudoAbrir()

        assertEquals(AvisoTelegram.NoSeAbrio, viewModel.estado.value.aviso)
        assertFalse(viewModel.estado.value.esperandoInicio)
    }
}
