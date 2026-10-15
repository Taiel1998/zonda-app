package ar.edu.uade.zonda.data.remote

import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El cliente HTTP real contra un servidor de mentira: cabeceras, códigos de error y tiempos de espera. */
class ProveedorDeApiTest {

    private val servidor = MockWebServer()

    @Before
    fun arrancar() {
        servidor.start()
    }

    @After
    fun parar() {
        servidor.shutdown()
    }

    private fun proveedor(token: String? = "token-1") = ProveedorDeApiRetrofit(
        tokens = { token },
        clienteBase = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build(),
    )

    private fun api(token: String? = "token-1"): ZondaApi = proveedor(token).para("red-1", servidor.url("/").toString())

    private fun respuesta(codigo: Int = 200, cuerpo: String = "{}") =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `manda el token como Bearer y lee una pagina del contrato`() = runTest {
        servidor.enqueue(respuesta(cuerpo = PAGINA))

        val pagina = api().alertas("02aef296:10")

        val pedido = servidor.takeRequest()
        assertEquals("Bearer token-1", pedido.getHeader("Authorization"))
        assertEquals("/alertas", pedido.requestUrl?.encodedPath)
        assertEquals("02aef296:10", pedido.requestUrl?.queryParameter("desde"))
        assertEquals(listOf("a-1"), pagina.alertas.map { it.id })
        assertEquals("fuerza_bruta", pagina.alertas.single().tipoAtaque)
        assertEquals("02aef296:28", pagina.cursorSiguiente)
        assertFalse(pagina.hayMas)
    }

    @Test
    fun `sin token no manda la cabecera y la direccion base se normaliza con barra final`() = runTest {
        servidor.enqueue(respuesta(cuerpo = SALUD))
        val sinBarra = servidor.url("/").toString().trimEnd('/')

        val salud = proveedor(token = null).sinToken(sinBarra).salud()

        val pedido = servidor.takeRequest()
        assertNull(pedido.getHeader("Authorization"))
        assertEquals("/salud", pedido.path)
        assertEquals("0.3.0", salud.version)
        assertEquals(28, salud.alertas)
        assertEquals("red-oficina", salud.red?.id)
        assertNull(salud.tokenVence)
        assertEquals("https://zonda-equipo.duckdns.org/", ProveedorDeApiRetrofit.normalizar(" https://zonda-equipo.duckdns.org// "))
    }

    @Test
    fun `el 401 es NoAutorizado, el 404 NoEncontrado y un 5xx ErrorDelServidor con su codigo`() = runTest {
        servidor.enqueue(respuesta(401))
        servidor.enqueue(respuesta(404))
        servidor.enqueue(respuesta(503))

        assertTrue(errorDe { api().salud() } is ErrorZonda.NoAutorizado)
        assertTrue(errorDe { api().alerta("x") } is ErrorZonda.NoEncontrado)
        val error = errorDe { api().catalogo() }
        assertTrue(error is ErrorZonda.ErrorDelServidor)
        assertEquals(503, (error as ErrorZonda.ErrorDelServidor).codigo)
    }

    @Test
    fun `el 410 y el 400 cambian de significado segun la operacion`() = runTest {
        repeat(2) { servidor.enqueue(respuesta(410)) }
        repeat(2) { servidor.enqueue(respuesta(400)) }

        assertTrue(errorDe(OperacionHttp.SINCRONIZACION) { api().alertas("otra:5") } is ErrorZonda.CursorInvalido)
        assertTrue(errorDe(OperacionHttp.PAREO) { api().parear(PareoEntradaDto("K7M2-QX9P", "x")) } is ErrorZonda.CodigoVencido)
        assertTrue(errorDe(OperacionHttp.PAREO) { api().parear(PareoEntradaDto("ZZZZ-ZZZZ", "x")) } is ErrorZonda.CodigoInvalido)
        val general = errorDe { api().catalogo() }
        assertTrue(general is ErrorZonda.ErrorDelServidor)
        assertEquals(400, (general as ErrorZonda.ErrorDelServidor).codigo)
    }

    @Test
    fun `el 409 trae la revision de la otra persona`() = runTest {
        servidor.enqueue(respuesta(409, CONFLICTO))

        val error = errorDe(OperacionHttp.SINCRONIZACION) {
            api().revisar("a-1", RevisionEntradaDto("accion-1", "ataque_real", "escalado_al_proveedor", "Escalado", 1, false))
        }

        assertTrue(error is ErrorZonda.Conflicto)
        val ajena = (error as ErrorZonda.Conflicto).revisionAjena
        assertEquals("Teléfono de Lucía", ajena.autor)
        assertEquals(ResultadoRevision.FALSO_POSITIVO, ajena.resultado)
        assertEquals(Motivo.TAREA_PROGRAMADA, ajena.motivo)
        assertEquals("Era el respaldo", ajena.comentario)
        assertEquals(OrigenRevision.SERVIDOR, ajena.origen)
        val cuerpo = servidor.takeRequest().body.readUtf8()
        assertTrue(cuerpo, cuerpo.contains("\"accionId\":\"accion-1\""))

        // Un 409 sin la revisión ajena no se puede mostrar: es un error del servidor.
        servidor.enqueue(respuesta(409, """{"detail": "sin cuerpo útil"}"""))
        assertTrue(errorDe { api().catalogo() } is ErrorZonda.ErrorDelServidor)
    }

    @Test
    fun `sin respuesta a tiempo o sin servidor es SinConexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertTrue(errorDe { api().salud() } is ErrorZonda.SinConexion)

        val apagado = MockWebServer()
        apagado.start()
        val direccion = apagado.url("/").toString()
        apagado.shutdown()
        assertTrue(errorDe { proveedor().sinToken(direccion).salud() } is ErrorZonda.SinConexion)
    }

    @Test
    fun `una direccion http que el cliente no deja usar es ServidorNoVerificado y no llega al servidor`() = runTest {
        // En Android lo bloquea la configuración de red; acá, un cliente sin texto claro. OkHttp tira la
        // misma excepción (UnknownServiceException) antes de conectarse.
        val soloHttps = ProveedorDeApiRetrofit(
            tokens = { "token-1" },
            clienteBase = OkHttpClient.Builder().connectionSpecs(listOf(ConnectionSpec.MODERN_TLS)).build(),
        )

        val error = errorDe { soloHttps.para("red-1", servidor.url("/").toString()).salud() }

        assertTrue(error is ErrorZonda.ServidorNoVerificado)
        assertEquals(0, servidor.requestCount)
    }

    private suspend fun errorDe(operacion: OperacionHttp = OperacionHttp.GENERAL, bloque: suspend () -> Any?): Throwable? =
        runCatching { conErroresTraducidos(operacion) { bloque() } }.exceptionOrNull()

    companion object {
        private val SALUD = """{"version": "0.3.0", "hora": "2026-10-12T04:05:29+00:00", "alertas": 28, "red": {"id": "red-oficina", "nombre": "Oficina"}, "tokenVence": null}"""

        private val PAGINA = """
            {"alertas": [{
              "id": "a-1", "creadaEn": "2026-10-11T14:25:10+00:00", "inicio": "2026-10-11T14:02:00+00:00", "fin": "2026-10-11T14:20:10+00:00",
              "cantidadFlujos": 1198, "uidReferencia": "CxYz1", "origenIp": "172.16.0.1", "origenNombre": "gw-borde",
              "destinoIp": "192.168.10.50", "destinoNombre": "srv-web", "servicio": "ftp", "puerto": 21, "puertoReferencia": 21, "puertosDistintos": 1,
              "tipoAtaque": "fuerza_bruta", "confianzaTipo": 1.0, "severidad": "alta",
              "veredictos": {"ia": true, "errorReconstruccion": 0.0412, "umbralIa": 0.00306383, "umbrales": true, "contexto60s": 65, "umbralServicio": 1,
                             "servicioSinLineaBase": false, "reglas": false, "parDestino": "192.168.10.50:21", "parEnCatalogo": true},
              "estadoRevision": "sin_revisar", "historial": [], "version": 1, "actualizadaEn": "2026-10-11T14:25:10+00:00"
            }], "cursorSiguiente": "02aef296:28", "hayMas": false, "horaServidor": "2026-10-12T04:05:31+00:00"}
        """.trimIndent()

        private val CONFLICTO = """
            {"detail": {"mensaje": "Otra persona revisó esta alerta después de tu última sincronización",
                        "revisionAjena": {"resultado": "falso_positivo", "motivo": "tarea_programada", "comentario": "Era el respaldo",
                                          "autor": "Teléfono de Lucía", "fecha": "2026-10-12T05:00:00+00:00"},
                        "alerta": null}}
        """.trimIndent()
    }
}
