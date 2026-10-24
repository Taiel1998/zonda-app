package ar.edu.uade.zonda.domain.usecase

import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.EstadoSistema
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tarjeta "Estado del sistema": cada cuánto pregunta y qué conserva si el servidor deja de contestar. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObservarEstadoSistemaTest {

    private val redes = FakeRedesRepository()

    private fun funcionando(corrida: Instant? = Fixtures.AHORA.minusSeconds(5)) = EstadoSistema(
        sensor = EstadoPieza.FUNCIONA,
        analisis = EstadoPieza.FUNCIONA,
        servidor = EstadoPieza.FUNCIONA,
        ultimaCorrida = corrida,
        ultimoFlujo = null,
        consultadoEn = Fixtures.AHORA,
    )

    @Test
    fun `sin red no hay tarjeta y no se pregunta nada`() = runTest {
        ObservarEstadoSistema(redes)().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(redes.consultasDelSistema.isEmpty())
    }

    @Test
    fun `pregunta enseguida y despues cada 20 segundos, lo que tarda una vuelta del analisis`() = runTest {
        redes.agregar(Fixtures.red("red-1"))
        var vuelta = 0
        redes.estadoSistema = { funcionando(corrida = Fixtures.AHORA.minusSeconds((++vuelta).toLong())) }

        ObservarEstadoSistema(redes)().test {
            awaitItem()
            assertEquals(0L, currentTime)
            awaitItem()
            assertEquals(20_000L, currentTime)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("red-1", "red-1"), redes.consultasDelSistema)
    }

    @Test
    fun `si el servidor deja de contestar conserva la ultima corrida conocida para que siga contando`() = runTest {
        redes.agregar(Fixtures.red("red-1"))
        val corrida = Fixtures.AHORA.minusSeconds(8)
        var contesta = true
        redes.estadoSistema = { if (contesta) funcionando(corrida) else EstadoSistema.sinRespuesta(Fixtures.AHORA) }

        ObservarEstadoSistema(redes)().test {
            assertEquals(corrida, awaitItem()?.ultimaCorrida)
            contesta = false
            val sinRespuesta = awaitItem()!!
            assertEquals(EstadoPieza.NO_RESPONDE, sinRespuesta.servidor)
            assertEquals(EstadoPieza.SIN_DATOS, sinRespuesta.analisis)
            assertEquals(EstadoPieza.SIN_DATOS, sinRespuesta.sensor)
            assertEquals(corrida, sinRespuesta.ultimaCorrida)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una sincronizacion que cambia la hora de la red no reinicia la consulta`() = runTest {
        redes.agregar(Fixtures.red("red-1"))
        redes.estadoSistema = { funcionando() }

        ObservarEstadoSistema(redes)().test {
            awaitItem()
            redes.actualizar(Fixtures.red("red-1", ultimaSincronizacion = Fixtures.AHORA.plusSeconds(3)))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("red-1"), redes.consultasDelSistema)
    }
}
