package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sumar, cambiar de perfil y quitar gente desde Ajustes → Personas (corrección 2, decisión 39). */
class GestionarPersonasTest {

    private val redes = FakeRedesRepository(listOf(Fixtures.red()))
    private val personas = GestionarPersonas(redes)

    @Test
    fun `generar codigo pide el nombre y lo manda sin espacios con el perfil elegido`() = runTest {
        assertTrue(runCatching { personas.crearCodigo("red-1", "  ", Perfil.ANALISTA) }.exceptionOrNull() is ErrorZonda.Validacion)
        assertTrue(redes.codigosPedidos.isEmpty())

        val codigo = personas.crearCodigo("red-1", "  Laura ", Perfil.ANALISTA)

        assertEquals(listOf("Laura" to Perfil.ANALISTA), redes.codigosPedidos)
        assertEquals("K7M2-QX9P", codigo.codigo)
        assertTrue(codigo.contenido.startsWith("zonda:https://"))
    }

    @Test
    fun `cambiar el perfil y quitar del equipo pasan al servidor`() = runTest {
        redes.personas.value = listOf(
            Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, null, vos = true),
            Persona("d-2", "Laura", "Pixel 7", Perfil.ANALISTA, null, vos = false),
        )

        personas.cambiarPerfil("red-1", "d-2", Perfil.ADMINISTRADOR)
        personas.quitar("red-1", "d-2")

        assertEquals(listOf("d-2" to Perfil.ADMINISTRADOR), redes.perfilesCambiados)
        assertEquals(listOf("d-2"), redes.personasQuitadas)
        assertEquals(listOf("d-1"), redes.personas.value.map { it.id })
    }

    @Test
    fun `el servidor no deja sin administradores al equipo`() = runTest {
        redes.errorPersonas = ErrorZonda.UltimoAdministrador()

        val error = runCatching { personas.cambiarPerfil("red-1", "d-1", Perfil.ANALISTA) }.exceptionOrNull()

        assertTrue(error is ErrorZonda.UltimoAdministrador)
        assertTrue(redes.perfilesCambiados.isEmpty())
    }
}
