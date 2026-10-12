package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SincronizarAlertasTest {

    @Test
    fun `sincroniza la red activa cuando no se indica una`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red("red-1", activa = false), Fixtures.red("red-2", activa = true)))
        val alertas = FakeAlertasRepository()

        SincronizarAlertas(redes, alertas)()

        assertEquals(listOf("red-2"), alertas.sincronizaciones)
    }

    @Test
    fun `sin red pareada devuelve null y no toca el repositorio`() = runTest {
        val alertas = FakeAlertasRepository()

        assertNull(SincronizarAlertas(FakeRedesRepository(), alertas)())
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `los errores de red llegan como ErrorZonda`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red()))
        val alertas = FakeAlertasRepository().apply { errorAlSincronizar = ErrorZonda.NoAutorizado() }

        val error = runCatching { SincronizarAlertas(redes, alertas)() }.exceptionOrNull()

        assertTrue(error is ErrorZonda.NoAutorizado)
    }
}
