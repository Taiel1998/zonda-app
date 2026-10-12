package ar.edu.uade.zonda.ui.detalle

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import ar.edu.uade.zonda.domain.usecase.ResolverEnvioRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class DetalleViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
    private val alertas = FakeAlertasRepository()
    private val acciones = FakeAccionesPendientesRepository()
    private val conectividad = FakeConectividad()

    /** [alcance] es el alcance de la app: en las pruebas, el de fondo del test, con tiempo virtual. */
    private fun viewModel(alertaId: String, alcance: CoroutineScope, redes: RedesRepository = this.redes) = DetalleViewModel(
        obtenerDetalle = ObtenerDetalleAlerta(alertas, conectividad),
        revisarAlerta = RevisarAlerta(alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
        deshacerRevision = DeshacerRevision(alertas, acciones),
        resolverEnvio = ResolverEnvioRevision(alertas, acciones),
        sincronizarAlertas = SincronizarAlertas(redes, alertas),
        redes = redes,
        acciones = acciones,
        conectividad = conectividad,
        alcanceDeEnvio = alcance,
        alertaId = alertaId,
    )

    private suspend fun ReceiveTurbine<DetalleUiState>.esperar(condicion: (DetalleUiState) -> Boolean): DetalleUiState {
        var estado = awaitItem()
        while (!condicion(estado)) estado = awaitItem()
        return estado
    }

    private val ajena = Fixtures.revision(resultado = ResultadoRevision.FALSO_POSITIVO, motivo = Motivo.TAREA_PROGRAMADA, origen = OrigenRevision.SERVIDOR)

    /** a-1 revisada por otra persona (versión 2) y por este teléfono, con la acción en [estado]. */
    private suspend fun conRevisionPropia(estado: EstadoEnvio) {
        alertas.agregar(Fixtures.alerta("a-1", revision = ajena).copy(version = 2))
        alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision())
        acciones.encolar(
            Fixtures.accion(alertaId = "a-1").copy(
                estado = estado,
                intentos = 5,
                ultimoError = "Error del servidor",
                revisionAjena = if (estado == EstadoEnvio.CONFLICTO) ajena else null,
            ),
        )
    }

    // ---- Lectura del detalle (fase 5) ----

    @Test
    fun `una alerta guardada se muestra y sigue los cambios de la base`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))

        viewModel("a-1", backgroundScope).estado.test {
            val estado = esperar { it is DetalleUiState.Contenido } as DetalleUiState.Contenido

            assertEquals("a-1", estado.alerta.id)
            assertTrue(estado.alerta.sinRevisar)
            assertNull(estado.accion)
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

        viewModel("a-9", backgroundScope).estado.test {
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

        viewModel("a-9", backgroundScope).estado.test {
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
        val viewModel = viewModel("a-9", backgroundScope)

        viewModel.estado.test {
            val noDisponible = esperar { it is DetalleUiState.NoDisponible } as DetalleUiState.NoDisponible
            assertTrue(noDisponible.error is ErrorZonda.NoEncontrado)
            assertEquals(false, noDisponible.sinConexion)

            alertas.alertasRemotas = mapOf("a-9" to Fixtures.alerta("a-9"))
            viewModel.reintentarDescarga()
            assertTrue(esperar { it is DetalleUiState.Contenido } is DetalleUiState.Contenido)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin red pareada el detalle lo dice`() = runTest {
        viewModel("a-1", backgroundScope, redes = FakeRedesRepository()).estado.test {
            assertEquals(DetalleUiState.SinRed, esperar { it !is DetalleUiState.Cargando })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Revisar en dos pasos, Deshacer y envío (fase 6) ----

    @Test
    fun `revisar en dos pasos guarda al instante, ofrece deshacer y envia a los cinco segundos`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val viewModel = viewModel("a-1", backgroundScope)

        viewModel.abrirRevision()
        assertEquals(PasoRevision.RESULTADO, viewModel.formulario.value?.paso)
        viewModel.elegirResultado(ResultadoRevision.ATAQUE_REAL)
        val paso2 = viewModel.formulario.value!!
        assertEquals(PasoRevision.MOTIVO, paso2.paso)
        assertEquals(Motivo.para(ResultadoRevision.ATAQUE_REAL), paso2.motivos)
        assertFalse(paso2.puedeConfirmar)
        viewModel.confirmarRevision() // sin motivo ni comentario no hace nada (criterio 2)
        assertTrue(acciones.todas.isEmpty())

        viewModel.elegirMotivo(Motivo.ESCALADO_AL_PROVEEDOR, "Escalé la alerta al proveedor.")
        assertEquals("Escalé la alerta al proveedor.", viewModel.formulario.value?.comentario)
        viewModel.cambiarComentario("Avisé al proveedor y bloqueé el acceso")
        viewModel.elegirMotivo(Motivo.EQUIPO_AISLADO, "Aislé el equipo.")
        assertEquals("Avisé al proveedor y bloqueé el acceso", viewModel.formulario.value?.comentario)
        assertTrue(viewModel.formulario.value!!.puedeConfirmar)

        viewModel.confirmarRevision()

        assertNull(viewModel.formulario.value)
        assertEquals(AvisoDetalle.RevisionGuardada(conConexion = true), viewModel.aviso.value)
        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.ATAQUE_REAL, alerta.estadoRevision)
        assertEquals(Motivo.EQUIPO_AISLADO, alerta.revisionActual?.motivo)
        assertEquals("Avisé al proveedor y bloqueé el acceso", alerta.revisionActual?.comentario)
        assertEquals(1, acciones.todas.size)
        assertTrue("todavía se puede deshacer", alertas.sincronizaciones.isEmpty())

        advanceTimeBy(ESPERA + 1)

        assertNull(viewModel.aviso.value)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `deshacer dentro de los cinco segundos descarta la revision y no envia nada`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val viewModel = viewModel("a-1", backgroundScope)
        viewModel.abrirRevision()
        viewModel.elegirResultado(ResultadoRevision.FALSO_POSITIVO)
        viewModel.elegirMotivo(Motivo.TAREA_PROGRAMADA, "Era el respaldo.")
        viewModel.confirmarRevision()

        advanceTimeBy(ESPERA - 1_000)
        viewModel.deshacer()
        advanceTimeBy(ESPERA * 2)

        assertNull(viewModel.aviso.value)
        assertTrue(acciones.todas.isEmpty())
        assertEquals(EstadoRevision.SIN_REVISAR, alertas.observarAlerta("red-1", "a-1").first()!!.estadoRevision)
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `sin conexion la revision queda pendiente y sale sola cuando vuelve la red`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        conectividad.estado.value = false
        val viewModel = viewModel("a-1", backgroundScope)
        viewModel.abrirRevision()
        viewModel.elegirResultado(ResultadoRevision.ATAQUE_REAL)
        viewModel.elegirMotivo(Motivo.EN_INVESTIGACION, "Lo estoy investigando.")

        viewModel.confirmarRevision()
        assertEquals(AvisoDetalle.RevisionGuardada(conConexion = false), viewModel.aviso.value)
        advanceTimeBy(ESPERA + 1)
        assertTrue(alertas.sincronizaciones.isEmpty())
        assertEquals(EstadoEnvio.PENDIENTE, acciones.todas.single().estado)

        conectividad.estado.value = true
        runCurrent()

        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `cambiar el resultado borra el motivo y el comentario propuesto, pero no lo que escribio la persona`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val viewModel = viewModel("a-1", backgroundScope)
        viewModel.abrirRevision()
        viewModel.elegirResultado(ResultadoRevision.ATAQUE_REAL)
        viewModel.elegirMotivo(Motivo.ESCALADO_AL_PROVEEDOR, "Escalé la alerta al proveedor.")

        viewModel.volverAlResultado()
        assertEquals(PasoRevision.RESULTADO, viewModel.formulario.value?.paso)
        viewModel.elegirResultado(ResultadoRevision.FALSO_POSITIVO)
        assertNull(viewModel.formulario.value?.motivo)
        assertEquals("", viewModel.formulario.value?.comentario)

        viewModel.elegirMotivo(Motivo.TAREA_PROGRAMADA, "Era una tarea programada.")
        viewModel.cambiarComentario("Respaldo de las 2 de la mañana")
        viewModel.volverAlResultado()
        viewModel.elegirResultado(ResultadoRevision.ATAQUE_REAL)
        assertNull(viewModel.formulario.value?.motivo)
        assertEquals("Respaldo de las 2 de la mañana", viewModel.formulario.value?.comentario)

        viewModel.cancelarRevision()
        assertNull(viewModel.formulario.value)
        assertTrue(acciones.todas.isEmpty())
    }

    // ---- Conflicto y reintento (fase 6) ----

    @Test
    fun `en conflicto muestra la revision ajena y aplicar la mia igual la reenvia con la version que se vio`() = runTest {
        conRevisionPropia(EstadoEnvio.CONFLICTO)
        val viewModel = viewModel("a-1", backgroundScope)

        viewModel.estado.test {
            val enConflicto = esperar { (it as? DetalleUiState.Contenido)?.accion?.estado == EstadoEnvio.CONFLICTO } as DetalleUiState.Contenido
            assertEquals(ajena, enConflicto.accion?.revisionAjena)
            assertEquals(EstadoRevision.ATAQUE_REAL, enConflicto.alerta.estadoRevision)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.aplicarLaMiaIgual()
        runCurrent()

        val reenviada = acciones.todas.single()
        assertEquals(EstadoEnvio.PENDIENTE, reenviada.estado)
        assertEquals(2, reenviada.versionVista)
        assertNull(reenviada.revisionAjena)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `quedarme con la otra descarta la revision propia sin enviar nada`() = runTest {
        conRevisionPropia(EstadoEnvio.CONFLICTO)
        val viewModel = viewModel("a-1", backgroundScope)

        viewModel.quedarmeConLaOtra()
        runCurrent()

        assertTrue(acciones.todas.isEmpty())
        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.FALSO_POSITIVO, alerta.estadoRevision)
        assertEquals(ajena, alerta.revisionActual)
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `reintentar una revision que quedo en error la vuelve a la cola y la envia`() = runTest {
        conRevisionPropia(EstadoEnvio.ERROR)
        val viewModel = viewModel("a-1", backgroundScope)

        viewModel.reintentarEnvio()
        runCurrent()

        val accion = acciones.todas.single()
        assertEquals(EstadoEnvio.PENDIENTE, accion.estado)
        assertEquals(0, accion.intentos)
        assertNull(accion.ultimoError)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `si el envio falla por algo que hay que resolver se avisa y el aviso se consume`() = runTest {
        conRevisionPropia(EstadoEnvio.ERROR)
        alertas.errorAlSincronizar = ErrorZonda.NoAutorizado()
        val viewModel = viewModel("a-1", backgroundScope)

        viewModel.reintentarEnvio()
        runCurrent()

        assertTrue((viewModel.aviso.value as AvisoDetalle.Error).error is ErrorZonda.NoAutorizado)
        viewModel.avisoMostrado()
        assertNull(viewModel.aviso.value)
    }

    private companion object {
        const val ESPERA = DetalleViewModel.ESPERA_DESHACER_MS
    }
}
