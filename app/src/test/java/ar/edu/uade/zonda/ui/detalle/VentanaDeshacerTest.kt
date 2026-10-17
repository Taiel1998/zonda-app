package ar.edu.uade.zonda.ui.detalle

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La ventana de Deshacer de la app (corrección 7, decisión 38): sale sola, se deshace una sola vez y recuerda la hoja. */
@OptIn(ExperimentalCoroutinesApi::class)
class VentanaDeshacerTest {

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1"), Fixtures.red("red-2", activa = false)))
    private val alertas = FakeAlertasRepository()
    private val acciones = FakeAccionesPendientesRepository()
    private val conectividad = FakeConectividad()
    private val revisar = RevisarAlerta(alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA })

    private fun ventana(alcance: CoroutineScope) =
        VentanaDeshacer(DeshacerRevision(alertas, acciones), SincronizarAlertas(redes, alertas), conectividad, alcance)

    private val hoja = FormularioRevision(
        paso = PasoRevision.MOTIVO,
        resultado = ResultadoRevision.FALSO_POSITIVO,
        motivo = Motivo.TAREA_PROGRAMADA,
        comentario = "Era el respaldo de las 2.",
        comentarioEditado = true,
    )

    /** Guarda la revisión de [alertaId] en el teléfono, como al tocar "Confirmar revisión". */
    private suspend fun revisada(alertaId: String, redId: String = "red-1"): RevisionPorDeshacer {
        alertas.agregar(Fixtures.alerta(alertaId, redId = redId))
        revisar(redId, alertaId, ResultadoRevision.FALSO_POSITIVO, Motivo.TAREA_PROGRAMADA, "Era el respaldo de las 2.")
        return RevisionPorDeshacer(redId, alertaId, "pc-09 → srv-web", hoja, conConexion = true)
    }

    @Test
    fun `la revision sale a los cinco segundos aunque no haya ninguna pantalla abierta`() = runTest {
        val ventana = ventana(backgroundScope)
        val revision = revisada("a-1")

        ventana.abrir(revision)
        assertEquals(revision, ventana.abierta.value)
        advanceTimeBy(ESPERA - 1)
        assertTrue("todavía se puede deshacer", alertas.sincronizaciones.isEmpty())

        advanceTimeBy(2)
        assertNull(ventana.abierta.value)
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
        assertNull("ya no se puede deshacer", ventana.deshacer())
    }

    @Test
    fun `deshacer la descarta, no envia nada y guarda la hoja para el detalle de esa alerta`() = runTest {
        val ventana = ventana(backgroundScope)
        ventana.abrir(revisada("a-1"))
        advanceTimeBy(ESPERA - 1_000)

        val deshecha = ventana.deshacer()
        advanceTimeBy(ESPERA * 2)

        assertEquals("a-1", deshecha?.alertaId)
        assertNull(ventana.abierta.value)
        assertTrue(acciones.todas.isEmpty())
        assertEquals(EstadoRevision.SIN_REVISAR, alertas.observarAlerta("red-1", "a-1").first()!!.estadoRevision)
        assertTrue(alertas.sincronizaciones.isEmpty())
        assertNull(ventana.recuperarFormulario("a-2"))
        assertEquals(hoja, ventana.recuperarFormulario("a-1"))
        assertNull("se recupera una sola vez", ventana.recuperarFormulario("a-1"))
    }

    @Test
    fun `sin conexion no sale al terminar y queda en la cola hasta que vuelva la red`() = runTest {
        conectividad.estado.value = false
        val ventana = ventana(backgroundScope)
        ventana.abrir(revisada("a-1").copy(conConexion = false))

        advanceTimeBy(ESPERA + 1)

        assertNull(ventana.abierta.value)
        assertTrue(alertas.sincronizaciones.isEmpty())
        assertEquals(EstadoEnvio.PENDIENTE, acciones.todas.single().estado)
    }

    @Test
    fun `otra revision cierra la ventana de la anterior, que sale con ella`() = runTest {
        val ventana = ventana(backgroundScope)
        val primera = revisada("a-1")
        val segunda = revisada("a-2")

        ventana.abrir(primera)
        advanceTimeBy(ESPERA - 1_000)
        ventana.abrir(segunda)
        advanceTimeBy(2_000)
        assertTrue("la segunda todavía se puede deshacer", alertas.sincronizaciones.isEmpty())
        assertEquals(segunda, ventana.abierta.value)

        advanceTimeBy(ESPERA)
        // Una sola sincronización manda la cola entera: las dos revisiones.
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
        assertEquals(2, acciones.todas.size)
    }

    @Test
    fun `si la anterior era de otra red, sale enseguida`() = runTest {
        val ventana = ventana(backgroundScope)
        ventana.abrir(revisada("a-1", redId = "red-2"))
        ventana.abrir(revisada("a-2"))
        runCurrent()

        assertEquals(listOf("red-2"), alertas.sincronizaciones)
        advanceTimeBy(ESPERA + 1)
        assertEquals(listOf("red-2", "red-1"), alertas.sincronizaciones)
    }

    private companion object {
        const val ESPERA = VentanaDeshacer.ESPERA_DESHACER_MS
    }
}
