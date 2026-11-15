package ar.edu.uade.zonda.ui.estado

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeBloqueosRepository.Companion.bloqueo
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Los bloqueos fuera de la lista de Estado (decisión 63): una sola línea con cuántos hay, como la pieza
 * Firewall del tablero, y la pantalla Bloqueos con cada uno.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BloqueosTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    private val aplicado = bloqueo(id = "b-1", alertaId = "a-1", estado = EstadoBloqueo.APLICADO)
    private val pedido = bloqueo(id = "b-2", alertaId = "a-2", estado = EstadoBloqueo.PEDIDO, pedidoPor = "Laura").copy(ip = "192.168.30.50", nombre = "pc-rrhh")
    private val aprobado = bloqueo(id = "b-3", alertaId = "a-3", estado = EstadoBloqueo.APROBADO).copy(ip = "192.168.30.51", nombre = "pc-ventas")

    @Test
    fun `con varios bloqueos, Estado los cuenta en una sola linea que abre la pantalla Bloqueos`() {
        var abierta: String? = null
        var verBloqueos = 0
        compose.setContent { ZondaTheme { TarjetaBloqueos(listOf(aplicado, pedido, aprobado), { abierta = it }, { verBloqueos++ }) } }

        // Ni cada equipo ni su estado: eso queda para la pantalla Bloqueos.
        compose.onAllNodesWithText("Pedido de Laura").assertCountEquals(0)
        compose.onNodeWithText("1 bloqueado · 1 bloqueando · 1 pedido").performClick()
        assertEquals(1, verBloqueos)
        assertNull(abierta)
    }

    @Test
    fun `la linea usa el plural y no nombra lo que esta en cero`() {
        val otroAplicado = aplicado.copy(id = "b-4", alertaId = "a-4", ip = "192.168.30.52", nombre = "pc-compras")
        val otroPedido = pedido.copy(id = "b-5", alertaId = "a-5", ip = "192.168.30.53", nombre = "notebook-3")
        compose.setContent { ZondaTheme { TarjetaBloqueos(listOf(aplicado, otroAplicado, pedido, otroPedido), {}, {}) } }

        compose.onNodeWithText("2 bloqueados · 2 pedidos").assertExists()
    }

    @Test
    fun `con un solo bloqueo, la linea lo nombra y abre su alerta`() {
        var abierta: String? = null
        var verBloqueos = 0
        compose.setContent {
            ZondaTheme {
                Column {
                    TarjetaBloqueos(listOf(aplicado), { abierta = it }, { verBloqueos++ })
                    TarjetaBloqueos(listOf(pedido), {}, {})
                    TarjetaBloqueos(listOf(aprobado), {}, {})
                }
            }
        }

        compose.onNodeWithText("Pedido de bloqueo: pc-rrhh").assertExists()
        compose.onNodeWithText("Bloqueando pc-ventas").assertExists()
        compose.onNodeWithText("pc-09 bloqueado").performClick()
        assertEquals("a-1", abierta)
        assertEquals(0, verBloqueos)
    }

    @Test
    fun `la pantalla Bloqueos pone primero los pedidos y al final los bloqueados, el que vence antes arriba`() {
        val hastaDesbloquear = bloqueo(id = "b-6", alertaId = "a-6", estado = EstadoBloqueo.APLICADO, segundos = null)
        val venceAntes = bloqueo(id = "b-7", alertaId = "a-7", estado = EstadoBloqueo.APLICADO, segundos = 900)
        val pedidoViejo = pedido.copy(id = "b-8", alertaId = "a-8", pedidoEn = Fixtures.AHORA.minusSeconds(3600))

        val orden = bloqueosEnOrden(listOf(hastaDesbloquear, aplicado, aprobado, pedido, venceAntes, pedidoViejo)).map { it.id }

        assertEquals(listOf("b-8", "b-2", "b-3", "b-7", "b-1", "b-6"), orden)
    }

    @Test
    fun `la pantalla Bloqueos agrupa por estado, cada grupo con su titulo, y cada fila abre su alerta`() {
        var abierta: String? = null
        val hastaDesbloquear = bloqueo(id = "b-6", alertaId = "a-6", estado = EstadoBloqueo.APLICADO, segundos = null).copy(ip = "192.168.30.52", nombre = "pc-compras")
        compose.setContent {
            ZondaTheme {
                BloqueosContenido(listOf(aplicado, pedido, aprobado, hastaDesbloquear), firewallConectado = true, alAbrirDetalle = { abierta = it }, alVolver = {}, zona = utc, ahora = Fixtures.AHORA)
            }
        }

        compose.onNodeWithText("Esperan aprobación").assertExists()
        compose.onNodeWithText("192.168.30.50 · Pedido de Laura").assertExists()
        // El aprobado todavía se está bloqueando; "Bloqueados" son solo los que el firewall aplicó.
        compose.onNodeWithText("Bloqueando").assertExists()
        compose.onNodeWithText("192.168.30.51 · El firewall lo aplica en unos segundos").assertExists()
        compose.onNodeWithText("Bloqueados").assertExists()
        compose.onNodeWithText("192.168.10.9 · Vence 12:58").assertExists()
        compose.onNodeWithText("192.168.30.52 · Hasta desbloquear").assertExists()
        compose.onNodeWithText("pc-rrhh").performClick()
        assertEquals("a-2", abierta)
    }

    @Test
    fun `en la pantalla Bloqueos, un aprobado con el firewall caido queda sin aplicar`() {
        compose.setContent {
            ZondaTheme {
                BloqueosContenido(listOf(aprobado), firewallConectado = false, alAbrirDetalle = {}, alVolver = {}, zona = utc, ahora = Fixtures.AHORA)
            }
        }

        compose.onNodeWithText("Sin aplicar").assertExists()
        compose.onNodeWithText("192.168.30.51 · El firewall no responde").assertExists()
        compose.onAllNodesWithText("Bloqueando", substring = true).assertCountEquals(0)
    }

    @Test
    fun `dos direcciones con el mismo nombre se distinguen por la direccion, y sin nombre el titulo ya es la direccion`() {
        // En la demo, el catálogo llama "pc-rrhh" a 192.168.30.50 y a 172.16.0.1.
        val rrhh = aplicado.copy(ip = "192.168.30.50", nombre = "pc-rrhh")
        val otraRrhh = aplicado.copy(id = "b-7", alertaId = "a-7", ip = "172.16.0.1", nombre = "pc-rrhh", segundos = null, venceEn = null)
        val sinNombre = aplicado.copy(id = "b-8", alertaId = "a-8", ip = "192.168.10.14", nombre = null)
        compose.setContent {
            ZondaTheme {
                BloqueosContenido(listOf(rrhh, otraRrhh, sinNombre), firewallConectado = true, alAbrirDetalle = {}, alVolver = {}, zona = utc, ahora = Fixtures.AHORA)
            }
        }

        compose.onNodeWithText("192.168.30.50 · Vence 12:58").assertExists()
        compose.onNodeWithText("172.16.0.1 · Hasta desbloquear").assertExists()
        compose.onNodeWithText("192.168.10.14").assertExists()
        compose.onNodeWithText("Vence 12:58").assertExists()
    }

    @Test
    fun `en la pantalla Bloqueos, un grupo sin bloqueos no aparece, ni su titulo`() {
        compose.setContent {
            ZondaTheme {
                BloqueosContenido(listOf(aplicado), firewallConectado = true, alAbrirDetalle = {}, alVolver = {}, zona = utc, ahora = Fixtures.AHORA)
            }
        }

        compose.onNodeWithText("Bloqueados").assertExists()
        compose.onAllNodesWithText("Esperan aprobación").assertCountEquals(0)
        compose.onAllNodesWithText("Bloqueando").assertCountEquals(0)
    }

    @Test
    fun `la pantalla Bloqueos sin ninguno lo dice y vuelve con la flecha`() {
        var volvio = false
        compose.setContent {
            ZondaTheme {
                BloqueosContenido(emptyList(), firewallConectado = true, alAbrirDetalle = {}, alVolver = { volvio = true }, zona = utc, ahora = Fixtures.AHORA)
            }
        }

        compose.onNodeWithText("No hay equipos bloqueados ni pedidos de bloqueo.").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        assertTrue(volvio)
    }

    /** Nueve bloqueados: uno lleva tilde y dos se llaman "rrhh", con y sin mayúsculas. */
    private fun nueve(): List<Bloqueo> = (1..9).map { n ->
        val nombre = when (n) {
            1 -> "pc-rrhh"
            2 -> "PC-RRHH-2"
            3 -> "pc-dirección"
            else -> "pc-$n"
        }
        aplicado.copy(id = "b-$n", alertaId = "a-$n", ip = "192.168.30.${50 + n}", nombre = nombre)
    }

    @Test
    fun `desde 9 bloqueos aparece el buscador, que busca en el nombre sin tildes ni mayusculas y en la IP`() {
        compose.setContent {
            ZondaTheme { BloqueosContenido(nueve(), firewallConectado = true, alAbrirDetalle = {}, alVolver = {}, zona = utc, ahora = Fixtures.AHORA) }
        }
        val campo = compose.onNode(hasSetTextAction())

        campo.performTextInput("RRHH")
        compose.onNodeWithText("pc-rrhh").assertExists()
        compose.onNodeWithText("PC-RRHH-2").assertExists()
        compose.onAllNodesWithText("pc-5").assertCountEquals(0)

        campo.performTextReplacement("direccion")
        compose.onNodeWithText("pc-dirección").assertExists()
        compose.onAllNodesWithText("pc-rrhh").assertCountEquals(0)

        campo.performTextReplacement("30.55")
        compose.onNodeWithText("pc-5").assertExists()
        compose.onAllNodesWithText("pc-6").assertCountEquals(0)

        // Sin coincidencias, ni los títulos de los grupos.
        campo.performTextReplacement("zzz")
        compose.onNodeWithText("Ningún equipo coincide con «zzz».").assertExists()
        compose.onAllNodesWithText("Bloqueados").assertCountEquals(0)
    }

    @Test
    fun `con 8 bloqueos o menos no hay buscador`() {
        compose.setContent {
            ZondaTheme { BloqueosContenido(nueve().take(8), firewallConectado = true, alAbrirDetalle = {}, alVolver = {}, zona = utc, ahora = Fixtures.AHORA) }
        }

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
