package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.FiltroServicio
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertasRepositoryImplTest : PruebaConBase() {

    private lateinit var alertas: AlertasRepositoryImpl
    private lateinit var acciones: AccionesPendientesRepositoryImpl

    @Before
    fun preparar() {
        alertas = AlertasRepositoryImpl(base.alertasDao(), reloj = { Fixtures.AHORA })
        acciones = AccionesPendientesRepositoryImpl(base.accionesPendientesDao())
    }

    @Test
    fun `guarda alertas del servidor y las devuelve iguales, de la mas reciente a la mas antigua`() = runTest {
        conRed()
        val vieja = Fixtures.alerta("a-1", creadaEn = Fixtures.AHORA.minus(Duration.ofHours(2)))
        val nueva = Fixtures.alerta("a-2", creadaEn = Fixtures.AHORA.minus(Duration.ofHours(1)), severidad = Severidad.MEDIA)
        alertas.guardarDelServidor(listOf(vieja, nueva))

        val lista = alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first()

        assertEquals(listOf("a-2", "a-1"), lista.map { it.id })
        assertEquals(vieja, lista[1])
    }

    @Test
    fun `los filtros de estado, severidad, metodo y periodo funcionan en la base`() = runTest {
        conRed()
        alertas.guardarDelServidor(
            listOf(
                Fixtures.alerta("alta-ia", severidad = Severidad.ALTA, metodos = setOf(MetodoDeteccion.IA)),
                Fixtures.alerta("media-reglas", severidad = Severidad.MEDIA, metodos = setOf(MetodoDeteccion.REGLAS)),
                Fixtures.alerta("alta-revisada", severidad = Severidad.ALTA, revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
                Fixtures.alerta("vieja", severidad = Severidad.BAJA, creadaEn = Fixtures.AHORA.minus(Duration.ofHours(30))),
            ),
        )

        suspend fun ids(filtro: FiltroAlertas) = alertas.observarAlertas("red-1", filtro).first().map { it.id }.toSet()

        assertEquals(setOf("alta-ia", "media-reglas", "vieja"), ids(FiltroAlertas(estado = FiltroEstado.SIN_REVISAR)))
        assertEquals(setOf("alta-ia"), ids(FiltroAlertas.sinRevisarDe(Severidad.ALTA)))
        assertEquals(setOf("alta-revisada"), ids(FiltroAlertas(estado = FiltroEstado.REVISADAS)))
        assertEquals(setOf("alta-ia", "media-reglas"), ids(FiltroAlertas(severidades = setOf(Severidad.ALTA, Severidad.MEDIA), estado = FiltroEstado.SIN_REVISAR)))
        assertEquals(setOf("media-reglas"), ids(FiltroAlertas(metodo = MetodoDeteccion.REGLAS)))
        assertEquals(setOf("alta-ia", "media-reglas", "alta-revisada"), ids(FiltroAlertas(periodo = Periodo.ULTIMAS_24_HORAS)))
        assertEquals(4, ids(FiltroAlertas.TODAS).size)
    }

    @Test
    fun `revisadas incluye los falsos positivos, como en el Figma`() = runTest {
        conRed()
        alertas.guardarDelServidor(
            listOf(
                Fixtures.alerta("real", revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
                Fixtures.alerta("falsa", revision = Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, motivo = Motivo.BACKUP, origen = OrigenRevision.SERVIDOR)),
                Fixtures.alerta("sin-revisar"),
            ),
        )

        suspend fun ids(estado: FiltroEstado) = alertas.observarAlertas("red-1", FiltroAlertas(estado = estado)).first().map { it.id }.toSet()

        assertEquals(setOf("real", "falsa"), ids(FiltroEstado.REVISADAS))
        assertEquals(setOf("falsa"), ids(FiltroEstado.FALSOS_POSITIVOS))
        assertEquals(setOf("real", "falsa", "sin-revisar"), ids(FiltroEstado.TODAS))
    }

    @Test
    fun `mas filtros ofrece los servicios y destinos de las alertas guardadas y Otros junta el resto`() = runTest {
        conRed()
        val servicios = listOf("ssh", "ssh", "ssh", "ftp", "ftp", "smb", "rdp", "http", "dns", "ldap")
        alertas.guardarDelServidor(
            servicios.mapIndexed { indice, servicio ->
                Fixtures.alerta("a-$indice", servicio = servicio, destinoIp = if (indice < 6) "192.168.10.50" else "192.168.10.77")
            },
        )

        val opciones = alertas.observarOpcionesDeFiltro("red-1").first()

        assertEquals(listOf("ssh", "ftp", "dns", "http", "ldap", "rdp", "smb"), opciones.servicios)
        assertEquals(listOf("ssh", "ftp", "dns", "http", "ldap"), opciones.serviciosConChip)
        assertTrue(opciones.hayOtrosServicios)
        assertEquals(listOf("192.168.10.50", "192.168.10.77"), opciones.destinos.map { it.ip })
        assertEquals("srv-web", opciones.destinos.first().nombre)

        val otros = alertas.observarAlertas("red-1", FiltroAlertas(servicio = FiltroServicio.Otros(opciones.serviciosConChip.toSet()))).first()
        assertEquals(setOf("rdp", "smb"), otros.map { it.servicio }.toSet())
        val ssh = alertas.observarAlertas("red-1", FiltroAlertas(servicio = FiltroServicio.Uno("ssh"))).first()
        assertEquals(3, ssh.size)
    }

    @Test
    fun `por defecto la lista muestra los ultimos 30 dias`() = runTest {
        conRed()
        alertas.guardarDelServidor(
            listOf(
                Fixtures.alerta("de-ayer", creadaEn = Fixtures.AHORA.minus(Duration.ofDays(1))),
                Fixtures.alerta("de-hace-40-dias", creadaEn = Fixtures.AHORA.minus(Duration.ofDays(40))),
            ),
        )

        assertEquals(listOf("de-ayer"), alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().map { it.id })
    }

    @Test
    fun `cuenta las alertas sin revisar por severidad`() = runTest {
        conRed()
        alertas.guardarDelServidor(
            listOf(
                Fixtures.alerta("a-1", severidad = Severidad.ALTA),
                Fixtures.alerta("a-2", severidad = Severidad.ALTA),
                Fixtures.alerta("a-3", severidad = Severidad.MEDIA),
                Fixtures.alerta("a-4", severidad = Severidad.BAJA, revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
            ),
        )

        assertEquals(ContadoresSinRevisar(altas = 2, medias = 1, bajas = 0), alertas.observarContadoresSinRevisar("red-1").first())
    }

    @Test
    fun `una revision local cambia el estado, queda en el historial y se ve como pendiente`() = runTest {
        conRed()
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1")))

        val revisada = alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, motivo = ar.edu.uade.zonda.domain.model.Motivo.TAREA_PROGRAMADA))
        acciones.encolar(Fixtures.accion(alertaId = "a-1"))

        assertEquals(EstadoRevision.FALSO_POSITIVO, revisada.estadoRevision)
        val observada = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(1, observada.historial.size)
        assertEquals(OrigenRevision.ESTE_TELEFONO, observada.revisionActual?.origen)
        assertEquals(EstadoEnvio.PENDIENTE, observada.envio)
        assertTrue(observada.pendienteDeEnvio)
    }

    @Test
    fun `deshacer vuelve al estado del servidor y borra la revision local`() = runTest {
        conRed()
        val revisionDelServidor = Fixtures.revision(ResultadoRevision.ATAQUE_REAL, fecha = Fixtures.AHORA.minus(Duration.ofHours(1)), origen = OrigenRevision.SERVIDOR)
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1", revision = revisionDelServidor)))
        alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, motivo = ar.edu.uade.zonda.domain.model.Motivo.EQUIPO_NUEVO))

        alertas.restaurarEstadoDelServidor("red-1", "a-1")

        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.ATAQUE_REAL, alerta.estadoRevision)
        assertEquals(listOf(revisionDelServidor), alerta.historial)
        assertNull(alerta.envio)
    }

    @Test
    fun `una actualizacion del servidor no pisa una revision local pendiente`() = runTest {
        conRed()
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1")))
        alertas.aplicarRevisionLocal("red-1", "a-1", Fixtures.revision(ResultadoRevision.ATAQUE_REAL))

        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1").copy(version = 2)))

        val alerta = alertas.observarAlerta("red-1", "a-1").first()!!
        assertEquals(EstadoRevision.ATAQUE_REAL, alerta.estadoRevision)
        assertEquals(2, alerta.version)
        assertEquals(1, alerta.historial.size)
    }

    @Test
    fun `al borrar la red se van sus alertas y sus acciones pendientes`() = runTest {
        conRed()
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1")))
        acciones.encolar(Fixtures.accion(alertaId = "a-1"))

        base.redesDao().borrar("red-1")

        assertTrue(alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().isEmpty())
        assertTrue(acciones.observar("red-1").first().isEmpty())
    }

    @Test
    fun `la purga borra las alertas anteriores a la fecha dada`() = runTest {
        conRed()
        alertas.guardarDelServidor(
            listOf(
                Fixtures.alerta("reciente"),
                Fixtures.alerta("vieja", creadaEn = Fixtures.AHORA.minus(Duration.ofDays(40))),
            ),
        )

        val borradas = alertas.purgarAnterioresA("red-1", Fixtures.AHORA.minus(Duration.ofDays(30)))

        assertEquals(1, borradas)
        assertEquals(listOf("reciente"), alertas.observarAlertas("red-1", FiltroAlertas.TODAS).first().map { it.id })
    }
}
