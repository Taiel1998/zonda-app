package ar.edu.uade.zonda.data.sync

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.DtoFixtures
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.data.remote.errorHttp
import ar.edu.uade.zonda.data.repository.AccionesPendientesRepositoryImpl
import ar.edu.uade.zonda.data.repository.AlertasRepositoryImpl
import ar.edu.uade.zonda.data.repository.RedesRepositoryImpl
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.ResolverEnvioRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La sincronización completa sobre Room en memoria y la API en memoria, sin HTTP. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SincronizadorHttpTest : PruebaConBase() {

    private val api = ApiFalsa()
    private val almacen = FakeAlmacenSeguro()
    private lateinit var alertas: AlertasRepositoryImpl
    private lateinit var redes: RedesRepositoryImpl
    private lateinit var acciones: AccionesPendientesRepositoryImpl
    private var contadorDeIds = 0
    private val generador = GeneradorDeIds { "accion-${++contadorDeIds}" }

    @Before
    fun preparar() {
        val proveedor = ProveedorDeApiFalso(api)
        val sincronizador = SincronizadorHttp(base, proveedor, reloj = { Fixtures.AHORA })
        alertas = AlertasRepositoryImpl(base.alertasDao(), sincronizador, reloj = { Fixtures.AHORA })
        redes = RedesRepositoryImpl(base.redesDao(), almacen, proveedor, reloj = { Fixtures.AHORA })
        acciones = AccionesPendientesRepositoryImpl(base.accionesPendientesDao())
    }

    /** Cinco alertas en el servidor: dos altas (fuerza bruta), dos medias (escaneo) y una baja. */
    private fun publicarCinco() {
        listOf("a-1" to "alta", "a-2" to "media", "a-3" to "baja", "a-4" to "alta", "a-5" to "media").forEach { (id, severidad) ->
            val tipo = when (severidad) {
                "alta" -> "fuerza_bruta"
                "media" -> "escaneo"
                else -> "probable_falsa_alarma"
            }
            api.publicar(DtoFixtures.alerta(id, severidad = severidad, tipoAtaque = tipo))
        }
    }

    private suspend fun revisar(alertaId: String): AccionPendiente =
        RevisarAlerta(alertas, acciones, generador, reloj = { Fixtures.AHORA })(
            "red-1", alertaId, ResultadoRevision.ATAQUE_REAL, Motivo.ESCALADO_AL_PROVEEDOR, "Escalado al proveedor",
        )

    @Test
    fun `la primera sincronizacion baja todas las paginas, guarda el cursor y cuenta nuevas y altas`() = runTest {
        conRed()
        publicarCinco()
        api.tamanioPagina = 2
        api.severidades["escaneo"] = "alta"

        val resultado = alertas.sincronizar("red-1")

        assertEquals(5, resultado.nuevas)
        assertEquals(0, resultado.modificadas)
        assertEquals(2, resultado.altasNuevas)
        assertEquals(0, resultado.accionesEnviadas)
        assertEquals(0, resultado.accionesConError)
        assertEquals(Fixtures.AHORA, resultado.hora)
        val lista = alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first()
        assertEquals(5, lista.size)
        assertTrue(lista.all { it.historial.isEmpty() && it.envio == null && it.redId == "red-1" })
        val red = redes.observarRedActiva().first()!!
        assertEquals("inst1:5", red.cursor)
        assertEquals(Fixtures.AHORA, red.ultimaSincronizacion)
        assertEquals(mapOf("192.168.10.50" to "srv-web", "192.168.10.9" to "pc-09"), redes.observarCatalogo("red-1").first().equipos)
        assertEquals(listOf("smb", "ssh"), redes.observarCatalogo("red-1").first().servicios)
        assertEquals(Severidad.ALTA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.ESCANEO))
    }

    @Test
    fun `la segunda sincronizacion trae solo lo que cambio desde el cursor`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        api.revisarDesdeOtroTelefono("a-1")
        api.publicar(DtoFixtures.alerta("a-6", severidad = "alta"))

        val resultado = alertas.sincronizar("red-1")

        assertEquals(1, resultado.nuevas)
        assertEquals(1, resultado.modificadas)
        assertEquals(1, resultado.altasNuevas)
        val a1 = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.FALSO_POSITIVO, a1.estadoRevision)
        assertEquals(2, a1.version)
        assertEquals("Teléfono de Lucía", a1.revisionActual?.autor)
        assertEquals(OrigenRevision.SERVIDOR, a1.revisionActual?.origen)
        assertEquals("inst1:7", redes.observarRedActiva().first()?.cursor)
        assertEquals(6, alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().size)
    }

    @Test
    fun `envia la revision pendiente antes de bajar y la deja confirmada por el servidor`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        val accion = revisar("a-2")
        assertEquals(EstadoEnvio.PENDIENTE, alertas.observarAlerta("red-1", "a-2").first()?.envio)

        val resultado = alertas.sincronizar("red-1")

        assertEquals(1, resultado.accionesEnviadas)
        assertEquals(0, resultado.accionesConError)
        assertEquals(0, resultado.nuevas)
        assertEquals(1, resultado.modificadas)
        assertTrue(acciones.observar("red-1").first().isEmpty())
        val a2 = alertas.observarAlerta("red-1", "a-2").first()!!
        assertEquals(EstadoRevision.ATAQUE_REAL, a2.estadoRevision)
        assertNull(a2.envio)
        assertEquals(2, a2.version)
        assertEquals(listOf(OrigenRevision.SERVIDOR), a2.historial.map { it.origen })
        assertEquals("Este teléfono", a2.revisionActual?.autor)
        val recibida = api.revisionesRecibidas.single()
        assertEquals(accion.id, recibida.accionId)
        assertEquals(1, recibida.versionVista)
        assertEquals("ataque_real", recibida.resultado)
        assertEquals("escalado_al_proveedor", recibida.motivo)
        assertFalse(recibida.forzar)
    }

    @Test
    fun `un 409 deja la accion en conflicto con la revision ajena y no frena a las demas`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        val enConflicto = revisar("a-1")
        revisar("a-3")
        api.revisarDesdeOtroTelefono("a-1")

        val resultado = alertas.sincronizar("red-1")

        assertEquals(1, resultado.accionesEnviadas)
        assertEquals(0, resultado.accionesConError)
        val pendiente = acciones.observar("red-1").first().single()
        assertEquals(enConflicto.id, pendiente.id)
        assertEquals(EstadoEnvio.CONFLICTO, pendiente.estado)
        assertEquals("Teléfono de Lucía", pendiente.revisionAjena?.autor)
        assertEquals(Motivo.TAREA_PROGRAMADA, pendiente.revisionAjena?.motivo)
        val a1 = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoEnvio.CONFLICTO, a1.envio)
        assertFalse(a1.pendienteDeEnvio)
        // Hasta que la persona decida, lo que eligió en este teléfono sigue mandando sobre el estado...
        assertEquals(EstadoRevision.ATAQUE_REAL, a1.estadoRevision)
        // ...y la revisión de la otra persona ya está en el historial.
        assertEquals("Teléfono de Lucía", a1.historial.first().autor)
        assertEquals(2, a1.historial.size)
    }

    @Test
    fun `un error del servidor cuenta un intento y a los cinco la accion queda en error hasta el reintento manual`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        val accion = revisar("a-1")
        api.errores["revisar"] = errorHttp(503)

        repeat(4) { assertEquals(1, alertas.sincronizar("red-1").accionesConError) }
        var guardada = acciones.deAlerta("red-1", "a-1")!!
        assertEquals(EstadoEnvio.PENDIENTE, guardada.estado)
        assertEquals(4, guardada.intentos)

        alertas.sincronizar("red-1")
        guardada = acciones.deAlerta("red-1", "a-1")!!
        assertEquals(EstadoEnvio.ERROR, guardada.estado)
        assertEquals(5, guardada.intentos)
        assertEquals("Error del servidor", guardada.ultimoError)
        assertEquals(EstadoEnvio.ERROR, alertas.observarAlerta("red-1", "a-1").first()?.envio)

        // En error no se reintenta sola, aunque el servidor ya responda...
        api.errores.clear()
        assertEquals(0, alertas.sincronizar("red-1").accionesEnviadas)
        // ...hasta que la persona toca Reintentar.
        acciones.reintentar(accion.id)
        assertEquals(1, alertas.sincronizar("red-1").accionesEnviadas)
        assertNull(acciones.deAlerta("red-1", "a-1"))
    }

    @Test
    fun `si el servidor rechaza los datos la accion queda en error al primer intento`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        revisar("a-1")
        api.errores["revisar"] = errorHttp(422)

        assertEquals(1, alertas.sincronizar("red-1").accionesConError)

        val guardada = acciones.deAlerta("red-1", "a-1")!!
        assertEquals(EstadoEnvio.ERROR, guardada.estado)
        assertEquals(1, guardada.intentos)
    }

    @Test
    fun `sin conexion la accion vuelve a pendiente sin sumar intentos y la sincronizacion falla`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        revisar("a-1")
        api.errores["revisar"] = IOException("sin red")

        val error = runCatching { alertas.sincronizar("red-1") }.exceptionOrNull()

        assertTrue(error is ErrorZonda.SinConexion)
        val guardada = acciones.deAlerta("red-1", "a-1")!!
        assertEquals(EstadoEnvio.PENDIENTE, guardada.estado)
        assertEquals(0, guardada.intentos)
    }

    @Test
    fun `un 401 corta la sincronizacion y conserva la cache y el cursor`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        api.errores["alertas"] = errorHttp(401)

        val error = runCatching { alertas.sincronizar("red-1") }.exceptionOrNull()

        assertTrue(error is ErrorZonda.NoAutorizado)
        assertEquals(5, alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().size)
        assertEquals("inst1:5", redes.observarRedActiva().first()?.cursor)
    }

    @Test
    fun `un cursor de otra instalacion dispara una sincronizacion completa que conserva lo pendiente`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        revisar("a-1")
        api.errores["revisar"] = errorHttp(503) // la acción no sale y queda pendiente
        api.instancia = "inst2" // el servidor se reinstaló

        val resultado = alertas.sincronizar("red-1")

        assertEquals(0, resultado.nuevas)
        assertEquals(5, resultado.modificadas)
        assertEquals("inst2:5", redes.observarRedActiva().first()?.cursor)
        assertEquals(EstadoEnvio.PENDIENTE, acciones.deAlerta("red-1", "a-1")?.estado)
        assertEquals(EstadoRevision.ATAQUE_REAL, alertas.observarAlerta("red-1", "a-1").first()?.estadoRevision)
    }

    @Test
    fun `purga las alertas de mas de 30 dias despues de sincronizar`() = runTest {
        conRed()
        api.publicar(DtoFixtures.alerta("vieja", inicio = "2026-08-20T10:00:00+00:00", fin = "2026-08-20T10:05:00+00:00", creadaEn = "2026-08-20T10:10:00+00:00"))
        api.publicar(DtoFixtures.alerta("reciente"))

        val resultado = alertas.sincronizar("red-1")

        assertEquals(2, resultado.nuevas)
        assertEquals(listOf("reciente"), alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().map { it.id })
    }

    @Test
    fun `descargarDetalle trae una alerta que todavia no estaba en el telefono`() = runTest {
        conRed()
        api.publicar(DtoFixtures.alerta("a-9", severidad = "media", tipoAtaque = "escaneo"))
        assertNull(alertas.observarAlerta("red-1", "a-9").first())

        val alerta = alertas.descargarDetalle("red-1", "a-9")

        assertEquals("a-9", alerta.id)
        assertEquals(TipoAtaque.ESCANEO, alerta.tipoAtaque)
        assertEquals("a-9", alertas.observarAlerta("red-1", "a-9").first()?.id)
        assertTrue(runCatching { alertas.descargarDetalle("red-1", "no-existe") }.exceptionOrNull() is ErrorZonda.NoEncontrado)
    }

    @Test
    fun `cambiar severidades se envia con PUT y todas las alertas vuelven con la severidad nueva`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        CambiarSeveridades(redes, acciones, generador, reloj = { Fixtures.AHORA })(
            "red-1", SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA),
        )

        val resultado = alertas.sincronizar("red-1")

        assertEquals(1, resultado.accionesEnviadas)
        assertEquals("alta", api.severidades["escaneo"])
        assertEquals(5, resultado.modificadas)
        assertEquals(Severidad.ALTA, redes.observarSeveridades("red-1").first().severidadDe(TipoAtaque.ESCANEO))
        assertEquals(Severidad.ALTA, alertas.observarAlerta("red-1", "a-2").first()?.severidad)
        assertTrue(acciones.observar("red-1").first().isEmpty())
    }

    @Test
    fun `aplicar la mia igual despues de un 409 la envia con la version vista y el servidor la acepta`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        val accion = revisar("a-1")
        api.revisarDesdeOtroTelefono("a-1")
        alertas.sincronizar("red-1") // 409: la acción queda en conflicto y baja la revisión de Lucía (versión 2)

        ResolverEnvioRevision(alertas, acciones).aplicarLaMiaIgual("red-1", "a-1")
        val resultado = alertas.sincronizar("red-1")

        assertEquals(1, resultado.accionesEnviadas)
        val enviada = api.revisionesRecibidas.last()
        assertEquals(accion.id, enviada.accionId)
        assertEquals(2, enviada.versionVista)
        assertFalse(enviada.forzar)
        assertTrue(acciones.observar("red-1").first().isEmpty())
        val a1 = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.ATAQUE_REAL, a1.estadoRevision)
        assertEquals(3, a1.version)
        assertNull(a1.envio)
        assertEquals(listOf("Teléfono de Lucía", "Este teléfono"), a1.historial.map { it.autor })
        assertTrue(a1.historial.all { it.origen == OrigenRevision.SERVIDOR })
    }

    @Test
    fun `quedarme con la otra despues de un 409 deja la revision de la otra persona y no envia nada`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        revisar("a-1")
        api.revisarDesdeOtroTelefono("a-1")
        alertas.sincronizar("red-1")
        val recibidas = api.revisionesRecibidas.size

        ResolverEnvioRevision(alertas, acciones).quedarmeConLaOtra("red-1", "a-1")
        alertas.sincronizar("red-1")

        assertEquals(recibidas, api.revisionesRecibidas.size)
        val a1 = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.FALSO_POSITIVO, a1.estadoRevision)
        assertEquals("Teléfono de Lucía", a1.revisionActual?.autor)
        assertEquals(1, a1.historial.size)
        assertNull(a1.envio)
    }

    @Test
    fun `dos sincronizaciones a la vez no mandan la misma accion dos veces`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        revisar("a-2")

        val resultados = listOf(async { alertas.sincronizar("red-1") }, async { alertas.sincronizar("red-1") }).awaitAll()

        assertEquals(1, api.revisionesRecibidas.size)
        assertEquals(1, resultados.sumOf { it.accionesEnviadas })
    }

    @Test
    fun `cuenta las nuevas por severidad para la notificacion`() = runTest {
        conRed()
        publicarCinco()

        val resultado = alertas.sincronizar("red-1")

        assertEquals(2, resultado.altasNuevas)
        assertEquals(2, resultado.mediasNuevas)
        assertEquals(1, resultado.bajasNuevas)
    }

    @Test
    fun `un 401 deja la red con el acceso vencido y un pareo nuevo o una sincronizacion que anda lo limpian`() = runTest {
        conRed()
        publicarCinco()
        alertas.sincronizar("red-1")
        api.errores["alertas"] = errorHttp(401)

        assertTrue(runCatching { alertas.sincronizar("red-1") }.exceptionOrNull() is ErrorZonda.NoAutorizado)
        assertTrue(redes.observarRedActiva().first()!!.accesoVencido)

        api.errores.clear()
        alertas.sincronizar("red-1")
        assertFalse(redes.observarRedActiva().first()!!.accesoVencido)

        redes.marcarAccesoVencido("red-1", true)
        redes.guardarRedPareada(Fixtures.red("red-1"), "token-nuevo")
        assertFalse(redes.observarRedActiva().first()!!.accesoVencido)
        assertEquals(5, alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().size)
    }

    @Test
    fun `sincronizar una red que no esta pareada es NoEncontrado`() = runTest {
        assertTrue(runCatching { alertas.sincronizar("red-x") }.exceptionOrNull() is ErrorZonda.NoEncontrado)
    }
}
