package ar.edu.uade.zonda.ui.primerinicio

import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * El primer inicio guarda los avisos del teléfono. Desde la 1.0.5 no elige severidades: son de toda la
 * red y las cambia un administrador desde Ajustes (decisión 39).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrimerInicioViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val preferencias = FakePreferenciasRepository()

    @Test
    fun `los avisos se guardan al instante y la introduccion queda hecha`() = runTest {
        val viewModel = PrimerInicioViewModel(preferencias)

        viewModel.cambiarAviso(Severidad.MEDIA, ModoAviso.SIN_AVISO)

        assertEquals(ModoAviso.SIN_AVISO, preferencias.avisos.value.media)
        assertEquals(ModoAviso.SONIDO, preferencias.avisos.value.alta)
        assertFalse(preferencias.primerInicio.value)
        viewModel.terminarIntroduccion()
        assertTrue(preferencias.primerInicio.value)
    }
}
