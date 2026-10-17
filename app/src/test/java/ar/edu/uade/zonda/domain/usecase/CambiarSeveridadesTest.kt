package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cambiar la configuración de la red: severidades e importancia de un equipo, siempre con motivo (decisiones 40 y 41). */
class CambiarSeveridadesTest {

    private val redes = FakeRedesRepository(listOf(Fixtures.red()))
    private val acciones = FakeAccionesPendientesRepository()

    @Test
    fun `guarda las severidades en el telefono y encola el envio con el motivo`() = runTest {
        val nuevas = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA)

        val accion = CambiarSeveridades(redes, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA })("red-1", nuevas, "  Ahora los escaneos son prioridad. ")

        assertEquals(Severidad.ALTA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.ESCANEO))
        assertEquals("accion-1", accion.id)
        assertEquals(TipoAccion.CAMBIAR_SEVERIDADES, accion.tipo)
        assertEquals(EstadoEnvio.PENDIENTE, accion.estado)
        assertEquals("Ahora los escaneos son prioridad.", accion.motivo)
        assertEquals(listOf(accion), acciones.todas)
    }

    @Test
    fun `sin motivo no guarda nada`() = runTest {
        val nuevas = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA)

        val error = runCatching { CambiarSeveridades(redes, acciones, GeneradorDeIdsFijo())("red-1", nuevas, "   ") }.exceptionOrNull()

        assertTrue(error is ErrorZonda.Validacion)
        assertEquals(Severidad.MEDIA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.ESCANEO))
        assertTrue(acciones.todas.isEmpty())
    }

    @Test
    fun `cambiar la importancia de un equipo la guarda y la encola con su motivo`() = runTest {
        redes.equipos.value = listOf(EquipoDeLaRed("192.168.10.50", "srv-sueldos", Importancia.NORMAL))
        val cambiar = CambiarImportancia(redes, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA })

        assertTrue(runCatching { cambiar("red-1", "192.168.10.50", Importancia.CRITICO, "") }.exceptionOrNull() is ErrorZonda.Validacion)
        val accion = cambiar("red-1", "192.168.10.50", Importancia.CRITICO, "Ahí están los sueldos de toda la empresa.")

        assertEquals(Importancia.CRITICO, redes.equipos.value.single().importancia)
        assertEquals(TipoAccion.CAMBIAR_IMPORTANCIA, accion.tipo)
        assertEquals("192.168.10.50", accion.equipoIp)
        assertEquals(Importancia.CRITICO, accion.importancia)
        assertEquals("Ahí están los sueldos de toda la empresa.", accion.motivo)
        assertEquals(listOf(accion), acciones.todas)
    }
}
