package ar.edu.uade.zonda.notificaciones

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ar.edu.uade.zonda.MainActivity
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.ui.navigation.ENLACE_ALERTAS

/**
 * La notificación local cuando la sincronización en segundo plano trae alertas nuevas (RF05).
 *
 * Hay un canal por modo de aviso (con sonido, vibración, en silencio) y cada severidad va al canal
 * que eligió la persona en Preferencias. Así el sistema respeta la elección aunque la app esté
 * cerrada, y la persona puede ajustar cada canal desde Android. El texto siempre dice la severidad
 * con palabras (RNF09) y no lleva IP ni nombres de equipos (RNF02). Tocarla abre la lista de alertas.
 */
class Notificador(private val contexto: Context) {

    fun crearCanales() {
        val canales = listOf(
            NotificationChannelCompat.Builder(CANAL_SONIDO, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(contexto.getString(R.string.canal_sonido))
                .setDescription(contexto.getString(R.string.canal_sonido_descripcion))
                .build(),
            NotificationChannelCompat.Builder(CANAL_VIBRACION, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(contexto.getString(R.string.canal_vibracion))
                .setDescription(contexto.getString(R.string.canal_vibracion_descripcion))
                .setSound(null, null)
                .setVibrationEnabled(true)
                .build(),
            NotificationChannelCompat.Builder(CANAL_SILENCIO, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(contexto.getString(R.string.canal_silencio))
                .setDescription(contexto.getString(R.string.canal_silencio_descripcion))
                .build(),
        )
        NotificationManagerCompat.from(contexto).createNotificationChannelsCompat(canales)
    }

    /** Una notificación por severidad con alertas nuevas, salvo las severidades marcadas "sin aviso". */
    fun avisarAlertasNuevas(red: Red, resultado: ResultadoSincronizacion, avisos: PreferenciasAvisos) {
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled()) return
        val porSeveridad = listOf(
            Severidad.ALTA to resultado.altasNuevas,
            Severidad.MEDIA to resultado.mediasNuevas,
            Severidad.BAJA to resultado.bajasNuevas,
        )
        for ((severidad, cantidad) in porSeveridad) {
            if (cantidad == 0) continue
            val canal = when (avisos.para(severidad)) {
                ModoAviso.SONIDO -> CANAL_SONIDO
                ModoAviso.VIBRACION -> CANAL_VIBRACION
                ModoAviso.SIN_AVISO -> continue
            }
            val notificacion = NotificationCompat.Builder(contexto, canal)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle(contexto.getString(R.string.notificacion_titulo, red.nombre))
                .setContentText(contexto.resources.getQuantityString(pluralDe(severidad), cantidad, cantidad))
                .setPriority(if (severidad == Severidad.ALTA) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(abrirAlertas())
                .setAutoCancel(true)
                .build()
            try {
                gestor.notify(idDe(red, severidad), notificacion)
            } catch (e: SecurityException) {
                // Sin el permiso de notificaciones (Android 13 o superior) no se muestra nada; la app sigue igual.
            }
        }
    }

    private fun pluralDe(severidad: Severidad): Int = when (severidad) {
        Severidad.ALTA -> R.plurals.notificacion_altas
        Severidad.MEDIA -> R.plurals.notificacion_medias
        Severidad.BAJA -> R.plurals.notificacion_bajas
    }

    /** Un id por red y severidad: una nueva reemplaza a la anterior en vez de apilarse. */
    private fun idDe(red: Red, severidad: Severidad): Int = red.id.hashCode() * 3 + severidad.ordinal

    private fun abrirAlertas(): PendingIntent {
        val intento = Intent(Intent.ACTION_VIEW, Uri.parse(ENLACE_ALERTAS), contexto, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(contexto, 0, intento, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CANAL_SONIDO = "alertas_sonido"
        const val CANAL_VIBRACION = "alertas_vibracion"
        const val CANAL_SILENCIO = "alertas_silencio"
    }
}
