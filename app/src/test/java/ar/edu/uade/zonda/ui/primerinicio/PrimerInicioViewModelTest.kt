package ar.edu.uade.zonda.ui.primerinicio

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrimerInicioViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val preferencias = FakePreferenciasRepository()
    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
    private val alertas = FakeAlertasRepository()
    private val acciones = FakeAccionesPendientesRepository()

    private fun viewModel(alcance: CoroutineScope) = PrimerInicioViewModel(
        preferencias = preferencias,
        cambiarSeveridades = CambiarSeveridades(redes, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
        sincronizarAlertas = SincronizarAlertas(redes, alertas),
        alcanceDeEnvio = alcance,
    )

    @Test
    fun `los avisos se guardan al instante y la introduccion queda hecha`() = runTest {
        val viewModel = viewModel(backgroundScope)

        viewModel.cambiarAviso(Severidad.MEDIA, ModoAviso.SIN_AVISO)

        assertEquals(ModoAviso.SIN_AVISO, preferencias.avisos.value.media)
        assertEquals(ModoAviso.SONIDO, preferencias.avisos.value.alta)
        assertFalse(preferencias.primerInicio.value)
        viewModel.terminarIntroduccion()
        assertTrue(preferencias.primerInicio.value)
    }

    @Test
    fun `con la severidad recomendada no se manda nada a la red`() = runTest {
        val viewModel = viewModel(backgroundScope)
        viewModel.cambiarSeveridad(TipoAtaque.ESCANEO, Severidad.ALTA)
        viewModel.cambiarSeveridad(TipoAtaque.ESCANEO, Severidad.MEDIA) // vuelve a lo recomendado

        viewModel.alParear(Fixtures.red("red-1"))
        runCurrent()

        assertTrue(acciones.todas.isEmpty())
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `si la persona cambio la severidad, despues del pareo se guarda para la red y se envia`() = runTest {
        val viewModel = viewModel(backgroundScope)
        viewModel.cambiarSeveridad(TipoAtaque.ESCANEO, Severidad.ALTA)

        viewModel.alParear(Fixtures.red("red-1"))
        runCurrent()

        val accion = acciones.todas.single()
        assertEquals(TipoAccion.CAMBIAR_SEVERIDADES, accion.tipo)
        assertEquals(Severidad.ALTA, accion.severidades?.severidadDe(TipoAtaque.ESCANEO))
        assertEquals(Severidad.ALTA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.ESCANEO))
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }
}
