package ar.edu.uade.zonda.ui.ajustes

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.DetallesConexion
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetallesTecnicosViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))

    @Test
    fun `lee la conexion de la red activa una vez`() {
        val detalles = DetallesConexion("TLSv1.3", "Let's Encrypt", Instant.parse("2027-01-08T00:00:00Z"), Instant.parse("2027-01-10T00:00:00Z"))
        redes.detallesConexion = detalles

        val estado = DetallesTecnicosViewModel(redes, "Google Pixel 7").estado.value

        assertEquals("red-1", estado.red?.id)
        assertEquals("Google Pixel 7", estado.nombreDelTelefono)
        assertEquals(LecturaConexion.Lista(detalles), estado.conexion)
    }

    @Test
    fun `sin respuesta del servidor muestra lo guardado y el error`() {
        redes.errorDetalles = ErrorZonda.SinConexion()

        val estado = DetallesTecnicosViewModel(redes, "Google Pixel 7").estado.value

        assertEquals("red-1", estado.red?.id)
        assertTrue((estado.conexion as LecturaConexion.SinRespuesta).error is ErrorZonda.SinConexion)
    }

    @Test
    fun `sin red activa no consulta nada`() {
        val sinRedes = FakeRedesRepository(emptyList())

        val estado = DetallesTecnicosViewModel(sinRedes, "Google Pixel 7").estado.value

        assertNull(estado.red)
        assertEquals(LecturaConexion.Leyendo, estado.conexion)
    }

    @Test
    fun `la version de TLS se muestra como en el Figma`() {
        assertEquals("TLS 1.3", versionLegible("TLSv1.3"))
        assertEquals("TLS 1.2", versionLegible("TLSv1.2"))
    }
}
