package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.model.EstadoRevision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeshacerRevisionTest {

    @Test
    fun `quita la accion pendiente y la alerta vuelve a sin revisar`() = runTest {
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1")))
        val acciones = FakeAccionesPendientesRepository()
        val revision = Fixtures.revision()
        alertas.aplicarRevisionLocal("red-1", "a-1", revision)
        acciones.encolar(Fixtures.accion(alertaId = "a-1", revision = revision))
        assertEquals(EstadoRevision.ATAQUE_REAL, alertas.observarAlerta("red-1", "a-1").first()!!.estadoRevision)

        DeshacerRevision(alertas, acciones)("red-1", "a-1")

        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.SIN_REVISAR, alerta.estadoRevision)
        assertNull(alerta.revisionActual)
        assertTrue(acciones.todas.isEmpty())
    }

    @Test
    fun `si no habia accion pendiente no falla`() = runTest {
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1")))
        val acciones = FakeAccionesPendientesRepository()

        DeshacerRevision(alertas, acciones)("red-1", "a-1")

        assertEquals(EstadoRevision.SIN_REVISAR, alertas.observarAlerta("red-1", "a-1").first()!!.estadoRevision)
    }
}
