package ar.edu.uade.zonda.data.sync

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.AsignacionDto
import ar.edu.uade.zonda.data.remote.CambioRegistroDto
import ar.edu.uade.zonda.data.remote.DtoFixtures
import ar.edu.uade.zonda.data.remote.PersonaDto
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.data.remote.errorHttp
import ar.edu.uade.zonda.data.repository.AccionesPendientesRepositoryImpl
import ar.edu.uade.zonda.data.repository.AlertasRepositoryImpl
import ar.edu.uade.zonda.data.repository.RedesRepositoryImpl
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.TipoCambio
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.usecase.AsignarAlerta
import ar.edu.uade.zonda.domain.usecase.CambiarImportancia
import java.io.IOException
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

/**
 * La sincronización de lo que suma la 1.0.5: la gente y su perfil (decisión 39), el registro de cambios
 * (decisión 40), la importancia de cada equipo (decisión 41) y las asignaciones (decisión 42).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SincronizacionDelEquipoTest : PruebaConBase() {

    private val api = ApiFalsa()
    private val almacen = FakeAlmacenSeguro()
    private lateinit var alertas: AlertasRepositoryImpl
    private lateinit var redes: RedesRepositoryImpl
    private lateinit var acciones: AccionesPendientesRepositoryImpl
    private var contadorDeIds = 0
    private val generador = GeneradorDeIds { "accion-${++contadorDeIds}" }

    private val laura = PersonaDto("d-2", "Laura", "Pixel 7", "analista", null, vos = false)

    @Before
    fun preparar() {
        val proveedor = ProveedorDeApiFalso(api)
        val sincronizador = SincronizadorHttp(base, proveedor, reloj = { Fixtures.AHORA })
        alertas = AlertasRepositoryImpl(base.alertasDao(), sincronizador, reloj = { Fixtures.AHORA })
        redes = RedesRepositoryImpl(base.redesDao(), almacen, proveedor, reloj = { Fixtures.AHORA })
        acciones = AccionesPendientesRepositoryImpl(base.accionesPendientesDao())
    }

    private suspend fun persona(id: String): Persona = redes.observarPersonas("red-1").first().first { it.id == id }

    private suspend fun responsable(alertaId: String = "a-1"): String? =
        alertas.observarAlerta("red-1", alertaId).first()?.asignacion?.responsableId

    private val asignar get() = AsignarAlerta(redes, alertas, acciones, generador, reloj = { Fixtures.AHORA })

    // ---- Personas y registro ----

    @Test
    fun `la sincronizacion trae la gente y el registro y deja al dia el perfil de este telefono`() = runTest {
        conRed()
        api.personas[0] = api.personas[0].copy(nombre = "Taiel", perfil = "analista")
        api.personas += laura.copy(perfil = "administrador")
        api.registro += CambioRegistroDto(
            1, "2026-10-12T05:00:00+00:00", "severidad", "d-2", "Laura", "fuerza_bruta", null, "alta", "media",
            "El backup de las 3 dispara falsas alarmas.",
        )

        alertas.sincronizar("red-1")

        val red = redes.observarRedActiva().first()!!
        assertEquals(Perfil.ANALISTA, red.perfil)
        assertEquals("d-1", red.dispositivoId)
        assertEquals(listOf("Taiel", "Laura"), redes.observarPersonas("red-1").first().map { it.nombreVisible })
        assertTrue(persona("d-1").vos)
        val cambio = redes.observarRegistro("red-1").first().single()
        assertEquals(TipoCambio.SEVERIDAD, cambio.tipo)
        assertEquals(TipoAtaque.FUERZA_BRUTA, cambio.tipoDeAtaque)
        assertEquals("El backup de las 3 dispara falsas alarmas.", cambio.motivo)
    }

    @Test
    fun `con un servidor anterior sin personas la red queda como antes`() = runTest {
        conRed()
        api.errores["personas"] = errorHttp(404)
        api.publicar(DtoFixtures.alerta("a-1"))

        val resultado = alertas.sincronizar("red-1")

        assertEquals(1, resultado.nuevas)
        assertEquals(Perfil.ADMINISTRADOR, redes.observarRedActiva().first()!!.perfil)
        assertTrue(redes.observarPersonas("red-1").first().isEmpty())
    }

    @Test
    fun `quitar este telefono si deja al equipo sin administradores no borra la red`() = runTest {
        conRed()
        api.errores["quitar"] = errorHttp(409, """{"detail": "Tiene que quedar al menos un administrador en el equipo"}""")

        val error = runCatching { redes.quitarDispositivo("red-1") }.exceptionOrNull()

        assertTrue(error is ErrorZonda.UltimoAdministrador)
        assertNotNull(redes.observarRedActiva().first())
    }

    // ---- Importancia de los equipos ----

    @Test
    fun `la importancia se envia con su motivo y la alerta vuelve con un nivel mas`() = runTest {
        conRed()
        api.publicar(DtoFixtures.alerta("a-1", severidad = "media", tipoAtaque = "escaneo", destinoIp = "192.168.10.50"))
        alertas.sincronizar("red-1")

        CambiarImportancia(redes, acciones, generador, reloj = { Fixtures.AHORA })("red-1", "192.168.10.50", Importancia.CRITICO, "Ahí están los sueldos.")
        assertEquals(Importancia.CRITICO, redes.observarEquipos("red-1").first().first { it.ip == "192.168.10.50" }.importancia)
        val resultado = alertas.sincronizar("red-1")

        assertEquals(1, resultado.accionesEnviadas)
        assertEquals("critico", api.importancias["192.168.10.50"])
        assertEquals(listOf("Ahí están los sueldos."), api.motivosRecibidos)
        assertEquals(Severidad.ALTA, alertas.observarAlerta("red-1", "a-1").first()?.severidad)
        assertEquals(Importancia.CRITICO, redes.observarEquipos("red-1").first().first { it.ip == "192.168.10.50" }.importancia)
        assertTrue(acciones.observar("red-1").first().isEmpty())
    }

    @Test
    fun `un cambio de importancia que todavia no se envio no lo pisa el catalogo`() = runTest {
        conRed()
        alertas.sincronizar("red-1")
        CambiarImportancia(redes, acciones, generador, reloj = { Fixtures.AHORA })("red-1", "192.168.10.9", Importancia.POCO_IMPORTANTE, "Es la impresora.")
        api.errores["importancia"] = errorHttp(500)

        alertas.sincronizar("red-1")

        assertEquals(Importancia.POCO_IMPORTANTE, redes.observarEquipos("red-1").first().first { it.ip == "192.168.10.9" }.importancia)
        assertEquals(1, acciones.observar("red-1").first().size)
    }

    @Test
    fun `la importancia que cambio otra persona llega con la sincronizacion`() = runTest {
        conRed()
        api.importancias["192.168.10.50"] = "critico"

        alertas.sincronizar("red-1")

        val equipos = redes.observarEquipos("red-1").first().associate { it.ip to it.importancia }
        assertEquals(mapOf("192.168.10.9" to Importancia.NORMAL, "192.168.10.50" to Importancia.CRITICO), equipos)
    }

    // ---- Asignaciones ----

    @Test
    fun `una asignacion se ve al instante y al sincronizar queda la del servidor`() = runTest {
        conRed()
        api.personas += laura
        api.publicar(DtoFixtures.alerta("a-1"))
        alertas.sincronizar("red-1")

        asignar("red-1", "a-1", persona("d-2"))
        assertEquals("d-2", responsable())
        alertas.sincronizar("red-1")

        assertEquals("d-2", api.asignacionesRecibidas.single().responsableId)
        val asignacion = alertas.observarAlerta("red-1", "a-1").first()?.asignacion
        assertEquals("Laura", asignacion?.responsable)
        assertEquals("d-1", asignacion?.porId)
        assertEquals("d-2", api.alertaGuardada("a-1").asignacion?.responsableId)
        assertTrue(base.accionesPendientesDao().asignacionesParaEnviar("red-1").isEmpty())
    }

    @Test
    fun `si dos asignan a la vez queda la ultima que llega al servidor`() = runTest {
        conRed()
        api.personas += laura
        api.publicar(DtoFixtures.alerta("a-1"))
        alertas.sincronizar("red-1")

        asignar("red-1", "a-1", persona("d-2"))
        asignar("red-1", "a-1", persona("d-1"))
        alertas.sincronizar("red-1")

        // En la cola queda una por alerta: la última de este teléfono.
        assertEquals(listOf("d-1"), api.asignacionesRecibidas.map { it.responsableId })
        assertEquals("d-1", responsable())
    }

    @Test
    fun `si el servidor rechaza la asignacion la alerta vuelve al responsable que dice el servidor`() = runTest {
        conRed()
        api.personas += laura
        api.publicar(DtoFixtures.alerta("a-1"))
        alertas.sincronizar("red-1")
        asignar("red-1", "a-1", persona("d-2"))
        api.errorAlAsignar = errorHttp(409, """{"detail": "Ya está asignada a otra persona"}""")

        alertas.sincronizar("red-1")

        assertNull(responsable())
        assertTrue(base.accionesPendientesDao().asignacionesParaEnviar("red-1").isEmpty())
    }

    @Test
    fun `sin conexion la asignacion espera en la cola`() = runTest {
        conRed()
        api.personas += laura
        api.publicar(DtoFixtures.alerta("a-1"))
        alertas.sincronizar("red-1")
        asignar("red-1", "a-1", persona("d-2"))
        api.errores["asignar"] = IOException("sin red")

        assertTrue(runCatching { alertas.sincronizar("red-1") }.exceptionOrNull() is ErrorZonda.SinConexion)

        assertEquals("d-2", responsable())
        assertEquals(1, base.accionesPendientesDao().asignacionesParaEnviar("red-1").size)
        api.errores.remove("asignar")
        alertas.sincronizar("red-1")
        assertEquals("d-2", api.alertaGuardada("a-1").asignacion?.responsableId)
    }

    @Test
    fun `una alerta que otra persona me asigno avisa una sola vez y asignarse una no avisa`() = runTest {
        conRed()
        api.personas += laura.copy(perfil = "administrador")
        val deLaura = AsignacionDto("d-1", "Este teléfono", "d-2", "Laura", "2026-10-12T06:00:00+00:00")
        api.publicar(DtoFixtures.alerta("a-1").copy(asignacion = deLaura, asignaciones = listOf(deLaura)))
        val propia = AsignacionDto("d-1", "Este teléfono", "d-1", "Este teléfono", "2026-10-12T06:00:00+00:00")
        api.publicar(DtoFixtures.alerta("a-2").copy(asignacion = propia, asignaciones = listOf(propia)))

        val primera = alertas.sincronizar("red-1")

        assertEquals(listOf("a-1"), primera.asignacionesNuevas.map { it.alertaId })
        assertEquals("Laura", primera.asignacionesNuevas.single().por)

        // La misma alerta vuelve por otro motivo (la revisaron): no avisa de nuevo.
        api.revisarDesdeOtroTelefono("a-1")
        assertTrue(alertas.sincronizar("red-1").asignacionesNuevas.isEmpty())

        // Se la vuelven a asignar más tarde: avisa otra vez.
        val otraVez = AsignacionDto("d-1", "Este teléfono", "d-2", "Laura", "2026-10-12T08:00:00+00:00")
        val actual = api.alertaGuardada("a-1")
        api.publicar(actual.copy(asignacion = otraVez, asignaciones = actual.asignaciones.orEmpty() + otraVez))
        assertEquals(listOf("a-1"), alertas.sincronizar("red-1").asignacionesNuevas.map { it.alertaId })
    }
}
