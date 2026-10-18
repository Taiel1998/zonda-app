package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Asignar alertas (corrección 1, decisión 42): sin conexión, como las revisiones, con las reglas de cada perfil. */
class AsignarAlertaTest {

    private val taiel = Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, null, vos = false)
    private val laura = Persona("d-2", "Laura", "Pixel 7", Perfil.ANALISTA, null, vos = false)
    private val meli = Persona("d-3", "Meli", "Galaxy A54", Perfil.ANALISTA, null, vos = false)

    private val redes = FakeRedesRepository(listOf(Fixtures.red()))
    private val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1")))
    private val acciones = FakeAccionesPendientesRepository()
    private val asignar = AsignarAlerta(redes, alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA })

    /** Este teléfono es [yo], con su perfil. */
    private fun soy(yo: Persona) {
        redes.actualizar(Fixtures.red().copy(perfil = yo.perfil, dispositivoId = yo.id))
        redes.personas.value = listOf(taiel, laura, meli).map { it.copy(vos = it.id == yo.id) }
    }

    private suspend fun responsable(): String? = alertas.observarAlerta("red-1", "a-1").first()?.asignacion?.responsableId

    @Test
    fun `el administrador asigna a cualquiera al instante y queda en la cola`() = runTest {
        soy(taiel)

        asignar("red-1", "a-1", laura)

        assertEquals("d-2", responsable())
        assertEquals(Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA), alertas.asignacionesLocales.single().second)
        assertEquals(mapOf("a-1" to "d-2"), acciones.asignaciones)

        asignar("red-1", "a-1", null)

        assertNull(responsable())
        assertEquals(mapOf<String, String?>("a-1" to null), acciones.asignaciones)
    }

    @Test
    fun `la analista se asigna una sin asignar y la suelta`() = runTest {
        soy(laura)

        asignar("red-1", "a-1", laura)
        assertEquals("d-2", responsable())
        assertTrue(alertas.asignacionesLocales.last().second.propia)

        asignar("red-1", "a-1", null)
        assertNull(responsable())
    }

    @Test
    fun `la analista no asigna a otra persona ni le saca una alerta a nadie`() = runTest {
        soy(laura)

        assertTrue(runCatching { asignar("red-1", "a-1", meli) }.exceptionOrNull() is ErrorZonda.SinPermiso)

        soy(meli)
        asignar("red-1", "a-1", meli)
        soy(laura)

        assertTrue(runCatching { asignar("red-1", "a-1", laura) }.exceptionOrNull() is ErrorZonda.SinPermiso)
        assertTrue(runCatching { asignar("red-1", "a-1", null) }.exceptionOrNull() is ErrorZonda.SinPermiso)
        assertEquals("d-3", responsable())
        assertEquals(mapOf("a-1" to "d-3"), acciones.asignaciones)
    }

    @Test
    fun `volver a elegir a la misma persona no encola nada`() = runTest {
        soy(taiel)
        asignar("red-1", "a-1", laura)

        asignar("red-1", "a-1", laura)

        assertEquals(1, alertas.asignacionesLocales.size)
    }

    @Test
    fun `una alerta que no esta en el telefono no se asigna`() = runTest {
        soy(taiel)

        assertTrue(runCatching { asignar("red-1", "otra", laura) }.exceptionOrNull() is ErrorZonda.NoEncontrado)
        assertTrue(acciones.asignaciones.isEmpty())
    }
}
