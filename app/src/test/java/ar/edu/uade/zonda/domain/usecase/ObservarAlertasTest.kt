package ar.edu.uade.zonda.domain.usecase

import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.Severidad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservarAlertasTest {

    @Test
    fun `emite las alertas de la red activa y no las de las otras redes`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red("red-1", activa = true), Fixtures.red("red-2", activa = false)))
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", redId = "red-1"), Fixtures.alerta("a-2", redId = "red-2")))

        ObservarAlertas(redes, alertas)().test {
            assertEquals(listOf("a-1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `al cambiar la red activa la lista cambia sola`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red("red-1", activa = true), Fixtures.red("red-2", activa = false)))
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", redId = "red-1"), Fixtures.alerta("a-2", redId = "red-2")))

        ObservarAlertas(redes, alertas)().test {
            assertEquals(listOf("a-1"), awaitItem().map { it.id })
            redes.activar("red-2")
            assertEquals(listOf("a-2"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin red pareada emite una lista vacia`() = runTest {
        ObservarAlertas(FakeRedesRepository(), FakeAlertasRepository(listOf(Fixtures.alerta())))().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el filtro de un contador deja solo las sin revisar de esa severidad`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red()))
        val alertas = FakeAlertasRepository(
            listOf(
                Fixtures.alerta("alta-sin-revisar", severidad = Severidad.ALTA),
                Fixtures.alerta("alta-revisada", severidad = Severidad.ALTA, revision = Fixtures.revision()),
                Fixtures.alerta("media-sin-revisar", severidad = Severidad.MEDIA),
            ),
        )

        ObservarAlertas(redes, alertas)(FiltroAlertas.sinRevisarDe(Severidad.ALTA)).test {
            val lista = awaitItem()
            assertEquals(listOf("alta-sin-revisar"), lista.map { it.id })
            assertTrue(lista.all { it.estadoRevision == EstadoRevision.SIN_REVISAR })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
