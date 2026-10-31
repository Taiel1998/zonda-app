package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.CambioRegistroDto
import ar.edu.uade.zonda.data.remote.DtoFixtures
import ar.edu.uade.zonda.data.remote.EstadoFirewallDto
import ar.edu.uade.zonda.data.remote.PersonaDto
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.data.remote.errorHttp
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.TipoCambio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los bloqueos del firewall contra la API en memoria, con las mismas reglas que el servidor (decisión 51). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BloqueosRepositoryImplTest : PruebaConBase() {

    private val api = ApiFalsa()
    private lateinit var redes: RedesRepositoryImpl
    private lateinit var bloqueos: BloqueosRepositoryImpl

    @Before
    fun preparar() {
        redes = RedesRepositoryImpl(base.redesDao(), FakeAlmacenSeguro(), ProveedorDeApiFalso(api), reloj = { Fixtures.AHORA })
        bloqueos = BloqueosRepositoryImpl(base.redesDao(), ProveedorDeApiFalso(api))
        api.publicar(DtoFixtures.alerta("a-1"))
        // El firewall preguntó a las 11:59:24 del servidor.
        api.firewall = EstadoFirewallDto(habilitado = true, conectado = true, ultimaConsulta = "2026-10-12T11:59:24+00:00", nuncaBloquear = listOf("192.168.20.0/24"))
    }

    @Test
    fun `un administrador bloquea, el firewall lo aplica y despues lo desbloquea`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")

        val nuevo = bloqueos.bloquear("red-1", "accion-1", "a-1", DuracionBloqueo.UNA_HORA, "Fuerza bruta.")

        assertEquals(EstadoBloqueo.APROBADO, nuevo.estado)
        assertEquals("pc-09", nuevo.etiqueta)
        assertNotNull(nuevo.venceEn)
        api.aplicarBloqueosAprobados()
        assertEquals(EstadoBloqueo.APLICADO, bloqueos.consultar("red-1").activoDe("192.168.10.9")?.estado)

        val suelto = bloqueos.desbloquear("red-1", nuevo.id, "Se cambió la contraseña.")

        assertEquals(EstadoBloqueo.DESBLOQUEADO, suelto.estado)
        assertNull(bloqueos.consultar("red-1").activoDe("192.168.10.9"))
        assertEquals(listOf("Se cambió la contraseña."), api.motivosRecibidos)
    }

    @Test
    fun `un analista pide y un administrador lo aprueba despues`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.personas[0] = api.personas[0].copy(perfil = "analista")

        val pedido = bloqueos.bloquear("red-1", "accion-1", "a-1", DuracionBloqueo.UN_DIA, "Fuerza bruta.")

        assertEquals(EstadoBloqueo.PEDIDO, pedido.estado)
        assertNull(pedido.venceEn)
        api.personas[0] = api.personas[0].copy(perfil = "administrador")
        val aprobado = bloqueos.aprobar("red-1", pedido.id)
        assertEquals(EstadoBloqueo.APROBADO, aprobado.estado)
        assertEquals(86_400, aprobado.segundos)
        assertNotNull(aprobado.venceEn)
    }

    @Test
    fun `hasta desbloquear sale sin revisar la alerta y viaja sin vencimiento`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")

        val hasta = bloqueos.bloquear("red-1", "accion-1", "a-1", DuracionBloqueo.HASTA_DESBLOQUEAR, "Fuerza bruta.")

        assertEquals(EstadoBloqueo.APROBADO, hasta.estado)
        assertNull(hasta.segundos)
        assertNull(hasta.venceEn)
        assertEquals(null, api.bloqueosRecibidos.last().duracion)
    }

    @Test
    fun `un equipo con un bloqueo abierto no se bloquea dos veces`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        bloqueos.bloquear("red-1", "accion-1", "a-1", DuracionBloqueo.UNA_HORA, "Fuerza bruta.")
        api.publicar(DtoFixtures.alerta("a-2"))

        val error = runCatching { bloqueos.bloquear("red-1", "accion-2", "a-2", DuracionBloqueo.UNA_HORA, "Otra vez.") }.exceptionOrNull()

        assertEquals("Ese equipo ya tiene un bloqueo o un pedido de bloqueo", (error as ErrorZonda.BloqueoNoPosible).detalle)
    }

    @Test
    fun `cambiar la duracion la deja aprobada hasta que el firewall la vuelve a aplicar`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        val nuevo = bloqueos.bloquear("red-1", "accion-1", "a-1", DuracionBloqueo.UNA_HORA, "Fuerza bruta.")
        api.aplicarBloqueosAprobados()

        val cambiado = bloqueos.cambiarDuracion("red-1", nuevo.id, DuracionBloqueo.UN_DIA, "Sigue el ataque.")

        assertEquals(EstadoBloqueo.APROBADO, cambiado.estado)
        assertEquals(86_400, cambiado.segundos)
        assertNull(cambiado.aplicadoEn)
    }

    @Test
    fun `sin la red en el telefono no se llama al servidor`() = runTest {
        val error = runCatching { bloqueos.consultar("red-que-no-existe") }.exceptionOrNull()

        assertTrue(error is ErrorZonda.NoEncontrado)
    }

    @Test
    fun `Estado del sistema suma el firewall en el reloj del telefono y los bloqueos abiertos`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        // El reloj del servidor va 30 s atrasado respecto del teléfono (Fixtures.AHORA es 12:00:00).
        api.horaSalud = "2026-10-12T11:59:30+00:00"
        bloqueos.bloquear("red-1", "accion-1", "a-1", DuracionBloqueo.UNA_HORA, "Fuerza bruta.")

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.FUNCIONA, estado.firewall)
        // 11:59:24 del servidor son las 11:59:54 del teléfono: "Última consulta hace 6 s".
        assertEquals(Fixtures.AHORA.minusSeconds(6), estado.ultimaConsultaFirewall)
        assertEquals(listOf("pc-09"), estado.bloqueos.map { it.etiqueta })
    }

    @Test
    fun `sin firewall conectado o con un servidor anterior la tarjeta no tiene fila del firewall`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")

        api.firewall = api.firewall.copy(habilitado = false)
        assertNull(redes.estadoDelSistema("red-1").firewall)
        api.firewall = api.firewall.copy(habilitado = true, conectado = false)
        assertEquals(EstadoPieza.DETENIDO, redes.estadoDelSistema("red-1").firewall)
        api.firewallEnSalud = false
        assertNull(redes.estadoDelSistema("red-1").firewall)
    }

    @Test
    fun `si no llegan los bloqueos la tarjeta sigue con el firewall y sin la lista`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.errores["bloqueos"] = errorHttp(500)

        val estado = redes.estadoDelSistema("red-1")

        assertEquals(EstadoPieza.FUNCIONA, estado.firewall)
        assertTrue(estado.bloqueos.isEmpty())
    }

    @Test
    fun `los navegadores del tablero quedan marcados al traer la gente`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.personas += PersonaDto("d-9", null, "Tablero web", "administrador", null, vos = false, tipo = "navegador")

        redes.actualizarPersonasYRegistro("red-1")

        val personas = redes.observarPersonas("red-1").first()
        assertEquals(listOf("d-1" to false, "d-9" to true), personas.map { it.id to it.navegador })
    }

    @Test
    fun `el registro se pide con los bloqueos y saltea lo que la app no conoce`() = runTest {
        redes.guardarRedPareada(Fixtures.red("red-1"), token = "t")
        api.registro += CambioRegistroDto(1, "2026-10-12T11:58:00+00:00", "bloqueo", "d-1", "Este teléfono", "192.168.10.9", "pc-09", null, "3600", "Fuerza bruta.")
        api.registro += CambioRegistroDto(2, "2026-10-12T11:59:00+00:00", "regla_nueva", "d-1", "Este teléfono", "x", null, null, null, null)

        redes.actualizarPersonasYRegistro("red-1")

        val registro = redes.observarRegistro("red-1").first()
        assertEquals(true, api.registroConBloqueos)
        assertEquals(listOf(TipoCambio.BLOQUEO), registro.map { it.tipo })
        assertEquals(3600, registro.single().segundosDespues)
    }
}
