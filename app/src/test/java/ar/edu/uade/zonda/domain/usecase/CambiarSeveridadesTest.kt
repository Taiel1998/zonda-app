package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CambiarSeveridadesTest {

    @Test
    fun `guarda las severidades en el telefono y encola el envio`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red()))
        val acciones = FakeAccionesPendientesRepository()
        val nuevas = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA)

        val accion = CambiarSeveridades(redes, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA })("red-1", nuevas)

        assertEquals(Severidad.ALTA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.ESCANEO))
        assertEquals("accion-1", accion.id)
        assertEquals(TipoAccion.CAMBIAR_SEVERIDADES, accion.tipo)
        assertEquals(EstadoEnvio.PENDIENTE, accion.estado)
        assertEquals(listOf(accion), acciones.todas)
    }
}
