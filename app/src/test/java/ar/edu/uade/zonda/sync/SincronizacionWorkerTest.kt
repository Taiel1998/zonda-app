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
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.notificaciones.Notificador
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
    private val redes = FakeRedesRepository(
        listOf(Fixtures.red("red-1"), Fixtures.red("red-2", activa = false), Fixtures.red("red-3", activa = false, accesoVencido = true)),
    )
    private val alertas = FakeAlertasRepository()
    private val preferencias = FakePreferenciasRepository(primerInicioHecho = true)
    private val notificador = Notificador(contexto).also { it.crearCanales() }

    private fun worker(): SincronizacionWorker {
        val fabrica = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                SincronizacionWorker(appContext, workerParameters, redes, alertas, preferencias, notificador)
        }
        return TestListenableWorkerBuilder.from(contexto, SincronizacionWorker::class.java).setWorkerFactory(fabrica).build()
    }

    private val notificaciones: List<Notification>
        get() = shadowOf(contexto.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun `sincroniza todas las redes menos las de acceso vencido y notifica las nuevas segun la preferencia`() = runTest {
        alertas.resultadoSincronizacion = ResultadoSincronizacion(
            nuevas = 3, modificadas = 0, altasNuevas = 2, accionesEnviadas = 0, accionesConError = 0, hora = Fixtures.AHORA, mediasNuevas = 1,
        )

        val resultado = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals(listOf("red-1", "red-2"), alertas.sincronizaciones)
        // Dos redes, cada una con altas y medias nuevas: cuatro notificaciones, ninguna por las bajas.
        assertEquals(4, notificaciones.size)
        val textos = notificaciones.map { it.extras.getString(Notification.EXTRA_TEXT) }
        assertEquals(2, textos.count { it == "2 alertas nuevas de severidad alta" })
        assertEquals(2, textos.count { it == "1 alerta nueva de severidad media" })
        assertTrue(notificaciones.all { it.extras.getString(Notification.EXTRA_TITLE) == "Zonda · Oficina" })
        assertEquals(setOf(Notificador.CANAL_SONIDO, Notificador.CANAL_VIBRACION), notificaciones.map { it.channelId }.toSet())
    }

    @Test
    fun `con la severidad en sin aviso no notifica y sin novedades tampoco`() = runTest {
        preferencias.avisos.value = PreferenciasAvisos(alta = ModoAviso.SIN_AVISO, media = ModoAviso.SIN_AVISO, baja = ModoAviso.SIN_AVISO)
        alertas.resultadoSincronizacion = ResultadoSincronizacion(nuevas = 2, modificadas = 0, altasNuevas = 2, accionesEnviadas = 0, accionesConError = 0, hora = Fixtures.AHORA)

        worker().doWork()
        assertTrue(notificaciones.isEmpty())

        preferencias.avisos.value = PreferenciasAvisos()
        alertas.resultadoSincronizacion = ResultadoSincronizacion(nuevas = 0, modificadas = 3, altasNuevas = 0, accionesEnviadas = 0, accionesConError = 0, hora = Fixtures.AHORA)
        worker().doWork()
        assertTrue(notificaciones.isEmpty())
    }

    @Test
    fun `sin servidor pide reintentar y con el acceso vencido no`() = runTest {
        alertas.errorAlSincronizar = ErrorZonda.SinConexion()
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())

        alertas.errorAlSincronizar = ErrorZonda.NoAutorizado()
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(notificaciones.isEmpty())
    }
}
