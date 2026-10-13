package ar.edu.uade.zonda.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ar.edu.uade.zonda.sync.SincronizacionWorker

/**
 * Solo en el build de debug: corre el worker de sincronización una vez, sin esperar los 15 minutos,
 * para probar la notificación desde la terminal con la app en segundo plano:
 *
 *   adb shell am broadcast -a ar.edu.uade.zonda.SINCRONIZAR -n ar.edu.uade.zonda/.debug.SincronizarAhoraReceiver
 */
class SincronizarAhoraReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        SincronizacionWorker.correrUnaVez(context)
    }
}
