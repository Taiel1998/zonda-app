package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverEnvioRevisionTest {

    /** La revisión de la otra persona, que ya llegó del servidor (versión 2 de la alerta). */
    private val ajena = Fixtures.revision(resultado = ResultadoRevision.FALSO_POSITIVO, motivo = Motivo.TAREA_PROGRAMADA, origen = OrigenRevision.SERVIDOR)
    private val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", revision = ajena).copy(version = 2)))
    private val acciones = FakeAccionesPendientesRepository()
    private val resolver = ResolverEnvioRevision(alertas, acciones)

    /** Una revisión de este teléfono (ataque real) con su acción en el estado dado. */
    private suspend fun conRevisionPropia(estado: EstadoEnvio) {
        alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision())
        acciones.encolar(
            Fixtures.accion(alertaId = "a-1").copy(
                estado = estado,
                intentos = 3,
                ultimoError = "Error del servidor",
                revisionAjena = if (estado == EstadoEnvio.CONFLICTO) ajena else null,
            ),
        )
    }

    @Test
    fun `aplicar la mia igual reencola la misma accion con la version que ahora tiene la alerta`() = runTest {
        conRevisionPropia(EstadoEnvio.CONFLICTO)

        val reenviada = resolver.aplicarLaMiaIgual("red-1", "a-1")

        assertEquals("accion-1", reenviada.id)
        assertEquals(2, reenviada.versionVista)
        assertEquals(EstadoEnvio.PENDIENTE, reenviada.estado)
        assertEquals(0, reenviada.intentos)
        assertNull(reenviada.ultimoError)
        assertNull(reenviada.revisionAjena)
        assertEquals(listOf(reenviada), acciones.todas)
        assertEquals(EstadoRevision.ATAQUE_REAL, alertas.observarAlerta("red-1", "a-1").first()!!.estadoRevision)
    }

    @Test
    fun `quedarme con la otra descarta la propia y deja la revision del servidor`() = runTest {
        conRevisionPropia(EstadoEnvio.CONFLICTO)

        resolver.quedarmeConLaOtra("red-1", "a-1")

        assertTrue(acciones.todas.isEmpty())
        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.FALSO_POSITIVO, alerta.estadoRevision)
        assertEquals(ajena, alerta.revisionActual)
    }

    @Test
    fun `reintentar vuelve la accion a pendiente con los intentos en cero y la misma version vista`() = runTest {
        conRevisionPropia(EstadoEnvio.ERROR)

        val reintentada = resolver.reintentar("red-1", "a-1")

        assertEquals(EstadoEnvio.PENDIENTE, reintentada.estado)
        assertEquals(0, reintentada.intentos)
        assertNull(reintentada.ultimoError)
        assertEquals(1, reintentada.versionVista)
        assertEquals(listOf(reintentada), acciones.todas)
    }

    @Test
    fun `cada salida vale solo en su estado y sin accion no hay nada que resolver`() = runTest {
        assertTrue(runCatching { resolver.aplicarLaMiaIgual("red-1", "a-1") }.exceptionOrNull() is ErrorZonda.NoEncontrado)
        assertTrue(runCatching { resolver.reintentar("red-1", "a-1") }.exceptionOrNull() is ErrorZonda.NoEncontrado)

        conRevisionPropia(EstadoEnvio.PENDIENTE)

        assertTrue(runCatching { resolver.aplicarLaMiaIgual("red-1", "a-1") }.exceptionOrNull() is ErrorZonda.Validacion)
        assertTrue(runCatching { resolver.reintentar("red-1", "a-1") }.exceptionOrNull() is ErrorZonda.Validacion)
        assertEquals(EstadoEnvio.PENDIENTE, acciones.todas.single().estado)
    }
}
