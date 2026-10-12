package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ObtenerDetalleAlertaTest {

    @Test
    fun `si la alerta esta en el telefono la devuelve sin ir al servidor`() = runTest {
        val alertas = FakeAlertasRepository(listOf(Fixtures.alerta("a-1")))
        val casoDeUso = ObtenerDetalleAlerta(alertas, FakeConectividad(conectadoAlInicio = false))

        val alerta = casoDeUso.descargarSiFalta("red-1", "a-1")

        assertEquals("a-1", alerta?.id)
    }

    @Test
    fun `si falta y hay conexion la descarga y queda guardada`() = runTest {
        val alertas = FakeAlertasRepository()
        alertas.alertasRemotas = mapOf("a-9" to Fixtures.alerta("a-9"))
        val casoDeUso = ObtenerDetalleAlerta(alertas, FakeConectividad(conectadoAlInicio = true))

        val alerta = casoDeUso.descargarSiFalta("red-1", "a-9")

        assertEquals("a-9", alerta?.id)
        assertNotNull(casoDeUso.observar("red-1", "a-9").first())
    }

    @Test
    fun `si falta y no hay conexion devuelve null para mostrar lo que trajo el aviso`() = runTest {
        val alertas = FakeAlertasRepository()
        alertas.alertasRemotas = mapOf("a-9" to Fixtures.alerta("a-9"))
        val casoDeUso = ObtenerDetalleAlerta(alertas, FakeConectividad(conectadoAlInicio = false))

        assertNull(casoDeUso.descargarSiFalta("red-1", "a-9"))
        assertNull(casoDeUso.observar("red-1", "a-9").first())
    }
}
