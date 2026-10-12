package ar.edu.uade.zonda.domain.usecase

import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObtenerEstadoRedTest {

    @Test
    fun `sin red pareada emite null`() = runTest {
        ObtenerEstadoRed(FakeRedesRepository(), FakeAlertasRepository())().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una red que nunca se sincronizo da SIN_DATOS aunque no tenga alertas`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red(ultimaSincronizacion = null)))

        ObtenerEstadoRed(redes, FakeAlertasRepository())().test {
            val estado = awaitItem()!!
            assertEquals(Semaforo.SIN_DATOS, estado.semaforo)
            assertNull(estado.datosDe)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una alta sin revisar pone el semaforo en rojo aunque haya medias y bajas`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red()))
        val alertas = FakeAlertasRepository(
            listOf(
                Fixtures.alerta("a-1", severidad = Severidad.ALTA),
                Fixtures.alerta("a-2", severidad = Severidad.MEDIA),
                Fixtures.alerta("a-3", severidad = Severidad.BAJA),
            ),
        )

        ObtenerEstadoRed(redes, alertas)().test {
            val estado = awaitItem()!!
            assertEquals(Semaforo.ROJO, estado.semaforo)
            assertEquals(ContadoresSinRevisar(altas = 1, medias = 1, bajas = 1), estado.sinRevisar)
            assertEquals(Fixtures.AHORA, estado.datosDe)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin altas y con una media sin revisar es amarillo`() = runTest {
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", severidad = Severidad.MEDIA)))

        ObtenerEstadoRed(FakeRedesRepository(listOf(Fixtures.red())), alertas)().test {
            assertEquals(Semaforo.AMARILLO, awaitItem()!!.semaforo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `solo bajas sin revisar es verde porque las bajas no cambian el semaforo`() = runTest {
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", severidad = Severidad.BAJA)))

        ObtenerEstadoRed(FakeRedesRepository(listOf(Fixtures.red())), alertas)().test {
            assertEquals(Semaforo.VERDE, awaitItem()!!.semaforo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una alta ya revisada no cuenta`() = runTest {
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", severidad = Severidad.ALTA, revision = Fixtures.revision())))

        ObtenerEstadoRed(FakeRedesRepository(listOf(Fixtures.red())), alertas)().test {
            val estado = awaitItem()!!
            assertEquals(Semaforo.VERDE, estado.semaforo)
            assertEquals(0, estado.sinRevisar.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `al revisar la ultima alta el semaforo cambia solo`() = runTest {
        val alertas = FakeAlertasRepository(
            listOf(
                Fixtures.alerta("a-1", severidad = Severidad.ALTA),
                Fixtures.alerta("a-2", severidad = Severidad.MEDIA),
            ),
        )

        ObtenerEstadoRed(FakeRedesRepository(listOf(Fixtures.red())), alertas)().test {
            assertEquals(Semaforo.ROJO, awaitItem()!!.semaforo)
            alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision())
            assertEquals(Semaforo.AMARILLO, awaitItem()!!.semaforo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la regla del semaforo se puede probar sola`() {
        val red = Fixtures.red()

        assertEquals(Semaforo.SIN_DATOS, ObtenerEstadoRed.calcularSemaforo(red.copy(ultimaSincronizacion = null), ContadoresSinRevisar(5, 0, 0)))
        assertEquals(Semaforo.ROJO, ObtenerEstadoRed.calcularSemaforo(red, ContadoresSinRevisar(1, 0, 0)))
        assertEquals(Semaforo.AMARILLO, ObtenerEstadoRed.calcularSemaforo(red, ContadoresSinRevisar(0, 2, 9)))
        assertEquals(Semaforo.VERDE, ObtenerEstadoRed.calcularSemaforo(red, ContadoresSinRevisar(0, 0, 3)))
        assertEquals(Semaforo.VERDE, ObtenerEstadoRed.calcularSemaforo(red, ContadoresSinRevisar.CERO))
    }
}
