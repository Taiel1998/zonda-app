package ar.edu.uade.zonda.ui.inicio

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import ar.edu.uade.zonda.ui.navigation.Rutas
import ar.edu.uade.zonda.ui.recorrido.PasoRecorrido
import ar.edu.uade.zonda.ui.recorrido.RecorridoViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La entrada de la app y el recorrido de once pasos (RF05). */
class ArranqueYRecorridoTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    @Test
    fun `la entrada depende de que haya red pareada y de que la introduccion se haya hecho`() {
        assertEquals(DestinoInicial.PRINCIPAL, DestinoInicial.para(hayRedPareada = true, primerInicioHecho = false))
        assertEquals(DestinoInicial.PRINCIPAL, DestinoInicial.para(hayRedPareada = true, primerInicioHecho = true))
        assertEquals(DestinoInicial.PRIMER_INICIO, DestinoInicial.para(hayRedPareada = false, primerInicioHecho = false))
        assertEquals(DestinoInicial.CONEXION, DestinoInicial.para(hayRedPareada = false, primerInicioHecho = true))
    }

    @Test
    fun `el arranque sigue a la base y a las preferencias`() = runTest {
        val redes = FakeRedesRepository()
        val preferencias = FakePreferenciasRepository()
        val viewModel = ArranqueViewModel(redes, preferencias)

        assertEquals(DestinoInicial.PRIMER_INICIO, viewModel.destinoInicial.value)
        preferencias.marcarPrimerInicioHecho()
        assertEquals(DestinoInicial.CONEXION, viewModel.destinoInicial.value)
        redes.agregar(Fixtures.red("red-1"))
        assertEquals(DestinoInicial.PRINCIPAL, viewModel.destinoInicial.value)
    }

    @Test
    fun `el recorrido no aparece sin red o antes de terminar la introduccion`() = runTest {
        val redes = FakeRedesRepository()
        val preferencias = FakePreferenciasRepository()
        val viewModel = RecorridoViewModel(preferencias, redes)

        assertNull(viewModel.paso.value)
        preferencias.marcarPrimerInicioHecho()
        assertNull(viewModel.paso.value)
        redes.agregar(Fixtures.red("red-1"))
        assertEquals(PasoRecorrido.SEMAFORO, viewModel.paso.value)
    }

    @Test
    fun `el recorrido muestra los once pasos sobre sus pestanias y al terminar no vuelve`() = runTest {
        val preferencias = FakePreferenciasRepository(primerInicioHecho = true)
        val viewModel = RecorridoViewModel(preferencias, FakeRedesRepository(listOf(Fixtures.red("red-1"))))
        val vistos = mutableListOf<PasoRecorrido>()

        repeat(PasoRecorrido.entries.size) {
            vistos += viewModel.paso.value!!
            viewModel.siguiente()
        }

        assertEquals(11, vistos.size)
        assertEquals(PasoRecorrido.entries, vistos)
        assertEquals(listOf(Rutas.ESTADO, Rutas.ALERTAS, Rutas.AJUSTES), vistos.map { it.ruta }.distinct())
        assertTrue(preferencias.recorrido.value)
        assertNull(viewModel.paso.value)
    }

    @Test
    fun `omitir el recorrido lo cierra para siempre`() = runTest {
        val preferencias = FakePreferenciasRepository(primerInicioHecho = true)
        val viewModel = RecorridoViewModel(preferencias, FakeRedesRepository(listOf(Fixtures.red("red-1"))))
        viewModel.siguiente()

        viewModel.terminar()

        assertTrue(preferencias.recorrido.value)
        assertNull(viewModel.paso.value)
    }
}
