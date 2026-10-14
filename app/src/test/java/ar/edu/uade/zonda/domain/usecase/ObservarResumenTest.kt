package ar.edu.uade.zonda.domain.usecase

import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import java.time.Duration
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservarResumenTest {

    private fun observar(redes: FakeRedesRepository, alertas: FakeAlertasRepository) =
        ObservarResumen(redes, alertas, reloj = { Fixtures.AHORA }, zona = { ZoneOffset.UTC })

    @Test
    fun `calcula el resumen con las alertas de la red activa`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red("red-1", activa = true), Fixtures.red("red-2", activa = false)))
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", redId = "red-1"), Fixtures.alerta("a-2", redId = "red-2")))

        observar(redes, alertas)(PeriodoResumen.SIETE_DIAS).test {
            assertEquals(1, awaitItem()!!.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `se recalcula solo cuando llega una alerta o se revisa una`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red()))
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1")))

        observar(redes, alertas)(PeriodoResumen.SIETE_DIAS).test {
            assertEquals(1, awaitItem()!!.total)

            alertas.agregar(Fixtures.alerta("a-2"))
            assertEquals(2, awaitItem()!!.total)

            alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision())
            assertEquals(1, awaitItem()!!.revisadas)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `con un grupo todas las cifras salen de ese grupo`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red()))
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1", revision = Fixtures.revision()), Fixtures.alerta("a-2")))

        observar(redes, alertas)(PeriodoResumen.SIETE_DIAS, GrupoResumen.SinRevisar).test {
            val resumen = awaitItem()!!
            assertEquals(GrupoResumen.SinRevisar, resumen.grupo)
            assertEquals(1, resumen.total)
            assertEquals(2, resumen.totalDelPeriodo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `en 30 dias entran las alertas que en 7 dias quedan afuera`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red()))
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("de-hace-20-dias", creadaEn = Fixtures.AHORA.minus(Duration.ofDays(20)))))

        observar(redes, alertas)(PeriodoResumen.SIETE_DIAS).test {
            assertEquals(0, awaitItem()!!.total)
            cancelAndIgnoreRemainingEvents()
        }
        observar(redes, alertas)(PeriodoResumen.TREINTA_DIAS).test {
            assertEquals(1, awaitItem()!!.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin red pareada emite null`() = runTest {
        observar(FakeRedesRepository(), FakeAlertasRepository(listOf(Fixtures.alerta())))(PeriodoResumen.SIETE_DIAS).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
