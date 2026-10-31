package ar.edu.uade.zonda.ui.ajustes

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.usecase.CambiarImportancia
import ar.edu.uade.zonda.domain.usecase.GestionarPersonas
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

/** Personas (decisión 39) y Equipos (decisión 41) desde Ajustes. */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonasYEquiposViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
    private val alertas = FakeAlertasRepository()
    private val acciones = FakeAccionesPendientesRepository()

    private val taiel = Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, null, vos = true)
    private val laura = Persona("d-2", "Laura", "Teléfono de Laura", Perfil.ANALISTA, null, vos = false)
    private val meli = Persona("d-3", "Meli", "Galaxy A54", Perfil.ADMINISTRADOR, null, vos = false)

    private fun TestScope.personas(): PersonasViewModel {
        val viewModel = PersonasViewModel(redes, GestionarPersonas(redes))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.estado.collect {} }
        return viewModel
    }

    private fun TestScope.equipos(): EquiposViewModel {
        val viewModel = EquiposViewModel(
            redes = redes,
            cambiarImportancia = CambiarImportancia(redes, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
            sincronizarAlertas = SincronizarAlertas(redes, alertas),
            alcanceDeEnvio = backgroundScope,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.estado.collect {} }
        return viewModel
    }

    // ---- Personas ----

    @Test
    fun `la lista muestra a vos primero y despues a los administradores y a los analistas`() = runTest {
        redes.personas.value = listOf(laura, meli, taiel)

        val estado = personas().estado.value

        assertEquals(listOf("Taiel", "Meli", "Laura"), estado.personas.map { it.nombreVisible })
        assertTrue(estado.esAdministrador)
    }

    @Test
    fun `agregar persona pide el nombre y con el codigo abre la pantalla del QR`() = runTest {
        val viewModel = personas()

        viewModel.empezarAAgregar()
        assertEquals(Perfil.ANALISTA, viewModel.estado.value.formulario?.perfil)
        viewModel.generarCodigo()
        assertTrue(viewModel.estado.value.formulario!!.faltaNombre)
        assertTrue(redes.codigosPedidos.isEmpty())

        viewModel.cambiarNombre("Laura")
        viewModel.cambiarPerfilNuevo(Perfil.ADMINISTRADOR)
        viewModel.generarCodigo()
        runCurrent()

        assertEquals(listOf("Laura" to Perfil.ADMINISTRADOR), redes.codigosPedidos)
        assertNull(viewModel.estado.value.formulario)
        assertEquals("K7M2-QX9P", viewModel.estado.value.codigo?.codigo)
        assertTrue(viewModel.estado.value.abrirCodigo)
        viewModel.codigoAbierto()
        assertFalse(viewModel.estado.value.abrirCodigo)
    }

    @Test
    fun `sin conexion el codigo no sale y la hoja lo dice`() = runTest {
        val viewModel = personas()
        viewModel.empezarAAgregar()
        viewModel.cambiarNombre("Laura")
        redes.errorPersonas = ErrorZonda.SinConexion()

        viewModel.generarCodigo()
        runCurrent()

        val formulario = viewModel.estado.value.formulario!!
        assertTrue(formulario.error is ErrorZonda.SinConexion)
        assertFalse(formulario.generando)
        assertNull(viewModel.estado.value.codigo)
    }

    @Test
    fun `cambiar el perfil y quitar lo avisan y el ultimo administrador lo frena el servidor`() = runTest {
        redes.personas.value = listOf(taiel, laura, meli)
        val viewModel = personas()

        viewModel.cambiarPerfil(laura, Perfil.ADMINISTRADOR)
        runCurrent()
        assertEquals(AvisoPersonas.PerfilCambiado("Laura", Perfil.ADMINISTRADOR), viewModel.estado.value.aviso)
        viewModel.avisoMostrado()

        viewModel.quitar(meli)
        runCurrent()
        assertEquals(AvisoPersonas.Quitada("Meli"), viewModel.estado.value.aviso)
        assertEquals(listOf("d-3"), redes.personasQuitadas)

        redes.errorPersonas = ErrorZonda.UltimoAdministrador()
        viewModel.cambiarPerfil(laura, Perfil.ANALISTA)
        runCurrent()
        assertTrue((viewModel.estado.value.aviso as AvisoPersonas.Error).error is ErrorZonda.UltimoAdministrador)
    }

    @Test
    fun `darle o sacarle el permiso de bloquear a un analista lo avisa`() = runTest {
        redes.personas.value = listOf(taiel, laura)
        val viewModel = personas()

        viewModel.cambiarPermisoBloqueo(laura, true)
        runCurrent()

        assertEquals(AvisoPersonas.PermisoBloqueoCambiado("Laura", true), viewModel.estado.value.aviso)
        assertTrue(viewModel.estado.value.personas.first { it.id == "d-2" }.puedeBloquear)
        viewModel.avisoMostrado()
        viewModel.cambiarPermisoBloqueo(laura, false)
        runCurrent()
        assertEquals(listOf("d-2" to true, "d-2" to false), redes.permisosDeBloquear)
        assertFalse(viewModel.estado.value.personas.first { it.id == "d-2" }.puedeBloquear)
    }

    @Test
    fun `sin huella ni PIN lo que da poder no sale y lo avisa`() = runTest {
        redes.personas.value = listOf(taiel, laura)
        val viewModel = personas()

        // La pantalla no llama al ViewModel si el teléfono no puede confirmar: solo avisa.
        viewModel.sinBloqueoDePantalla()

        assertEquals(AvisoPersonas.SinBloqueoDePantalla, viewModel.estado.value.aviso)
        assertTrue(redes.perfilesCambiados.isEmpty() && redes.permisosDeBloquear.isEmpty() && redes.personasQuitadas.isEmpty())
    }

    @Test
    fun `la analista ve la lista sin poder tocar a nadie`() = runTest {
        redes.actualizar(Fixtures.red("red-1").copy(perfil = Perfil.ANALISTA, dispositivoId = "d-2"))
        redes.personas.value = listOf(taiel, laura.copy(vos = true))

        assertFalse(personas().estado.value.esAdministrador)
    }

    // ---- Equipos ----

    private val sueldos = EquipoDeLaRed("192.168.20.20", "srv-sueldos", Importancia.NORMAL)
    private val impresora = EquipoDeLaRed("192.168.30.80", "impresora-2", Importancia.NORMAL)
    private val archivos = EquipoDeLaRed("192.168.20.100", "srv-archivos", Importancia.NORMAL)

    @Test
    fun `los equipos se ordenan por direccion, por numero y no por texto`() = runTest {
        redes.equipos.value = listOf(impresora, archivos, sueldos)

        assertEquals(listOf("srv-sueldos", "srv-archivos", "impresora-2"), equipos().estado.value.equipos.map { it.nombre })
    }

    @Test
    fun `elegir otra importancia pide el motivo y guardar la encola y sincroniza`() = runTest {
        redes.equipos.value = listOf(sueldos, impresora)
        val viewModel = equipos()

        viewModel.elegir(sueldos)
        assertEquals("srv-sueldos", viewModel.estado.value.elegido?.nombre)
        viewModel.elegirImportancia(Importancia.NORMAL)
        assertNull(viewModel.estado.value.elegido)
        assertNull(viewModel.estado.value.cambio)

        viewModel.elegir(sueldos)
        viewModel.elegirImportancia(Importancia.CRITICO)
        assertNull(viewModel.estado.value.elegido)
        assertEquals(CambioDeImportancia(sueldos, Importancia.CRITICO), viewModel.estado.value.cambio)

        viewModel.guardar()
        assertTrue(viewModel.estado.value.faltaMotivo)
        assertTrue(acciones.todas.isEmpty())

        viewModel.cambiarMotivo("Ahí están los sueldos de toda la empresa.")
        viewModel.guardar()
        runCurrent()

        val accion = acciones.todas.single()
        assertEquals(TipoAccion.CAMBIAR_IMPORTANCIA, accion.tipo)
        assertEquals(Importancia.CRITICO, accion.importancia)
        assertEquals("Ahí están los sueldos de toda la empresa.", accion.motivo)
        assertEquals(Importancia.CRITICO, viewModel.estado.value.equipos.first { it.nombre == "srv-sueldos" }.importancia)
        assertEquals(AvisoEquipos.Guardado("srv-sueldos"), viewModel.estado.value.aviso)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `cancelar el motivo deja todo como estaba y la analista no abre la hoja`() = runTest {
        redes.equipos.value = listOf(sueldos)
        val viewModel = equipos()
        viewModel.elegir(sueldos)
        viewModel.elegirImportancia(Importancia.POCO_IMPORTANTE)

        viewModel.cancelarMotivo()

        assertNull(viewModel.estado.value.cambio)
        assertEquals(Importancia.NORMAL, redes.equipos.value.single().importancia)

        redes.actualizar(Fixtures.red("red-1").copy(perfil = Perfil.ANALISTA))
        viewModel.elegir(sueldos)
        assertNull(viewModel.estado.value.elegido)
    }
}
