package ar.edu.uade.zonda.data.remote

import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.tipoConocido
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Los bloqueos del firewall con el cliente HTTP real contra un servidor de mentira (API 0.9.0, decisión 51). */
class BloqueosEnLaApiTest {

    private val servidor = MockWebServer()

    @Before
    fun arrancar() {
        servidor.start()
    }

    @After
    fun parar() {
        servidor.shutdown()
    }

    private fun api(): ZondaApi = ProveedorDeApiRetrofit(
        tokens = { "token-1" },
        clienteBase = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build(),
    ).para("red-1", servidor.url("/").toString())

    private fun respuesta(codigo: Int = 200, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `hasta desbloquear viaja como duracion null y no se pierde`() = runTest {
        servidor.enqueue(respuesta(201, BLOQUEO))

        api().bloquear(BloqueoEntradaDto("accion-1", "a-1", null, "Fuerza bruta."))

        val pedido = servidor.takeRequest()
        val cuerpo = pedido.body.readUtf8()
        assertEquals("POST /bloqueos", "${pedido.method} ${pedido.requestUrl?.encodedPath}")
        // Sin el campo, el servidor pondría una hora: el null tiene que estar escrito.
        assertTrue(cuerpo, cuerpo.contains("\"duracion\":null"))
        assertTrue(cuerpo, cuerpo.contains("\"accionId\":\"accion-1\""))
    }

    @Test
    fun `una duracion viaja en segundos y el cambio a hasta desbloquear tambien manda el null`() = runTest {
        servidor.enqueue(respuesta(201, BLOQUEO))
        servidor.enqueue(respuesta(200, BLOQUEO))

        api().bloquear(BloqueoEntradaDto("accion-1", "a-1", 86_400, "Fuerza bruta."))
        api().cambiarDuracionBloqueo("b-1", DuracionEntradaDto(null, "Sigue el ataque."))

        assertTrue(servidor.takeRequest().body.readUtf8().contains("\"duracion\":86400"))
        val cambio = servidor.takeRequest()
        assertEquals("PUT /bloqueos/b-1/duracion", "${cambio.method} ${cambio.requestUrl?.encodedPath}")
        assertTrue(cambio.body.readUtf8().contains("\"duracion\":null"))
    }

    @Test
    fun `los demas cuerpos siguen sin escribir sus null`() {
        val asignacion = ProveedorDeApiRetrofit.gson.toJson(AsignacionEntradaDto("accion-1", null))

        assertFalse(asignacion, asignacion.contains("responsableId"))
    }

    @Test
    fun `aprobar va sin cuerpo y desbloquear y rechazar llevan el motivo`() = runTest {
        repeat(3) { servidor.enqueue(respuesta(200, BLOQUEO)) }

        api().aprobarBloqueo("b-1")
        api().desbloquear("b-1", MotivoEntradaDto("Se cambió la contraseña."))
        api().rechazarBloqueo("b-1", MotivoEntradaDto("Es el backup."))

        val aprobacion = servidor.takeRequest()
        assertEquals("POST /bloqueos/b-1/aprobacion", "${aprobacion.method} ${aprobacion.requestUrl?.encodedPath}")
        assertEquals(0L, aprobacion.bodySize)
        val desbloqueo = servidor.takeRequest()
        assertEquals("/bloqueos/b-1/desbloqueo", desbloqueo.requestUrl?.encodedPath)
        assertEquals("""{"motivo":"Se cambió la contraseña."}""", desbloqueo.body.readUtf8())
        assertEquals("/bloqueos/b-1/rechazo", servidor.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun `lee la lista de bloqueos y el firewall como los manda el servidor`() = runTest {
        servidor.enqueue(respuesta(cuerpo = """{"bloqueos": [$BLOQUEO], "firewall": $FIREWALL}"""))

        val lista = api().bloqueos().aDominio()

        val bloqueo = lista.bloqueos.single()
        assertEquals(EstadoBloqueo.APLICADO, bloqueo.estado)
        assertEquals("pc-rrhh", bloqueo.etiqueta)
        assertEquals(3600, bloqueo.segundos)
        assertEquals(Instant.parse("2026-10-31T16:34:34Z"), bloqueo.venceEn)
        assertEquals(Instant.parse("2026-10-31T15:34:40Z"), bloqueo.aplicadoEn)
        assertTrue(lista.firewall.habilitado && lista.firewall.conectado)
        assertEquals(listOf("192.168.10.1/32", "192.168.20.0/24"), lista.firewall.nuncaBloquear)
        assertTrue(lista.firewall.nuncaBloquea("192.168.20.20"))
    }

    @Test
    fun `si el servidor no deja bloquear, el error trae el porque con sus palabras`() = runTest {
        val porques = mapOf(
            409 to "Ese equipo ya tiene un bloqueo o un pedido de bloqueo",
            422 to "El firewall nunca bloquea ese equipo: está en su lista de «nunca bloquear»",
            429 to "Demasiados bloqueos en una hora desde este dispositivo",
            503 to "Los bloqueos están apagados: esta red no tiene un firewall conectado",
        )
        for ((codigo, porque) in porques) {
            servidor.enqueue(respuesta(codigo, """{"detail": "$porque"}"""))

            val error = runCatching { conErroresTraducidos(OperacionHttp.BLOQUEO) { api().bloquear(BloqueoEntradaDto("accion-1", "a-1", 3600, "m")) } }.exceptionOrNull()

            assertEquals(porque, (error as ErrorZonda.BloqueoNoPosible).detalle)
        }
    }

    @Test
    fun `un analista que quiere desbloquear recibe sin permiso y fuera de los bloqueos un 422 es error del servidor`() = runTest {
        servidor.enqueue(respuesta(403, """{"detail": "Lo cambia un administrador"}"""))
        servidor.enqueue(respuesta(422, """{"detail": [{"msg": "field required"}]}"""))

        val sinPermiso = runCatching { conErroresTraducidos(OperacionHttp.BLOQUEO) { api().desbloquear("b-1", MotivoEntradaDto("m")) } }.exceptionOrNull()
        val otro = runCatching { conErroresTraducidos { api().bloqueos() } }.exceptionOrNull()

        assertTrue(sinPermiso is ErrorZonda.SinPermiso)
        assertEquals(422, (otro as ErrorZonda.ErrorDelServidor).codigo)
    }

    @Test
    fun `un detalle que no es texto deja el error sin porque`() = runTest {
        servidor.enqueue(respuesta(422, """{"detail": [{"msg": "field required"}]}"""))

        val error = runCatching { conErroresTraducidos(OperacionHttp.BLOQUEO) { api().bloquear(BloqueoEntradaDto("accion-1", "a-1", 3600, "m")) } }.exceptionOrNull()

        assertEquals(null, (error as ErrorZonda.BloqueoNoPosible).detalle)
    }

    @Test
    fun `el registro se pide con los bloqueos y salud trae el firewall`() = runTest {
        servidor.enqueue(respuesta(cuerpo = "[]"))
        servidor.enqueue(respuesta(cuerpo = """{"version": "0.9.0", "hora": "2026-10-31T15:35:00+00:00", "alertas": 539, "red": {"id": "red-oficina", "nombre": "Oficina"}, "tokenVence": null, "firewall": $FIREWALL}"""))

        api().registro(conBloqueos = true)
        val salud = api().salud()

        assertEquals("true", servidor.takeRequest().requestUrl?.queryParameter("bloqueos"))
        assertEquals(true, salud.firewall?.conectado)
    }

    @Test
    fun `un estado que la app no conoce se toma como terminado`() {
        val raro = ProveedorDeApiRetrofit.gson.fromJson(BLOQUEO, BloqueoDto::class.java).copy(estado = "en_revision").aDominio()

        assertEquals(EstadoBloqueo.VENCIDO, raro.estado)
        assertFalse(raro.estado.activo)
    }

    @Test
    fun `la ultima consulta del firewall se corre lo que difiere el reloj del servidor`() {
        val firewall = EstadoFirewallDto(habilitado = true, conectado = true, ultimaConsulta = "2026-10-12T11:59:30+00:00", nuncaBloquear = null)
            .aDominio(corrimiento = Duration.ofSeconds(30))

        assertEquals(Instant.parse("2026-10-12T12:00:00Z"), firewall.ultimaConsulta)
        assertEquals(emptyList<String>(), firewall.nuncaBloquear)
    }

    @Test
    fun `el registro reconoce los bloqueos y saltea los tipos que no conoce`() {
        fun cambio(tipo: String) = CambioRegistroDto(1, "2026-10-31T15:34:34+00:00", tipo, "d-1", "Taiel", "192.168.30.50", "pc-rrhh", null, "3600", "m")

        assertTrue(cambio("bloqueo").tipoConocido)
        assertTrue(cambio("desbloqueo").tipoConocido)
        assertFalse(cambio("regla_nueva").tipoConocido)
    }

    private companion object {
        const val FIREWALL = """{"habilitado": true, "conectado": true, "ultimaConsulta": "2026-10-31T15:36:31+00:00", "nuncaBloquear": ["192.168.10.1/32", "192.168.20.0/24"]}"""
        const val BLOQUEO = """{
            "id": "b-e6bd64ef536e", "alertaId": "a-dbb2231dff97", "ip": "192.168.30.50", "nombre": "pc-rrhh",
            "estado": "aplicado", "duracion": 3600, "motivo": "Fuerza bruta de pc-rrhh a srv-sueldos.",
            "pedidoPorId": "d-1", "pedidoPor": "Taiel", "pedidoEn": "2026-10-31T15:34:34+00:00",
            "aprobadoPorId": "d-1", "aprobadoPor": "Taiel", "aprobadoEn": "2026-10-31T15:34:34+00:00",
            "venceEn": "2026-10-31T16:34:34+00:00", "aplicadoEn": "2026-10-31T15:34:40+00:00",
            "terminadoPorId": null, "terminadoPor": null, "terminadoEn": null, "detalle": null
        }"""
    }
}
