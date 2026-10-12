package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.TipoAccion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisarAlertaTest {

    private val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1")))
    private val acciones = FakeAccionesPendientesRepository()
    private val casoDeUso = RevisarAlerta(alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA })

    @Test
    fun `una revision valida deja la alerta revisada y una accion pendiente`() = runTest {
        val accion = casoDeUso("red-1", "a-1", ResultadoRevision.ATAQUE_REAL, Motivo.ESCALADO_AL_PROVEEDOR, "  Escalado al proveedor de seguridad  ")

        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.ATAQUE_REAL, alerta.estadoRevision)
        assertEquals("Escalado al proveedor de seguridad", alerta.revisionActual?.comentario)
        assertEquals(OrigenRevision.ESTE_TELEFONO, alerta.revisionActual?.origen)
        assertEquals(Fixtures.AHORA, alerta.revisionActual?.fecha)
        assertEquals(EstadoEnvio.PENDIENTE, alerta.envio)

        assertEquals("accion-1", accion.id)
        assertEquals(TipoAccion.REVISAR_ALERTA, accion.tipo)
        assertEquals(1, accion.versionVista)
        assertEquals(EstadoEnvio.PENDIENTE, accion.estado)
        assertEquals(listOf(accion), acciones.todas)
    }

    @Test
    fun `sin comentario no se puede confirmar y nada cambia`() = runTest {
        val error = runCatching {
            casoDeUso("red-1", "a-1", ResultadoRevision.FALSO_POSITIVO, Motivo.TAREA_PROGRAMADA, "   ")
        }.exceptionOrNull()

        assertTrue(error is ErrorZonda.Validacion && error.campo == "comentario")
        assertEquals(EstadoRevision.SIN_REVISAR, alertas.observarAlerta("red-1", "a-1").first()!!.estadoRevision)
        assertTrue(acciones.todas.isEmpty())
    }

    @Test
    fun `el motivo tiene que corresponder al resultado`() = runTest {
        val error = runCatching {
            casoDeUso("red-1", "a-1", ResultadoRevision.ATAQUE_REAL, Motivo.TAREA_PROGRAMADA, "Comentario")
        }.exceptionOrNull()

        assertTrue(error is ErrorZonda.Validacion && error.campo == "motivo")
        assertTrue(acciones.todas.isEmpty())
    }

    @Test
    fun `una alerta inexistente da NoEncontrado`() = runTest {
        val error = runCatching {
            casoDeUso("red-1", "no-existe", ResultadoRevision.ATAQUE_REAL, Motivo.EN_INVESTIGACION, "Comentario")
        }.exceptionOrNull()

        assertTrue(error is ErrorZonda.NoEncontrado)
    }

    @Test
    fun `revisar dos veces la misma alerta deja una sola accion en la cola`() = runTest {
        casoDeUso("red-1", "a-1", ResultadoRevision.ATAQUE_REAL, Motivo.EN_INVESTIGACION, "Primera")
        val segunda = casoDeUso("red-1", "a-1", ResultadoRevision.FALSO_POSITIVO, Motivo.SERVICIO_NUEVO, "Cambio de opinión")

        assertEquals("accion-2", segunda.id)
        assertEquals(listOf(segunda), acciones.todas)
        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.FALSO_POSITIVO, alerta.estadoRevision)
        assertEquals(2, alerta.historial.size)
    }
}
