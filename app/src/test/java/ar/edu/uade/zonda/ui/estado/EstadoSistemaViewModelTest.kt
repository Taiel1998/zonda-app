package ar.edu.uade.zonda.ui.estado

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.ObservarEstadoSistema
import ar.edu.uade.zonda.domain.usecase.ObtenerEstadoRed
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La tarjeta "Estado del sistema" en el ViewModel de Estado: qué muestra y qué pasa si el servidor no contesta. */
class EstadoSistemaViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository()
    private val alertas = FakeAlertasRepository()
    private val conectividad = FakeConectividad()
    private val acciones = FakeAccionesPendientesRepository()

    private fun viewModel(conTarjeta: Boolean = true) = EstadoViewModel(
        obtenerEstadoRed = ObtenerEstadoRed(redes, alertas),
        observarAlertas = ObservarAlertas(redes, alertas),
        sincronizarAlertas = SincronizarAlertas(redes, alertas),
        redes = redes,
        alertas = alertas,
        conectividad = conectividad,
        acciones = acciones,
        observarEstadoSistema = if (conTarjeta) ObservarEstadoSistema(redes) else null,
    )

    private suspend fun ReceiveTurbine<EstadoUiState>.esperar(condicion: (EstadoUiState) -> Boolean): EstadoUiState {
        var estado = awaitItem()
        while (!condicion(estado)) estado = awaitItem()
        return estado
    }

    private val funcionando = EstadoSistema(
        sensor = EstadoPieza.FUNCIONA,
        analisis = EstadoPieza.FUNCIONA,
        servidor = EstadoPieza.FUNCIONA,
        ultimaCorrida = Fixtures.AHORA.minusSeconds(12),
        ultimoFlujo = null,
        consultadoEn = Fixtures.AHORA,
    )

    private fun conRed() {
        redes.agregar(Fixtures.red("red-1"))
        alertas.agregar(Fixtures.alerta("alta", severidad = Severidad.ALTA))
    }

    @Test
    fun `la tarjeta muestra lo que contesta el servidor`() = runTest {
        conRed()
        redes.estadoSistema = { funcionando }

        viewModel().estado.test {
            val estado = esperar { it.sistema != null && it.contenido is ContenidoEstado.Datos }
            assertEquals(funcionando, estado.sistema)
            assertFalse(estado.sinConexion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `si el servidor no contesta Estado pasa a sin conexion aunque el telefono tenga red`() = runTest {
        conRed()
        redes.estadoSistema = { EstadoSistema.sinRespuesta(Fixtures.AHORA) }

        viewModel().estado.test {
            val estado = esperar { it.sistema != null }
            assertEquals(EstadoPieza.NO_RESPONDE, estado.sistema?.servidor)
            assertTrue(estado.sinConexion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cuando el servidor vuelve a contestar sale de sin conexion y sincroniza solo`() = runTest {
        conRed()
        var contesta = false
        redes.estadoSistema = { if (contesta) funcionando else EstadoSistema.sinRespuesta(Fixtures.AHORA) }

        viewModel().estado.test {
            esperar { it.sinConexion }
            // La sincronización automática del arranque ya pasó; la de la vuelta del servidor es la siguiente.
            val antes = alertas.sincronizaciones.size
            contesta = true
            val estado = esperar { it.sistema?.servidor == EstadoPieza.FUNCIONA }
            assertFalse(estado.sinConexion)
            assertEquals(antes + 1, alertas.sincronizaciones.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin el caso de uso no hay tarjeta y nada cambia`() = runTest {
        conRed()

        viewModel(conTarjeta = false).estado.test {
            val estado = esperar { it.contenido is ContenidoEstado.Datos }
            assertNull(estado.sistema)
            assertFalse(estado.sinConexion)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(redes.consultasDelSistema.isEmpty())
    }
}
