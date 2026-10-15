package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los casos de uso de Telegram: vincular el chat y mandarle al bot cómo avisa cada severidad. */
class TelegramTest {

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1"), Fixtures.red("red-2", activa = false)))
    private val preferencias = FakePreferenciasRepository(primerInicioHecho = true)

    @Test
    fun `pedir el enlace manda los avisos que eligio la persona`() = runTest {
        preferencias.avisos.value = PreferenciasAvisos(media = ModoAviso.SIN_AVISO)

        val vinculo = VincularTelegram(redes, preferencias).pedirEnlace("red-1")

        assertEquals(redes.vinculoTelegram, vinculo)
        assertEquals("red-1" to PreferenciasAvisos(media = ModoAviso.SIN_AVISO), redes.vinculosPedidos.single())
    }

    @Test
    fun `comprobar deja la red vinculada cuando el servidor lo confirma`() = runTest {
        val vincular = VincularTelegram(redes, preferencias)
        assertEquals(false, vincular.comprobar("red-1").vinculado)

        redes.telegramEnElServidor = true
        vincular.comprobar("red-1")

        val red = redes.observarRedActiva().first()!!
        assertTrue(red.telegramVinculado)
        assertEquals("zonda_alertas_bot", red.telegramBot)
    }

    @Test
    fun `los avisos van a todas las redes y una que no responde no frena a las demas`() = runTest {
        val avisos = PreferenciasAvisos(baja = ModoAviso.SONIDO)

        EnviarAvisosTelegram(redes)(avisos)
        assertEquals(listOf("red-1" to avisos, "red-2" to avisos), redes.avisosEnviados)

        redes.errorTelegram = ErrorZonda.SinConexion()
        EnviarAvisosTelegram(redes)(avisos)
        assertEquals(2, redes.avisosEnviados.size)
    }
}
