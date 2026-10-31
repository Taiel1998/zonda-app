package ar.edu.uade.zonda.ui.detalle

import ar.edu.uade.zonda.domain.model.Perfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La huella o el PIN antes de lo que corta o devuelve tráfico (decisión 51), con un diálogo de mentira. */
class ConfirmacionDelDuenoTest {

    /** Contesta lo que se le diga y anota qué se le pidió. */
    private class DialogoFalso(private val respuesta: Confirmacion) : ConfirmacionDelDueno {
        val pedidos = mutableListOf<String>()

        override fun pedir(titulo: String, detalle: String, alTerminar: (Confirmacion) -> Unit) {
            pedidos += "$titulo | $detalle"
            alTerminar(respuesta)
        }
    }

    private fun confirmar(pide: Boolean, dialogo: ConfirmacionDelDueno?): String {
        var paso = "nada"
        confirmarConHuella(pide, dialogo, "Confirmá que sos vos", "Bloquear pc-rrhh · 1 hora", alConfirmar = { paso = "confirmado" }, alNoPoder = { paso = "no se puede" })
        return paso
    }

    @Test
    fun `piden la huella las acciones que cambian lo que corta el firewall`() {
        assertTrue(pideHuella(AccionBloqueo.BLOQUEAR, Perfil.ADMINISTRADOR))
        assertTrue(pideHuella(AccionBloqueo.CAMBIAR_DURACION, Perfil.ADMINISTRADOR))
        assertTrue(pideHuella(AccionBloqueo.DESBLOQUEAR, Perfil.ADMINISTRADOR))
        // Un analista pide el bloqueo y lo aprueba un administrador, con su huella; rechazar no corta nada.
        assertFalse(pideHuella(AccionBloqueo.BLOQUEAR, Perfil.ANALISTA))
        assertFalse(pideHuella(AccionBloqueo.RECHAZAR, Perfil.ADMINISTRADOR))
    }

    @Test
    fun `con la huella confirmada sale y el dialogo dice que se hace`() {
        val dialogo = DialogoFalso(Confirmacion.CONFIRMADA)

        assertEquals("confirmado", confirmar(pide = true, dialogo = dialogo))
        assertEquals(listOf("Confirmá que sos vos | Bloquear pc-rrhh · 1 hora"), dialogo.pedidos)
    }

    @Test
    fun `si la persona cancela no pasa nada`() {
        assertEquals("nada", confirmar(pide = true, dialogo = DialogoFalso(Confirmacion.CANCELADA)))
    }

    @Test
    fun `sin huella ni PIN en el telefono no sale`() {
        assertEquals("no se puede", confirmar(pide = true, dialogo = DialogoFalso(Confirmacion.SIN_BLOQUEO_DE_PANTALLA)))
        // Sin forma de preguntar, tampoco: nunca se bloquea sin confirmar.
        assertEquals("no se puede", confirmar(pide = true, dialogo = null))
    }

    @Test
    fun `lo que no la pide sale sin preguntar`() {
        val dialogo = DialogoFalso(Confirmacion.CANCELADA)

        assertEquals("confirmado", confirmar(pide = false, dialogo = dialogo))
        assertTrue(dialogo.pedidos.isEmpty())
    }
}
