package ar.edu.uade.zonda.ui.ajustes

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.domain.usecase.BorrarDatosLocales
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.QuitarDispositivo
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1"), Fixtures.red("red-2", activa = false)))
    private val alertas = FakeAlertasRepository()
    private val acciones = FakeAccionesPendientesRepository()
    private val preferencias = FakePreferenciasRepository(primerInicioHecho = true)

    private fun TestScope.viewModel(): AjustesViewModel {
        val viewModel = AjustesViewModel(
            redes = redes,
            preferencias = preferencias,
            sincronizarAlertas = SincronizarAlertas(redes, alertas),
            cambiarSeveridades = CambiarSeveridades(redes, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
            quitarDispositivo = QuitarDispositivo(redes, alertas, acciones),
            borrarDatosLocales = BorrarDatosLocales(redes, alertas, acciones),
            alcanceDeEnvio = backgroundScope,
            nombreDelTelefono = "Google Pixel 7",
            versionDeLaApp = "0.1.0",
        )
        // El estado se arma mientras alguien lo observa, como hace la pantalla.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.estado.collect {} }
        return viewModel
    }

    @Test
    fun `muestra las redes, la activa, los avisos y las severidades de la red activa`() = runTest {
        redes.guardarSeveridadesLocal("red-1", SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA))

        val estado = viewModel().estado.value

        assertEquals(listOf("red-1", "red-2"), estado.redes.map { it.id })
        assertEquals("red-1", estado.redActiva?.id)
        assertEquals(ModoAviso.SONIDO, estado.avisos.alta)
        assertEquals(Severidad.ALTA, estado.severidades.severidadDe(TipoAtaque.ESCANEO))
        assertEquals("Google Pixel 7", estado.nombreDelTelefono)
        assertEquals("0.1.0", estado.versionDeLaApp)
        assertNull(estado.borradorSeveridades)
    }

    @Test
    fun `activar otra red la deja activa y cambiar un aviso se guarda al instante`() = runTest {
        val viewModel = viewModel()

        viewModel.activarRed("red-2")
        viewModel.cambiarAviso(Severidad.MEDIA, ModoAviso.SIN_AVISO)

        assertEquals("red-2", viewModel.estado.value.redActiva?.id)
        assertEquals(ModoAviso.SIN_AVISO, preferencias.avisos.value.media)
        assertEquals(ModoAviso.SIN_AVISO, viewModel.estado.value.avisos.media)
    }

    @Test
    fun `las severidades se editan en un borrador y guardar las encola para toda la red y sincroniza`() = runTest {
        val viewModel = viewModel()

        viewModel.empezarAEditarSeveridades()
        assertEquals(SeveridadPorTipo.RECOMENDADA, viewModel.estado.value.borradorSeveridades)
        viewModel.cambiarSeveridadDelBorrador(TipoAtaque.ESCANEO, Severidad.ALTA)
        assertEquals(Severidad.MEDIA, viewModel.estado.value.severidades.severidadDe(TipoAtaque.ESCANEO)) // todavía no se guardó

        viewModel.guardarSeveridades()
        runCurrent()

        val accion = acciones.todas.single()
        assertEquals(TipoAccion.CAMBIAR_SEVERIDADES, accion.tipo)
        assertEquals(Severidad.ALTA, accion.severidades?.severidadDe(TipoAtaque.ESCANEO))
        assertEquals(Severidad.ALTA, viewModel.estado.value.severidades.severidadDe(TipoAtaque.ESCANEO))
        assertNull(viewModel.estado.value.borradorSeveridades)
        assertEquals(AvisoAjustes.SeveridadesGuardadas, viewModel.estado.value.aviso)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)

        viewModel.empezarAEditarSeveridades()
        viewModel.cambiarSeveridadDelBorrador(TipoAtaque.DDOS, Severidad.BAJA)
        viewModel.descartarBorrador()
        assertNull(viewModel.estado.value.borradorSeveridades)
        assertEquals(1, acciones.todas.size)
    }

    @Test
    fun `sincronizar ahora avisa el resultado y los errores, y el aviso se consume`() = runTest {
        alertas.resultadoSincronizacion = ResultadoSincronizacion(nuevas = 3, modificadas = 0, altasNuevas = 1, accionesEnviadas = 2, accionesConError = 0, hora = Fixtures.AHORA)
        val viewModel = viewModel()

        viewModel.sincronizarAhora()
        assertEquals(AvisoAjustes.Sincronizado(nuevas = 3, accionesEnviadas = 2), viewModel.estado.value.aviso)
        viewModel.avisoMostrado()
        assertNull(viewModel.estado.value.aviso)

        alertas.errorAlSincronizar = ErrorZonda.NoAutorizado()
        viewModel.sincronizarAhora()
        assertTrue((viewModel.estado.value.aviso as AvisoAjustes.Error).error is ErrorZonda.NoAutorizado)
        assertEquals(false, viewModel.estado.value.sincronizando)
    }

    @Test
    fun `borrar datos locales vacia las alertas y conserva las redes`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val viewModel = viewModel()

        viewModel.borrarDatosLocales()

        assertTrue(alertas.todas.isEmpty())
        assertEquals(2, viewModel.estado.value.redes.size)
        assertEquals(AvisoAjustes.DatosBorrados, viewModel.estado.value.aviso)
    }

    @Test
    fun `quitar el dispositivo quita la red activa y, si queda otra, la activa`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val viewModel = viewModel()

        viewModel.quitarDispositivo()

        assertEquals(listOf("red-1"), redes.redesQuitadas)
        assertEquals("red-2", viewModel.estado.value.redActiva?.id)
        assertTrue(alertas.todas.isEmpty())
        assertEquals(AvisoAjustes.DispositivoQuitado(quedanRedes = true), viewModel.estado.value.aviso)

        viewModel.quitarDispositivo()

        assertTrue(viewModel.estado.value.redes.isEmpty())
        assertNull(viewModel.estado.value.redActiva)
        assertEquals(AvisoAjustes.DispositivoQuitado(quedanRedes = false), viewModel.estado.value.aviso)
    }
}
