package ar.edu.uade.zonda.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import ar.edu.uade.zonda.di.AppContainer
import ar.edu.uade.zonda.domain.model.AvisosDeZonda
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.notificaciones.Notificador
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Sincronización en segundo plano (RNF08): cada 15 minutos, el mínimo que permite Android, solo con
 * red. Sincroniza todas las redes pareadas, no solo la activa, y, si el interruptor "Notificaciones
 * del sistema" de Ajustes está prendido, avisa las altas nuevas que Telegram no avisó en este
 * teléfono, recuerda una vez las que siguen sin revisar a los 15 minutos ([AvisosDeZonda], cambio 13)
 * y avisa las revisiones de este teléfono que no se aplicaron porque otra persona revisó antes (cambio 7).
 * Si no hay red o el servidor falla, WorkManager reintenta con espera exponencial (informe, 14.3); una
 * red con el acceso vencido se saltea hasta que se vuelva a parear.
 */
class SincronizacionWorker(
    contexto: Context,
    parametros: WorkerParameters,
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
    private val preferencias: PreferenciasRepository,
    private val notificador: Notificador,
    private val reloj: () -> Instant = Instant::now,
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        var reintentar = false
        val notificar = preferencias.observarNotificacionesActivas().first()
        for (red in redes.observarRedes().first()) {
            if (red.accesoVencido) continue
            try {
                val resultado = alertas.sincronizar(red.id)
                if (notificar) avisar(red.id, resultado)
            } catch (e: ErrorZonda.SinConexion) {
                reintentar = true
            } catch (e: ErrorZonda.ErrorDelServidor) {
                reintentar = true
            } catch (e: ErrorZonda) {
                // 401: la red ya quedó marcada y la app pide un pareo nuevo. Otros errores se ven en pantalla.
            }
        }
        return if (reintentar && runAttemptCount < MAXIMO_REINTENTOS) Result.retry() else Result.success()
    }

    /**
     * Las notificaciones de una red después de sincronizarla. La red se lee de nuevo porque la
     * sincronización acaba de actualizar si Telegram sigue vinculado.
     */
    private suspend fun avisar(redId: String, resultado: ResultadoSincronizacion) {
        val red = redes.observarRedes().first().firstOrNull { it.id == redId } ?: return
        val avisos = preferencias.observarAvisos().first()
        notificador.avisarAltasNuevas(red, AvisosDeZonda.altasParaNotificar(resultado, red.telegramVinculado, avisos))
        notificador.avisarRevisionesNoAplicadas(red, resultado.conflictosNuevos)

        val sinRevisar = alertas.observarAlertas(redId, ALTAS_SIN_REVISAR_DEL_DIA).first()
        val recordar = AvisosDeZonda.altasParaRecordar(sinRevisar, preferencias.recordatoriosHasta(redId), resultado.idsAltasNuevas, reloj())
        if (recordar.isNotEmpty()) {
            notificador.recordarAltas(red, recordar)
            preferencias.guardarRecordatoriosHasta(redId, recordar.maxOf { it.creadaEn })
        }
    }

    companion object {
        /** Las candidatas al recordatorio: las altas sin revisar del último día ([AvisosDeZonda.VENTANA_RECORDATORIO]). */
        private val ALTAS_SIN_REVISAR_DEL_DIA = FiltroAlertas(
            estado = FiltroEstado.SIN_REVISAR,
            severidades = setOf(Severidad.ALTA),
            periodo = Periodo.ULTIMAS_24_HORAS,
        )

        const val NOMBRE = "sincronizacion-periodica"
        const val NOMBRE_UNA_VEZ = "sincronizacion-una-vez"
        const val MAXIMO_REINTENTOS = 5
        const val CADA_MINUTOS = 15L

        /** Programa la tarea una sola vez; si ya estaba programada, se conserva. Se llama al arrancar la app. */
        fun programar(contexto: Context) {
            val pedido = PeriodicWorkRequestBuilder<SincronizacionWorker>(CADA_MINUTOS, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }

        /**
         * Corre la misma sincronización una sola vez, ahora. WorkManager no deja adelantar un trabajo
         * periódico, así que esto es lo que usa el receptor de debug para probar la notificación sin
         * esperar los 15 minutos.
         */
        fun correrUnaVez(contexto: Context) {
            val pedido = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(contexto).enqueueUniqueWork(NOMBRE_UNA_VEZ, ExistingWorkPolicy.REPLACE, pedido)
        }
    }
}

/** Sin Hilt, WorkManager crea los workers con esta fábrica, que toma las dependencias del [AppContainer]. */
class FabricaDeWorkers(private val contenedor: AppContainer) : WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            SincronizacionWorker::class.java.name -> SincronizacionWorker(
                appContext,
                workerParameters,
                contenedor.redes,
                contenedor.alertas,
                contenedor.preferencias,
                contenedor.notificador,
            )
            else -> null
        }
}
