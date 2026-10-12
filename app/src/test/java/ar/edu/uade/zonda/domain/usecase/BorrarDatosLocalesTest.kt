package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BorrarDatosLocalesTest {

    @Test
    fun `borra alertas y acciones de todas las redes, reinicia sus cursores y conserva los pareos`() = runTest {
        val redes = FakeRedesRepository(listOf(Fixtures.red("red-1"), Fixtures.red("red-2", activa = false)))
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1"), Fixtures.alerta("a-2", redId = "red-2")))
        val acciones = FakeAccionesPendientesRepository().apply { encolar(Fixtures.accion(alertaId = "a-1")) }

        BorrarDatosLocales(redes, alertas, acciones)()

        assertTrue(alertas.todas.isEmpty())
        assertTrue(acciones.todas.isEmpty())
        assertEquals(listOf("red-1", "red-2"), redes.todas.map { it.id })
        assertTrue(redes.todas.all { it.ultimaSincronizacion == null && it.cursor == null })
        assertEquals("red-1", redes.todas.single { it.activa }.id)
        assertTrue(redes.redesQuitadas.isEmpty())
        assertNull(alertas.observarAlerta("red-1", "a-1").first())
    }
}
