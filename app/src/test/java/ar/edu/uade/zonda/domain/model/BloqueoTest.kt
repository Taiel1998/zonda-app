package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.fakes.FakeBloqueosRepository
import ar.edu.uade.zonda.domain.fakes.FakeBloqueosRepository.Companion.bloqueo
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.usecase.GestionarBloqueos
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas de los bloqueos del firewall que el teléfono aplica solo (decisión 51). */
class BloqueoTest {

    @Test
    fun `una IPv4 esta en una red por su prefijo y una direccion sola vale como 32`() {
        assertTrue(enLaRed("192.168.20.20", "192.168.20.0/24"))
        assertFalse(enLaRed("192.168.30.50", "192.168.20.0/24"))
        assertTrue(enLaRed("192.168.10.1", "192.168.10.1/32"))
        assertTrue(enLaRed("192.168.10.1", "192.168.10.1"))
        assertFalse(enLaRed("192.168.10.2", "192.168.10.1"))
        assertTrue(enLaRed("10.0.2.15", "10.0.0.0/8"))
        assertTrue(enLaRed("8.8.8.8", "0.0.0.0/0"))
    }

    @Test
    fun `lo que no es una IPv4 o una red valida nunca esta en la lista`() {
        assertFalse(enLaRed("fe80::1", "192.168.20.0/24"))
        assertFalse(enLaRed("192.168.20.20", "192.168.20.0/33"))
        assertFalse(enLaRed("192.168.20.20", "192.168.20.0/24/8"))
        assertFalse(enLaRed("192.168.20.300", "192.168.20.0/24"))
        assertFalse(enLaRed("pc-rrhh", "192.168.20.0/24"))
    }

    @Test
    fun `el firewall nunca bloquea lo que esta en su lista`() {
        val firewall = EstadoFirewall(true, true, null, listOf("192.168.10.1/32", "192.168.20.0/24"))

        assertTrue(firewall.nuncaBloquea("192.168.20.20"))
        assertTrue(firewall.nuncaBloquea("192.168.10.1"))
        assertFalse(firewall.nuncaBloquea("192.168.30.50"))
    }

    @Test
    fun `el bloqueo abierto de un equipo es el pedido aprobado o aplicado y no uno terminado`() {
        val firewall = EstadoFirewall(true, true, null, emptyList())
        val terminado = bloqueo(id = "b-1", estado = EstadoBloqueo.DESBLOQUEADO)

        assertNull(BloqueosDeLaRed(listOf(terminado), firewall).activoDe("192.168.10.9"))
        val pedido = bloqueo(id = "b-2", estado = EstadoBloqueo.PEDIDO)
        assertEquals("b-2", BloqueosDeLaRed(listOf(pedido, terminado), firewall).activoDe("192.168.10.9")?.id)
        assertNull(BloqueosDeLaRed(listOf(pedido), firewall).activoDe("192.168.30.50"))
    }

    @Test
    fun `las duraciones son las que acepta el servidor y la predeterminada es una hora`() {
        assertEquals(listOf(3600, 86_400, null), DuracionBloqueo.entries.map { it.segundos })
        assertEquals(DuracionBloqueo.UNA_HORA, DuracionBloqueo.PREDETERMINADA)
        assertEquals(DuracionBloqueo.HASTA_DESBLOQUEAR, DuracionBloqueo.de(null))
        assertEquals(DuracionBloqueo.UN_DIA, DuracionBloqueo.de(86_400))
        // Un bloqueo viejo de 15 minutos u 8 horas no es ninguna de las de ahora.
        assertNull(DuracionBloqueo.de(900))
        assertNull(DuracionBloqueo.de(28_800))
    }

    @Test
    fun `cada accion sale con su id y su motivo sin espacios de mas`() = runTest {
        val repositorio = FakeBloqueosRepository()
        val gestionar = GestionarBloqueos(repositorio, GeneradorDeIdsFijo())

        val nuevo = gestionar.bloquear("red-1", "a-1", DuracionBloqueo.UN_DIA, "  Fuerza bruta.  ")

        assertEquals(listOf("bloquear a-1 UN_DIA Fuerza bruta."), repositorio.hecho)
        assertEquals(EstadoBloqueo.APROBADO, nuevo.estado)
        gestionar.desbloquear("red-1", nuevo.id, "Se cambió la contraseña.")
        assertEquals("desbloquear Se cambió la contraseña. ${nuevo.id}", repositorio.hecho.last())
    }

    @Test
    fun `sin motivo no sale nada`() = runTest {
        val repositorio = FakeBloqueosRepository()
        val gestionar = GestionarBloqueos(repositorio, GeneradorDeIdsFijo())

        val error = runCatching { gestionar.bloquear("red-1", "a-1", DuracionBloqueo.UNA_HORA, "   ") }.exceptionOrNull()

        assertTrue(error is ErrorZonda.Validacion)
        assertTrue(repositorio.hecho.isEmpty())
    }
}
