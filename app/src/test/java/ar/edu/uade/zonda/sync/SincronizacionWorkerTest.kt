package ar.edu.uade.zonda.sync

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakePreferenciasRepository
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.notificaciones.Notificador
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.navigation.EnlacesInternos
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** El worker de sincronización en segundo plano corrido a mano, con repositorios falsos y las notificaciones de Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SincronizacionWorkerTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private var redes = FakeRedesRepository(
        listOf(Fixtures.red("red-1"), Fixtures.red("red-2", activa = false), Fixtures.red("red-3", activa = false, accesoVencido = true)),
    )
    private val alertas = FakeAlertasRepository()
    private val preferencias = FakePreferenciasRepository(primerInicioHecho = true)
    private val notificador = Notificador(contexto).also { it.crearCanales() }

    private fun worker(): SincronizacionWorker {
        val fabrica = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                SincronizacionWorker(appContext, workerParameters, redes, alertas, preferencias, notificador, reloj = { Fixtures.AHORA })
        }
        return TestListenableWorkerBuilder.from(contexto, SincronizacionWorker::class.java).setWorkerFactory(fabrica).build()
    }

    private val notificaciones: List<Notification>
        get() = shadowOf(contexto.getSystemService(NotificationManager::class.java)).allNotifications

    /** Las notificaciones de cada red, sin el resumen del grupo. */
    private val avisos: List<Notification>
        get() = notificaciones.filter { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }

    private fun destinoDe(notificacion: Notification): String? = shadowOf(notificacion.contentIntent).savedIntent.data?.toString()

    private fun resultado(
        nuevas: Int,
        altasNuevas: Int,
        idsAltasNuevas: List<String> = emptyList(),
        conAvisoTelegram: Set<String> = emptySet(),
    ) = ResultadoSincronizacion(
        nuevas = nuevas, modificadas = 0, altasNuevas = altasNuevas, accionesEnviadas = 0, accionesConError = 0,
        hora = Fixtures.AHORA, idsAltasNuevas = idsAltasNuevas, idsAltasNuevasConAvisoTelegram = conAvisoTelegram,
    )

    private fun textoDe(notificacion: Notification): String? = notificacion.extras.getString(Notification.EXTRA_TEXT)

    @Test
    fun `sincroniza todas las redes menos las de acceso vencido y notifica las altas nuevas de cada una`() = runTest {
        alertas.resultadoSincronizacion = resultado(nuevas = 5, altasNuevas = 2, idsAltasNuevas = listOf("a-1", "a-4"))

        val resultado = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals(listOf("red-1", "red-2"), alertas.sincronizaciones)
        // Una notificación por red, en el único canal, con la severidad dicha con palabras.
        assertEquals(2, avisos.size)
        assertTrue(avisos.all { it.extras.getString(Notification.EXTRA_TEXT) == "2 alertas nuevas de severidad alta" })
        assertTrue(avisos.all { it.extras.getString(Notification.EXTRA_TITLE) == "Zonda · Oficina" })
        assertTrue(notificaciones.all { it.channelId == Notificador.CANAL_ALERTAS })
        // Cada una abre Alertas de su red filtrada en "Alta · Sin revisar"; el resumen del grupo, Alertas.
        assertEquals(
            setOf(
                EnlacesInternos.alertas("red-1", EstadoRevision.SIN_REVISAR, Severidad.ALTA),
                EnlacesInternos.alertas("red-2", EstadoRevision.SIN_REVISAR, Severidad.ALTA),
            ),
            avisos.map(::destinoDe).toSet(),
        )
        val resumen = notificaciones.single { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
        assertEquals(EnlacesInternos.ALERTAS, destinoDe(resumen))
        assertTrue(notificaciones.all { it.group == Notificador.GRUPO })
    }

    @Test
    fun `con una sola alta nueva la notificacion abre su detalle`() = runTest {
        alertas.resultadoSincronizacion = resultado(nuevas = 3, altasNuevas = 1, idsAltasNuevas = listOf("a-7"))

        worker().doWork()

        assertTrue(avisos.all { it.extras.getString(Notification.EXTRA_TEXT) == "1 alerta nueva de severidad alta" })
        assertEquals(
            setOf(EnlacesInternos.detalle("red-1", "a-7"), EnlacesInternos.detalle("red-2", "a-7")),
            avisos.map(::destinoDe).toSet(),
        )
    }

    @Test
    fun `sin altas nuevas o con el interruptor apagado no notifica`() = runTest {
        alertas.resultadoSincronizacion = resultado(nuevas = 4, altasNuevas = 0)
        worker().doWork()
        assertTrue(notificaciones.isEmpty())

        preferencias.notificaciones.value = false
        alertas.resultadoSincronizacion = resultado(nuevas = 2, altasNuevas = 2, idsAltasNuevas = listOf("a-1", "a-2"))
        worker().doWork()
        assertTrue(notificaciones.isEmpty())
        // Aunque no notifique, sincroniza igual.
        assertEquals(listOf("red-1", "red-2", "red-1", "red-2"), alertas.sincronizaciones)
    }

    @Test
    fun `sin servidor pide reintentar y con el acceso vencido no`() = runTest {
        alertas.errorAlSincronizar = ErrorZonda.SinConexion()
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())

        alertas.errorAlSincronizar = ErrorZonda.NoAutorizado()
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(notificaciones.isEmpty())
    }

    @Test
    fun `con Telegram vinculado no repite las altas que Telegram ya aviso en este telefono`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1").copy(telegramVinculado = true)))
        alertas.resultadoSincronizacion = resultado(nuevas = 2, altasNuevas = 2, idsAltasNuevas = listOf("a-1", "a-2"), conAvisoTelegram = setOf("a-1"))

        worker().doWork()

        assertEquals(1, avisos.size)
        assertEquals("1 alerta nueva de severidad alta", textoDe(avisos.single()))
        assertEquals(EnlacesInternos.detalle("red-1", "a-2"), destinoDe(avisos.single()))
    }

    @Test
    fun `si Telegram ya aviso todas, Zonda no notifica, y con las altas en Sin aviso en Telegram avisa todas`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1").copy(telegramVinculado = true)))
        alertas.resultadoSincronizacion = resultado(nuevas = 2, altasNuevas = 2, idsAltasNuevas = listOf("a-1", "a-2"), conAvisoTelegram = setOf("a-1", "a-2"))
        worker().doWork()
        assertTrue(notificaciones.isEmpty())

        preferencias.avisos.value = PreferenciasAvisos(alta = ModoAviso.SIN_AVISO)
        worker().doWork()
        assertEquals("2 alertas nuevas de severidad alta", textoDe(avisos.single()))
    }

    @Test
    fun `recuerda una sola vez las altas que siguen sin revisar a los 15 minutos`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        val llegada = Fixtures.AHORA.minus(Duration.ofMinutes(20))
        alertas.agregar(Fixtures.alerta("a-9", creadaEn = llegada), Fixtures.alerta("a-10", creadaEn = Fixtures.AHORA.minus(Duration.ofMinutes(5))))

        worker().doWork()

        val recordatorio = avisos.single()
        assertEquals("Recordatorio: 1 alerta alta sin revisar desde las ${Formato.hora(llegada)}", textoDe(recordatorio))
        assertEquals(EnlacesInternos.detalle("red-1", "a-9"), destinoDe(recordatorio))
        assertEquals(llegada, preferencias.recordatorios["red-1"])

        // La siguiente vuelta no la recuerda de nuevo; la de hace 5 minutos todavía espera.
        contexto.getSystemService(NotificationManager::class.java).cancelAll()
        worker().doWork()
        assertTrue(notificaciones.isEmpty())
    }

    @Test
    fun `el recordatorio no tapa la notificacion de alertas nuevas`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        alertas.agregar(Fixtures.alerta("a-9", creadaEn = Fixtures.AHORA.minus(Duration.ofMinutes(30))))
        alertas.resultadoSincronizacion = resultado(nuevas = 1, altasNuevas = 1, idsAltasNuevas = listOf("a-12"))

        worker().doWork()

        assertEquals(
            setOf("1 alerta nueva de severidad alta", "Recordatorio: 1 alerta alta sin revisar desde las ${Formato.hora(Fixtures.AHORA.minus(Duration.ofMinutes(30)))}"),
            avisos.map(::textoDe).toSet(),
        )
    }
}
