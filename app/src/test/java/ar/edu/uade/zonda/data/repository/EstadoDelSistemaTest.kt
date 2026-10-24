package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.data.remote.SistemaDto
import ar.edu.uade.zonda.data.remote.errorHttp
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.EstadoPieza
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `GET /salud` con token (API 0.7.0) leído para la tarjeta "Estado del sistema". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EstadoDelSistemaTest : PruebaConBase() {

    private val api = ApiFalsa()
    private lateinit var redes: RedesRepositoryImpl

    @Before
    fun preparar() {
        redes = RedesRepositoryImpl(base.redesDao(), FakeAlmacenSeguro(), ProveedorDeApiFalso(api), reloj = { Fixtures.AHORA })
    }

    private fun sistema(
        analisis: String = "funciona",
        sensor: String = "funciona",
        segundos: Long? = 9,
        ultimoFlujo: String? = "2026-10-12T11:59:00+00:00",
    ) = SistemaDto(
        servidor = "funciona",
        analisis = analisis,
        sensor = sensor,
        ultimaCorrida = "2026-10-12T11:59:51+00:00",
        segundosDesdeUltimaCorrida = segundos,
        ultimoFlujo = ultimoFlujo,
        flujosUltimaCorrida = 12,
        limiteSegundos = 120,
    )

    @Test
    fun `con el latido al dia todo funciona y las horas quedan en el reloj del telefono`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.sistema = sistema()
        // El reloj del servidor va 30 s atrasado respecto del teléfono (Fixtures.AHORA es 12:00:00).
        api.horaSalud = "2026-10-12T11:59:30+00:00"

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.FUNCIONA, estado.sensor)
        assertEquals(EstadoPieza.FUNCIONA, estado.analisis)
        assertEquals(EstadoPieza.FUNCIONA, estado.servidor)
        assertEquals(Fixtures.AHORA.minusSeconds(9), estado.ultimaCorrida)
        // 11:59:00 del servidor son las 11:59:30 del teléfono.
        assertEquals(Fixtures.AHORA.minusSeconds(30), estado.ultimoFlujo)
        assertEquals(Fixtures.AHORA, estado.consultadoEn)
    }

    @Test
    fun `con el latido vencido el analisis esta detenido y del sensor no se sabe`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.sistema = sistema(analisis = "detenido", sensor = "sin_datos", segundos = 150)

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.DETENIDO, estado.analisis)
        assertEquals(EstadoPieza.SIN_DATOS, estado.sensor)
        assertEquals(EstadoPieza.FUNCIONA, estado.servidor)
        assertEquals(Fixtures.AHORA.minusSeconds(150), estado.ultimaCorrida)
    }

    @Test
    fun `el sensor detenido y un estado que no se conoce nunca quedan en funciona`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.sistema = sistema(analisis = "reiniciando", sensor = "detenido", ultimoFlujo = "no es una fecha")

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.SIN_DATOS, estado.analisis)
        assertEquals(EstadoPieza.DETENIDO, estado.sensor)
        assertNull(estado.ultimoFlujo)
    }

    @Test
    fun `un servidor anterior a la API 0_7_0 no manda sistema y queda todo sin datos menos el servidor`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.FUNCIONA, estado.servidor)
        assertEquals(EstadoPieza.SIN_DATOS, estado.analisis)
        assertEquals(EstadoPieza.SIN_DATOS, estado.sensor)
        assertNull(estado.ultimaCorrida)
    }

    @Test
    fun `si el servidor no contesta queda No responde y lo demas sin datos`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.errores["salud"] = IOException("servidor apagado")

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.NO_RESPONDE, estado.servidor)
        assertEquals(EstadoPieza.SIN_DATOS, estado.analisis)
        assertEquals(EstadoPieza.SIN_DATOS, estado.sensor)
    }

    @Test
    fun `si no acepta el token el servidor funciona pero no hay datos`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.errores["salud"] = errorHttp(401)

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.FUNCIONA, estado.servidor)
        assertEquals(EstadoPieza.SIN_DATOS, estado.analisis)
    }

    @Test
    fun `un error del servidor cuenta como que no responde`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.errores["salud"] = errorHttp(500)

        assertEquals(EstadoPieza.NO_RESPONDE, redes.estadoDelSistema("red-1").servidor)
    }

    @Test
    fun `una red que no esta en el telefono da No responde sin llamar a nadie`() = runTest {
        api.errores["salud"] = IllegalStateException("no tenía que llamar")

        assertEquals(EstadoPieza.NO_RESPONDE, redes.estadoDelSistema("red-que-no-existe").servidor)
    }
}
