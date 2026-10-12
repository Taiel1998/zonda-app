package ar.edu.uade.zonda.ui.detalle

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetalleViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
    private val alertas = FakeAlertasRepository()
    private val conectividad = FakeConectividad()

    private fun viewModel(alertaId: String) = DetalleViewModel(ObtenerDetalleAlerta(alertas, conectividad), redes, conectividad, alertaId)

    private suspend fun ReceiveTurbine<DetalleUiState>.esperar(condicion: (DetalleUiState) -> Boolean): DetalleUiState {
        var estado = awaitItem()
        while (!condicion(estado)) estado = awaitItem()
        return estado
    }

    @Test
    fun `una alerta guardada se muestra y sigue los cambios de la base`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))

        viewModel("a-1").estado.test {
            val estado = esperar { it is DetalleUiState.Contenido } as DetalleUiState.Contenido

            assertEquals("a-1", estado.alerta.id)
            assertTrue(estado.alerta.sinRevisar)
            assertEquals(false, estado.sinConexion)

            alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision())
            val revisada = esperar { (it as? DetalleUiState.Contenido)?.alerta?.sinRevisar == false } as DetalleUiState.Contenido
            assertEquals(Fixtures.revision(), revisada.alerta.revisionActual)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una alerta que no esta en el telefono se descarga del servidor`() = runTest {
        alertas.alertasRemotas = mapOf("a-9" to Fixtures.alerta("a-9"))

        viewModel("a-9").estado.test {
            val estado = esperar { it is DetalleUiState.Contenido } as DetalleUiState.Contenido

            assertEquals("a-9", estado.alerta.id)
            assertEquals(listOf("a-9"), alertas.todas.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin conexion queda no disponible y se completa sola cuando vuelve la red`() = runTest {
        alertas.alertasRemotas = mapOf("a-9" to Fixtures.alerta("a-9"))
        conectividad.estado.value = false

        viewModel("a-9").estado.test {
            val sinRed = esperar { it is DetalleUiState.NoDisponible } as DetalleUiState.NoDisponible
            assertTrue(sinRed.sinConexion)
            assertTrue(sinRed.error is ErrorZonda.SinConexion)

            conectividad.estado.value = true
            val contenido = esperar { it is DetalleUiState.Contenido } as DetalleUiState.Contenido
            assertEquals("a-9", contenido.alerta.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `si el servidor no tiene la alerta se informa el error y reintentar vuelve a pedirla`() = runTest {
        val viewModel = viewModel("a-9")

        viewModel.estado.test {
            val noDisponible = esperar { it is DetalleUiState.NoDisponible } as DetalleUiState.NoDisponible
            assertTrue(noDisponible.error is ErrorZonda.NoEncontrado)
            assertEquals(false, noDisponible.sinConexion)

            alertas.alertasRemotas = mapOf("a-9" to Fixtures.alerta("a-9"))
            viewModel.reintentar()
            assertTrue(esperar { it is DetalleUiState.Contenido } is DetalleUiState.Contenido)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin red pareada el detalle lo dice`() = runTest {
        val sinRedes = FakeRedesRepository()

        DetalleViewModel(ObtenerDetalleAlerta(alertas, conectividad), sinRedes, conectividad, "a-1").estado.test {
            assertEquals(DetalleUiState.SinRed, esperar { it !is DetalleUiState.Cargando })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
