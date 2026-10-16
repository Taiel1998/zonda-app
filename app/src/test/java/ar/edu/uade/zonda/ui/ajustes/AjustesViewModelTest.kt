package ar.edu.uade.zonda.ui.ajustes

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.domain.usecase.BorrarDatosLocales
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.EnviarAvisosTelegram
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
import org.junit.Assert.assertFalse
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
            alertas = alertas,
            preferencias = preferencias,
            sincronizarAlertas = SincronizarAlertas(redes, alertas),
            cambiarSeveridades = CambiarSeveridades(redes, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
            quitarDispositivo = QuitarDispositivo(redes, alertas, acciones),
            borrarDatosLocales = BorrarDatosLocales(redes, alertas, acciones),
            enviarAvisosTelegram = EnviarAvisosTelegram(redes),
            alcanceDeEnvio = backgroundScope,
            nombreDelTelefono = "Google Pixel 7",
        )
        // El estado se arma mientras alguien lo observa, como hace la pantalla.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.estado.collect {} }
        return viewModel
    }

    @Test
    fun `muestra cada red con sus alertas, la activa, los avisos y las severidades de la red activa`() = runTest {
        redes.guardarSeveridadesLocal("red-1", SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA))
        alertas.agregar(
            Fixtures.alerta("a-1"),
            Fixtures.alerta("a-2", severidad = Severidad.MEDIA),
            Fixtures.alerta("a-3", redId = "red-2", severidad = Severidad.MEDIA),
        )

        val estado = viewModel().estado.value

        assertEquals(listOf("red-1", "red-2"), estado.redes.map { it.red.id })
        assertEquals(1, estado.redes[0].contadores.altas)
        assertEquals(2, estado.redes[0].alertasGuardadas)
        assertEquals(1, estado.redes[1].contadores.medias)
        assertEquals("red-1", estado.redActiva?.id)
        assertEquals(2, estado.alertasDeLaRedActiva)
        assertEquals(3, estado.alertasGuardadas)
        assertEquals(ModoAviso.SONIDO, estado.avisos.alta)
        assertEquals(Severidad.ALTA, estado.severidades.severidadDe(TipoAtaque.ESCANEO))
        assertTrue(estado.notificacionesActivas)
        assertEquals("Google Pixel 7", estado.nombreDelTelefono)
        assertNull(estado.borradorSeveridades)
        assertNull(estado.borradorAvisos)
    }

    @Test
    fun `activar otra red la deja activa, lo avisa y sincroniza esa red`() = runTest {
        val viewModel = viewModel()

        viewModel.activarRed("red-2")
        runCurrent()

        assertEquals("red-2", viewModel.estado.value.redActiva?.id)
        assertEquals(AvisoAjustes.RedActivada("Oficina"), viewModel.estado.value.aviso)
        assertEquals(listOf("red-2"), alertas.sincronizaciones)
    }

    @Test
    fun `los avisos se editan en un borrador, Guardar los guarda y volver sin guardar los descarta`() = runTest {
        val viewModel = viewModel()

        viewModel.empezarAEditarAvisos()
        assertEquals(PreferenciasAvisos(), viewModel.estado.value.borradorAvisos)
        viewModel.cambiarAvisoDelBorrador(Severidad.MEDIA, ModoAviso.SIN_AVISO)
        assertEquals(ModoAviso.SIN_AVISO, viewModel.estado.value.borradorAvisos?.media)
        assertEquals(ModoAviso.SILENCIO, preferencias.avisos.value.media) // todavía no se guardó

        viewModel.guardarAvisos()
        runCurrent()
        assertEquals(ModoAviso.SIN_AVISO, preferencias.avisos.value.media)
        assertEquals(ModoAviso.SIN_AVISO, viewModel.estado.value.avisos.media)
        assertNull(viewModel.estado.value.borradorAvisos)
        // Le llegan al bot de cada red, para que avise como eligió la persona.
        assertEquals(listOf("red-1", "red-2"), redes.avisosEnviados.map { it.first })
        assertEquals(ModoAviso.SIN_AVISO, redes.avisosEnviados.first().second.media)

        viewModel.empezarAEditarAvisos()
        viewModel.cambiarAvisoDelBorrador(Severidad.ALTA, ModoAviso.SIN_AVISO)
        viewModel.descartarBorradorAvisos()
        assertEquals(ModoAviso.SONIDO, preferencias.avisos.value.alta)
        assertNull(viewModel.estado.value.borradorAvisos)
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
    fun `el interruptor de notificaciones se guarda`() = runTest {
        val viewModel = viewModel()

        viewModel.cambiarNotificaciones(false)
        assertFalse(preferencias.notificaciones.value)
        assertFalse(viewModel.estado.value.notificacionesActivas)
    }

    @Test
    fun `sincronizar ahora avisa cuantas alertas llegaron y los errores, y el aviso se consume`() = runTest {
        alertas.resultadoSincronizacion = ResultadoSincronizacion(nuevas = 3, modificadas = 0, altasNuevas = 1, accionesEnviadas = 2, accionesConError = 0, hora = Fixtures.AHORA)
        val viewModel = viewModel()

        viewModel.sincronizarAhora()
        assertEquals(AvisoAjustes.Sincronizado(nuevas = 3), viewModel.estado.value.aviso)
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
        assertEquals(0, viewModel.estado.value.alertasGuardadas)
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

    @Test
    fun `quitar el dispositivo de otra red desde Redes deja la activa como estaba`() = runTest {
        val viewModel = viewModel()

        viewModel.quitarDispositivo("red-2")

        assertEquals(listOf("red-2"), redes.redesQuitadas)
        assertEquals("red-1", viewModel.estado.value.redActiva?.id)
        assertEquals(listOf("red-1"), viewModel.estado.value.redes.map { it.red.id })
        assertEquals(AvisoAjustes.DispositivoQuitado(quedanRedes = true), viewModel.estado.value.aviso)
    }
}
