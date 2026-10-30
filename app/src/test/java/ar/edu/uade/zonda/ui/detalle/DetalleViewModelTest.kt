package ar.edu.uade.zonda.ui.detalle

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.AsignarAlerta
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import ar.edu.uade.zonda.domain.usecase.ResolverEnvioRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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

    private var ventanaDeLaApp: VentanaDeshacer? = null

    /** La ventana de Deshacer es una para toda la app: la comparten los detalles de cada prueba. */
    private fun ventana(alcance: CoroutineScope): VentanaDeshacer = ventanaDeLaApp
        ?: VentanaDeshacer(DeshacerRevision(alertas, acciones), SincronizarAlertas(redes, alertas), conectividad, alcance).also { ventanaDeLaApp = it }

    /** [alcance] es el alcance de la app: en las pruebas, el de fondo del test, con tiempo virtual. */
    private fun viewModel(alertaId: String, alcance: CoroutineScope, redes: RedesRepository = this.redes) = DetalleViewModel(
        obtenerDetalle = ObtenerDetalleAlerta(alertas, conectividad),
        revisarAlerta = RevisarAlerta(alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
        asignarAlerta = AsignarAlerta(redes, alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
        ventana = ventana(alcance),
        resolverEnvio = ResolverEnvioRevision(alertas, acciones),
        sincronizarAlertas = SincronizarAlertas(redes, alertas),
        redes = redes,
        acciones = acciones,
        conectividad = conectividad,
        alcanceDeEnvio = alcance,
        alertaId = alertaId,
    )

    /** Revisa a-1 como falso positivo por una tarea programada y confirma. */
    private fun revisarYConfirmar(viewModel: DetalleViewModel) {
        viewModel.abrirRevision()
        viewModel.elegirResultado(ResultadoRevision.FALSO_POSITIVO)
        viewModel.elegirMotivo(Motivo.TAREA_PROGRAMADA, "Es la copia nocturna que corre todos los domingos.")
        viewModel.cambiarComentario("Era el respaldo de las 2.")
        viewModel.confirmarRevision()
    }

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

        viewModel.elegirMotivo(Motivo.ESCALADO_AL_PROVEEDOR, "Se avisó al proveedor de seguridad, que está revisando el servidor.")
        assertEquals("Se avisó al proveedor de seguridad, que está revisando el servidor.", viewModel.formulario.value?.comentario)
        viewModel.cambiarComentario("Se avisó al proveedor y se bloqueó el acceso")
        viewModel.elegirMotivo(Motivo.BLOQUEADO_MANUALMENTE, "Se bloqueó el acceso SSH de pc-09 y se avisó al proveedor.")
        assertEquals("Se avisó al proveedor y se bloqueó el acceso", viewModel.formulario.value?.comentario)
        assertTrue(viewModel.formulario.value!!.puedeConfirmar)

        assertFalse(viewModel.cerrar.value)
        viewModel.confirmarRevision()

        // Queda guardada al instante, el detalle se cierra y la lista dice "Revisaste pc-09 → srv-web." (corrección 7).
        assertNull(viewModel.formulario.value)
        assertTrue(viewModel.cerrar.value)
        val abierta = ventana(backgroundScope).abierta.value!!
        assertEquals("pc-09 → srv-web", abierta.alerta)
        assertEquals("a-1", abierta.alertaId)
        assertTrue(abierta.conConexion)
        assertEquals(Motivo.BLOQUEADO_MANUALMENTE, abierta.formulario.motivo)
        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.ATAQUE_REAL, alerta.estadoRevision)
        assertEquals(Motivo.BLOQUEADO_MANUALMENTE, alerta.revisionActual?.motivo)
        assertEquals("Se avisó al proveedor y se bloqueó el acceso", alerta.revisionActual?.comentario)
        assertEquals(1, acciones.todas.size)
        assertTrue("todavía se puede deshacer", alertas.sincronizaciones.isEmpty())

        // Sale a los cinco segundos aunque el detalle ya no esté.
        viewModel.viewModelScope.cancel()
        advanceTimeBy(ESPERA + 1)

        assertNull(ventana(backgroundScope).abierta.value)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `deshacer descarta la revision y el detalle vuelve a abrir la hoja en el paso 2 como estaba`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        alertas.agregar(Fixtures.alerta("a-2"))
        val detalle = viewModel("a-1", backgroundScope)
        revisarYConfirmar(detalle)
        runCurrent()
        val elegido = ventana(backgroundScope).abierta.value!!.formulario

        advanceTimeBy(ESPERA - 1_000)
        val deshecha = ventana(backgroundScope).deshacer()
        advanceTimeBy(ESPERA * 2)

        assertEquals("a-1", deshecha?.alertaId)
        assertTrue(acciones.todas.isEmpty())
        assertEquals(EstadoRevision.SIN_REVISAR, alertas.observarAlerta("red-1", "a-1").first()!!.estadoRevision)
        assertTrue(alertas.sincronizaciones.isEmpty())

        // Otra alerta no se lleva la hoja; el detalle de a-1, sí, una sola vez.
        assertNull(viewModel("a-2", backgroundScope).formulario.value)
        val otraVez = viewModel("a-1", backgroundScope).formulario.value!!
        assertEquals(PasoRevision.MOTIVO, otraVez.paso)
        assertEquals(ResultadoRevision.FALSO_POSITIVO, otraVez.resultado)
        assertEquals(Motivo.TAREA_PROGRAMADA, otraVez.motivo)
        assertEquals("Era el respaldo de las 2.", otraVez.comentario)
        assertEquals(elegido.copy(paso = PasoRevision.MOTIVO), otraVez)
        assertNull(viewModel("a-1", backgroundScope).formulario.value)
    }

    @Test
    fun `sin conexion la revision queda pendiente y sale sola cuando vuelve la red`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        conectividad.estado.value = false
        val viewModel = viewModel("a-1", backgroundScope)
        viewModel.abrirRevision()
        viewModel.elegirResultado(ResultadoRevision.ATAQUE_REAL)
        viewModel.elegirMotivo(Motivo.INVESTIGANDO, "Se están revisando los registros del servidor.")

        viewModel.confirmarRevision()
        assertFalse(ventana(backgroundScope).abierta.value!!.conConexion)
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

    // ---- Responsable y cálculo de la severidad (1.0.5) ----

    private val taiel = Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, null, vos = false)
    private val laura = Persona("d-2", "Laura", "Teléfono de Laura", Perfil.ANALISTA, null, vos = false)

    /** Este teléfono es el de Laura, analista. */
    private fun soyLaura() {
        redes.actualizar(Fixtures.red("red-1").copy(perfil = Perfil.ANALISTA, dispositivoId = "d-2"))
        redes.personas.value = listOf(taiel, laura.copy(vos = true))
    }

    @Test
    fun `el detalle trae la gente, el perfil, quien soy y como se calculo la severidad`() = runTest {
        soyLaura()
        redes.equipos.value = listOf(EquipoDeLaRed("192.168.10.50", "srv-web", Importancia.CRITICO))
        alertas.agregar(Fixtures.alerta("a-1", severidad = Severidad.MEDIA, tipoAtaque = TipoAtaque.ESCANEO))

        viewModel("a-1", backgroundScope).estado.test {
            val estado = esperar { (it as? DetalleUiState.Contenido)?.calculo?.conImportancia == true } as DetalleUiState.Contenido

            assertEquals(listOf("Laura", "Taiel"), estado.personas.map { it.nombreVisible })
            assertEquals(Perfil.ANALISTA, estado.perfil)
            assertEquals("d-2", estado.miId)
            assertEquals(Severidad.MEDIA, estado.calculo?.delTipo)
            assertEquals(Severidad.ALTA, estado.calculo?.final)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `me la asigno la deja a mi nombre al instante y la manda a la red`() = runTest {
        soyLaura()
        alertas.agregar(Fixtures.alerta("a-1"))
        val viewModel = viewModel("a-1", backgroundScope)

        viewModel.asignar(laura)
        runCurrent()

        assertEquals("d-2", alertas.todas.single().asignacion?.responsableId)
        assertEquals(mapOf("a-1" to "d-2"), acciones.asignaciones)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `la analista no puede asignarle la alerta a otra persona`() = runTest {
        soyLaura()
        alertas.agregar(Fixtures.alerta("a-1"))
        val viewModel = viewModel("a-1", backgroundScope)

        viewModel.asignar(taiel)
        runCurrent()

        assertTrue((viewModel.aviso.value as AvisoDetalle.Error).error is ErrorZonda.SinPermiso)
        assertNull(alertas.todas.single().asignacion)
        assertTrue(acciones.asignaciones.isEmpty())
    }

    private companion object {
        const val ESPERA = VentanaDeshacer.ESPERA_DESHACER_MS
    }
}
