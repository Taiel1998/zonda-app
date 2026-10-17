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
import ar.edu.uade.zonda.domain.model.ModoNotificacion
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.AlertaNueva
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.notificaciones.Notificador
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.navigation.EnlacesInternos
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private val gestor: NotificationManager = contexto.getSystemService(NotificationManager::class.java)

    private fun worker(): SincronizacionWorker {
        val fabrica = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                SincronizacionWorker(appContext, workerParameters, redes, alertas, preferencias, notificador, reloj = { Fixtures.AHORA })
        }
        return TestListenableWorkerBuilder.from(contexto, SincronizacionWorker::class.java).setWorkerFactory(fabrica).build()
    }

    private val notificaciones: List<Notification>
        get() = shadowOf(gestor).allNotifications

    /** Las notificaciones de cada red, sin el resumen del grupo. */
    private val avisos: List<Notification>
        get() = notificaciones.filter { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }

    private fun destinoDe(notificacion: Notification): String? = shadowOf(notificacion.contentIntent).savedIntent.data?.toString()

    private fun resultado(
        nuevas: Int,
        altasNuevas: Int,
        idsAltasNuevas: List<String> = emptyList(),
        conAvisoTelegram: Set<String> = emptySet(),
        medias: List<String> = emptyList(),
    ) = ResultadoSincronizacion(
        nuevas = nuevas, modificadas = 0, altasNuevas = altasNuevas, accionesEnviadas = 0, accionesConError = 0,
        hora = Fixtures.AHORA,
        alertasNuevas = idsAltasNuevas.map { AlertaNueva(it, Severidad.ALTA, conAvisoTelegram = it in conAvisoTelegram) } +
            medias.map { AlertaNueva(it, Severidad.MEDIA, conAvisoTelegram = it in conAvisoTelegram) },
    )

    private fun textoDe(notificacion: Notification): String? = notificacion.extras.getString(Notification.EXTRA_TEXT)

    private fun canal(modo: ModoNotificacion) = Notificador.canal(modo)

    @Test
    fun `sincroniza todas las redes menos las de acceso vencido y notifica las altas nuevas de cada una`() = runTest {
        alertas.resultadoSincronizacion = resultado(nuevas = 5, altasNuevas = 2, idsAltasNuevas = listOf("a-1", "a-4"))

        val resultado = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals(listOf("red-1", "red-2"), alertas.sincronizaciones)
        // Una notificación por red, en el canal de las altas (de fábrica, sonido y vibración), con la severidad dicha con palabras.
        assertEquals(2, avisos.size)
        assertTrue(avisos.all { it.extras.getString(Notification.EXTRA_TEXT) == "2 alertas nuevas de severidad alta" })
        assertTrue(avisos.all { it.extras.getString(Notification.EXTRA_TITLE) == "Zonda · Oficina" })
        assertTrue(notificaciones.all { it.channelId == canal(ModoNotificacion.SONIDO_Y_VIBRACION) })
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
    fun `las medias se avisan aparte, en el canal de su modo y abriendo Alertas en media`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        alertas.resultadoSincronizacion = resultado(nuevas = 3, altasNuevas = 1, idsAltasNuevas = listOf("a-1"), medias = listOf("m-1", "m-2"))

        worker().doWork()

        val alta = avisos.single { textoDe(it) == "1 alerta nueva de severidad alta" }
        val media = avisos.single { textoDe(it) == "2 alertas nuevas de severidad media" }
        assertEquals(canal(ModoNotificacion.SONIDO_Y_VIBRACION), alta.channelId)
        // De fábrica, las medias solo vibran.
        assertEquals(canal(ModoNotificacion.SOLO_VIBRACION), media.channelId)
        assertEquals(EnlacesInternos.alertas("red-1", EstadoRevision.SIN_REVISAR, Severidad.MEDIA), destinoDe(media))
    }

    @Test
    fun `cada severidad sale en el canal del modo elegido`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        preferencias.notificaciones.value = PreferenciasNotificaciones(alta = ModoNotificacion.SOLO_SONIDO, media = ModoNotificacion.EN_SILENCIO)
        alertas.resultadoSincronizacion = resultado(nuevas = 2, altasNuevas = 1, idsAltasNuevas = listOf("a-1"), medias = listOf("m-1"))

        worker().doWork()

        assertEquals(canal(ModoNotificacion.SOLO_SONIDO), avisos.single { textoDe(it)!!.endsWith("alta") }.channelId)
        assertEquals(canal(ModoNotificacion.EN_SILENCIO), avisos.single { textoDe(it)!!.endsWith("media") }.channelId)
    }

    @Test
    fun `el resumen del grupo va en el canal mas fuerte de las que estan a la vista`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        preferencias.notificaciones.value = PreferenciasNotificaciones(media = ModoNotificacion.EN_SILENCIO)
        alertas.resultadoSincronizacion = resultado(nuevas = 2, altasNuevas = 1, idsAltasNuevas = listOf("a-1"), medias = listOf("m-1"))

        worker().doWork()

        // La media en silencio se publica después de la alta: el grupo no pasa a las silenciosas.
        val resumen = notificaciones.single { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
        assertEquals(canal(ModoNotificacion.SONIDO_Y_VIBRACION), resumen.channelId)
    }

    @Test
    fun `sin alertas nuevas o con todo en Sin aviso no notifica, pero sincroniza igual`() = runTest {
        alertas.resultadoSincronizacion = resultado(nuevas = 4, altasNuevas = 0)
        worker().doWork()
        assertTrue(notificaciones.isEmpty())

        preferencias.notificaciones.value = PreferenciasNotificaciones.NINGUNA
        alertas.resultadoSincronizacion = resultado(nuevas = 2, altasNuevas = 2, idsAltasNuevas = listOf("a-1", "a-2"), medias = listOf("m-1"))
        worker().doWork()
        assertTrue(notificaciones.isEmpty())
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
        assertEquals(canal(ModoNotificacion.SONIDO_Y_VIBRACION), recordatorio.channelId)
        assertEquals(llegada, preferencias.recordatorios["red-1"])

        // La siguiente vuelta no la recuerda de nuevo; la de hace 5 minutos todavía espera.
        gestor.cancelAll()
        worker().doWork()
        assertTrue(notificaciones.isEmpty())
    }

    @Test
    fun `el recordatorio suena como las altas de Zonda y, con ellas en Sin aviso, con sonido y vibracion`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        alertas.agregar(Fixtures.alerta("a-9", creadaEn = Fixtures.AHORA.minus(Duration.ofMinutes(20))))
        preferencias.notificaciones.value = PreferenciasNotificaciones(alta = ModoNotificacion.SOLO_VIBRACION)
        worker().doWork()
        assertEquals(canal(ModoNotificacion.SOLO_VIBRACION), avisos.single().channelId)

        // El caso de Martín: las altas le llegan por Telegram y apagó las de Zonda, pero quiere el recordatorio.
        gestor.cancelAll()
        preferencias.recordatorios.clear()
        preferencias.notificaciones.value = PreferenciasNotificaciones(alta = ModoNotificacion.SIN_AVISO)
        worker().doWork()
        assertEquals(canal(ModoNotificacion.SONIDO_Y_VIBRACION), avisos.single().channelId)
    }

    @Test
    fun `con el recordatorio apagado no recuerda`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        alertas.agregar(Fixtures.alerta("a-9", creadaEn = Fixtures.AHORA.minus(Duration.ofMinutes(20))))
        preferencias.notificaciones.value = PreferenciasNotificaciones(recordatorio = false)

        worker().doWork()

        assertTrue(notificaciones.isEmpty())
        assertNull(preferencias.recordatorios["red-1"])
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

    @Test
    fun `avisa una vez, en el canal de revisiones, las revisiones que no se aplicaron`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        alertas.resultadoSincronizacion = resultado(nuevas = 0, altasNuevas = 0).copy(conflictosNuevos = listOf("a-3"))

        worker().doWork()

        val aviso = notificaciones.single()
        assertEquals(Notificador.CANAL_REVISIONES, aviso.channelId)
        assertEquals("Zonda · Oficina", aviso.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Una revisión tuya no se aplicó. Otra persona revisó esa alerta antes. Tocá para elegir cuál queda.", textoDe(aviso))
        assertEquals(EnlacesInternos.detalle("red-1", "a-3"), destinoDe(aviso))
    }

    @Test
    fun `con varias revisiones que no se aplicaron la notificacion abre Alertas con esas`() = runTest {
        redes = FakeRedesRepository(listOf(Fixtures.red("red-1")))
        alertas.resultadoSincronizacion = resultado(nuevas = 0, altasNuevas = 0).copy(conflictosNuevos = listOf("a-3", "a-5"))

        worker().doWork()

        val aviso = notificaciones.single()
        assertEquals("2 revisiones tuyas no se aplicaron. Alguien más revisó esas alertas antes. Tocá para elegir cuáles quedan.", textoDe(aviso))
        assertEquals(EnlacesInternos.noAplicadas("red-1"), destinoDe(aviso))
    }

    @Test
    fun `un canal por modo con su sonido y su vibracion, y el canal de la 1_0_3 se borra`() {
        val anterior = android.app.NotificationChannel(Notificador.CANAL_ANTERIOR, "Alertas altas", NotificationManager.IMPORTANCE_HIGH)
        gestor.createNotificationChannel(anterior)

        Notificador(contexto).crearCanales()

        assertTrue(shadowOf(gestor).isChannelDeleted(Notificador.CANAL_ANTERIOR))
        val sonidoYVibracion = gestor.getNotificationChannel(canal(ModoNotificacion.SONIDO_Y_VIBRACION))
        assertNotNull(sonidoYVibracion.sound)
        assertTrue(sonidoYVibracion.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, sonidoYVibracion.importance)

        val soloSonido = gestor.getNotificationChannel(canal(ModoNotificacion.SOLO_SONIDO))
        assertNotNull(soloSonido.sound)
        assertFalse(soloSonido.shouldVibrate())

        val soloVibracion = gestor.getNotificationChannel(canal(ModoNotificacion.SOLO_VIBRACION))
        assertNull(soloVibracion.sound)
        assertTrue(soloVibracion.shouldVibrate())

        val enSilencio = gestor.getNotificationChannel(canal(ModoNotificacion.EN_SILENCIO))
        assertNull(enSilencio.sound)
        assertFalse(enSilencio.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_LOW, enSilencio.importance)
        assertEquals("Alertas en silencio", enSilencio.name)
        assertNotNull(gestor.getNotificationChannel(Notificador.CANAL_REVISIONES))
    }
}
