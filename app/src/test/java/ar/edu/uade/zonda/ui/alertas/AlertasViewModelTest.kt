package ar.edu.uade.zonda.ui.alertas

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlertasViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository()
    private val alertas = FakeAlertasRepository()
    private val conectividad = FakeConectividad()

    private fun viewModel() = AlertasViewModel(ObservarAlertas(redes, alertas), SincronizarAlertas(redes, alertas), redes, conectividad)

    private suspend fun ReceiveTurbine<AlertasUiState>.esperar(condicion: (AlertasUiState) -> Boolean): AlertasUiState {
        var estado = awaitItem()
        while (!condicion(estado)) estado = awaitItem()
        return estado
    }

    private fun conRedYAlertas() {
        redes.agregar(Fixtures.red("red-1"))
        alertas.agregar(
            Fixtures.alerta("a-1", severidad = Severidad.ALTA),
            Fixtures.alerta("a-2", severidad = Severidad.MEDIA, creadaEn = Fixtures.AHORA.minus(Duration.ofHours(2))),
            Fixtures.alerta("a-3", severidad = Severidad.BAJA, revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
        )
    }

    @Test
    fun `con una red pareada muestra la lista, el resumen y sincroniza al abrir`() = runTest {
        conRedYAlertas()

        viewModel().estado.test {
            val estado = esperar { it.contenido is ContenidoAlertas.Lista }

            val lista = estado.contenido as ContenidoAlertas.Lista
            assertEquals(listOf("a-1", "a-3", "a-2"), lista.alertas.map { it.id })
            assertEquals(2, lista.sinRevisar)
            assertEquals(Fixtures.AHORA, estado.datosDe)
            assertFalse(estado.sinConexion)
            assertEquals(FiltroAlertas.TODAS, estado.filtro)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `sin red pareada muestra SinRed y no intenta sincronizar`() = runTest {
        viewModel().estado.test {
            val estado = esperar { it.contenido !is ContenidoAlertas.Cargando }

            assertEquals(ContenidoAlertas.SinRed, estado.contenido)
            assertNull(estado.datosDe)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `cambiar el filtro actualiza la lista al instante y quitar los filtros la completa`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.contenido is ContenidoAlertas.Lista }

            viewModel.aplicarFiltro(FiltroAlertas(estado = EstadoRevision.ATAQUE_REAL))
            val filtrado = esperar { (it.contenido as? ContenidoAlertas.Lista)?.alertas?.size == 1 }
            assertEquals("a-3", (filtrado.contenido as ContenidoAlertas.Lista).alertas.single().id)
            assertTrue(filtrado.filtro.activo)

            viewModel.aplicarFiltro(FiltroAlertas(estado = EstadoRevision.FALSO_POSITIVO, periodo = Periodo.ULTIMAS_24_HORAS))
            val vacio = esperar { it.contenido is ContenidoAlertas.Vacio }
            assertEquals(ContenidoAlertas.Vacio(conFiltro = true), vacio.contenido)

            viewModel.quitarFiltros()
            val completo = esperar { (it.contenido as? ContenidoAlertas.Lista)?.alertas?.size == 3 }
            assertEquals(FiltroAlertas.TODAS, completo.filtro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin alertas guardadas y sin filtro el vacio no ofrece quitar filtros`() = runTest {
        redes.agregar(Fixtures.red("red-1"))

        viewModel().estado.test {
            val estado = esperar { it.contenido is ContenidoAlertas.Vacio }

            assertEquals(ContenidoAlertas.Vacio(conFiltro = false), estado.contenido)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la sincronizacion manual avisa las novedades y los errores, y el aviso se consume`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel() // la sincronización automática no trae nada: no avisa
        alertas.resultadoSincronizacion = ResultadoSincronizacion(nuevas = 2, modificadas = 0, altasNuevas = 1, accionesEnviadas = 1, accionesConError = 0, hora = Fixtures.AHORA)

        viewModel.estado.test {
            assertNull(esperar { it.contenido is ContenidoAlertas.Lista }.aviso)

            viewModel.sincronizar()
            val sincronizado = esperar { it.aviso != null && !it.sincronizando }
            assertEquals(AvisoAlertas.Sincronizado(nuevas = 2, altasNuevas = 1, accionesEnviadas = 1), sincronizado.aviso)
            assertFalse(sincronizado.sincronizando)

            viewModel.avisoMostrado()
            esperar { it.aviso == null }

            alertas.errorAlSincronizar = ErrorZonda.NoAutorizado()
            viewModel.sincronizar()
            val conError = esperar { it.aviso != null }
            assertTrue((conError.aviso as AvisoAlertas.Error).error is ErrorZonda.NoAutorizado)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin conexion lo indica, no sincroniza al abrir y la sincronizacion automatica no molesta con avisos`() = runTest {
        conRedYAlertas()
        conectividad.estado.value = false
        val viewModel = viewModel()

        viewModel.estado.test {
            val estado = esperar { it.contenido is ContenidoAlertas.Lista }
            assertTrue(estado.sinConexion)
            assertTrue(alertas.sincronizaciones.isEmpty())

            // Una sincronización automática que no trae nada no avisa; una manual sí.
            alertas.errorAlSincronizar = ErrorZonda.SinConexion()
            viewModel.sincronizar(manual = false)
            assertNull(viewModel.estado.value.aviso)
            viewModel.sincronizar(manual = true)
            assertTrue(esperar { it.aviso != null }.aviso is AvisoAlertas.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
