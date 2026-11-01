package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los perfiles de la red (decisión 39) y lo que cada uno puede hacer con el responsable de una alerta (decisión 42). */
class PersonasYAsignacionTest {

    private fun persona(id: String, nombre: String?, perfil: Perfil, vos: Boolean = false) =
        Persona(id = id, nombre = nombre, telefono = "Teléfono $id", perfil = perfil, ultimoUso = null, vos = vos)

    @Test
    fun `personas muestra primero a vos y despues a los administradores y a los analistas en el orden en que se sumaron`() {
        val laura = persona("d-2", "Laura", Perfil.ANALISTA)
        val meli = persona("d-3", "Meli", Perfil.ADMINISTRADOR)
        val yo = persona("d-4", "Taiel", Perfil.ANALISTA, vos = true)
        val ramiro = persona("d-5", "Ramiro", Perfil.ANALISTA)
        val sofia = persona("d-6", "Sofía", Perfil.ADMINISTRADOR)

        val orden = Persona.ordenar(listOf(laura, meli, yo, ramiro, sofia))

        assertEquals(listOf("Taiel", "Meli", "Sofía", "Laura", "Ramiro"), orden.map { it.nombreVisible })
    }

    @Test
    fun `un telefono pareado antes de la 1_0_5 se ve con el nombre del telefono`() {
        val vieja = persona("d-1", null, Perfil.ADMINISTRADOR)

        assertEquals("Teléfono d-1", vieja.nombreVisible)
        assertEquals("T", vieja.inicial)
        assertEquals("L", persona("d-2", " laura", Perfil.ANALISTA).inicial)
    }

    @Test
    fun `un navegador del tablero se reconoce por su nombre y se nombra sin el Tablero web`() {
        assertTrue(esNombreDeNavegador("Tablero web · Chrome en Windows"))
        assertTrue(esNombreDeNavegador("Tablero web"))
        assertFalse(esNombreDeNavegador("Laura"))
        assertFalse(esNombreDeNavegador(null))
        assertEquals("Chrome en Windows", navegadorDelNombre("Tablero web · Chrome en Windows"))
        // Los primeros se llamaban solo "Tablero web": no hay más nombre que ese.
        assertNull(navegadorDelNombre("Tablero web"))
    }

    @Test
    fun `el administrador asigna o cambia y el analista toma una libre o suelta la suya`() {
        assertEquals(AccionResponsable.ASIGNAR, AccionResponsable.para(Perfil.ADMINISTRADOR, null, "d-1"))
        assertEquals(AccionResponsable.CAMBIAR, AccionResponsable.para(Perfil.ADMINISTRADOR, "d-2", "d-1"))
        assertEquals(AccionResponsable.CAMBIAR, AccionResponsable.para(Perfil.ADMINISTRADOR, "d-1", "d-1"))
        assertEquals(AccionResponsable.ASIGNARME, AccionResponsable.para(Perfil.ANALISTA, null, "d-2"))
        assertEquals(AccionResponsable.SOLTARLA, AccionResponsable.para(Perfil.ANALISTA, "d-2", "d-2"))
        assertEquals(AccionResponsable.NINGUNA, AccionResponsable.para(Perfil.ANALISTA, "d-3", "d-2"))
    }

    @Test
    fun `asignarse una alerta es propia y no avisa a nadie`() {
        val propia = Asignacion("d-2", "Laura", "d-2", "Laura", Fixtures.AHORA)
        val deOtro = Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA)
        val soltada = Asignacion(null, null, "d-2", "Laura", Fixtures.AHORA)

        assertTrue(propia.propia)
        assertFalse(deOtro.propia)
        assertFalse(soltada.propia)
    }
}
