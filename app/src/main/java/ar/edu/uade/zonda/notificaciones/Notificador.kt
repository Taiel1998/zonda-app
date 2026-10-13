package ar.edu.uade.zonda.notificaciones

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ar.edu.uade.zonda.MainActivity
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.ui.navigation.EnlacesInternos
import ar.edu.uade.zonda.ui.theme.VerdeZonda

/**
 * La notificación local cuando la sincronización en segundo plano trae alertas altas nuevas
 * (informe, sección 6: el aviso inmediato llega por Telegram y la app suma el suyo).
 *
 * Hay un solo canal, con el sonido del sistema: con sonido, vibración o sin aviso son modos de
 * Telegram, y un canal por severidad quedó como mejora posterior. El texto dice la severidad con
 * palabras (RNF09) y no lleva IP ni nombres de equipos (RNF02).
 *
 * Tocarla abre la lista de Alertas de esa red filtrada en "Alta · Sin revisar", o el detalle si trajo
 * una sola alerta, igual que el enlace de Telegram. Las notificaciones van en un grupo con resumen
 * propio: con el grupo cerrado Android usa el resumen, y el que arma el sistema abre la app en Estado.
 */
class Notificador(private val contexto: Context) {

    fun crearCanales() {
        val canal = NotificationChannelCompat.Builder(CANAL_ALERTAS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(contexto.getString(R.string.canal_alertas))
            .setDescription(contexto.getString(R.string.canal_alertas_descripcion))
            .build()
        NotificationManagerCompat.from(contexto).createNotificationChannel(canal)
    }

    /** Una notificación por red con las altas nuevas de esta sincronización; sin altas nuevas, nada. */
    fun avisarAltasNuevas(red: Red, resultado: ResultadoSincronizacion) {
        val cantidad = resultado.altasNuevas
        if (cantidad == 0) return
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled()) return
        val unica = resultado.idsAltasNuevas.singleOrNull()?.takeIf { cantidad == 1 }
        val destino = if (unica != null) {
            EnlacesInternos.detalle(red.id, unica)
        } else {
            EnlacesInternos.alertas(red.id, EstadoRevision.SIN_REVISAR, Severidad.ALTA)
        }
        val notificacion = NotificationCompat.Builder(contexto, CANAL_ALERTAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(VerdeZonda.toArgb())
            .setContentTitle(contexto.getString(R.string.notificacion_titulo, red.nombre))
            .setContentText(contexto.resources.getQuantityString(R.plurals.notificacion_altas, cantidad, cantidad))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GRUPO)
            .setContentIntent(abrir(destino))
            .setAutoCancel(true)
            .build()
        if (publicar(gestor, etiqueta = null, id = idDe(red), notificacion = notificacion)) {
            publicar(gestor, ETIQUETA_RESUMEN, ID_RESUMEN, resumen())
        }
    }

    /** El resumen del grupo; abre la lista de alertas. No suena por su cuenta (GROUP_ALERT_CHILDREN). */
    private fun resumen(): Notification =
        NotificationCompat.Builder(contexto, CANAL_ALERTAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(VerdeZonda.toArgb())
            .setContentTitle(contexto.getString(R.string.app_name))
            .setContentText(contexto.getString(R.string.notificacion_resumen))
            .setGroup(GRUPO)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(abrir(EnlacesInternos.ALERTAS))
            .setAutoCancel(true)
            .build()

    private fun publicar(gestor: NotificationManagerCompat, etiqueta: String?, id: Int, notificacion: Notification): Boolean =
        try {
            gestor.notify(etiqueta, id, notificacion)
            true
        } catch (e: SecurityException) {
            // Sin el permiso de notificaciones (Android 13 o superior) no se muestra nada; la app sigue igual.
            false
        }

    /** Un id por red: una notificación nueva de la misma red reemplaza a la anterior en vez de apilarse. */
    private fun idDe(red: Red): Int = red.id.hashCode()

    /**
     * Abre la app en el enlace interno. El intent es explícito (va directo a MainActivity), así que el
     * enlace no se publica a otras apps.
     */
    private fun abrir(enlace: String): PendingIntent {
        val intento = Intent(Intent.ACTION_VIEW, Uri.parse(enlace), contexto, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(contexto, enlace.hashCode(), intento, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CANAL_ALERTAS = "alertas_altas"

        /** El grupo de las notificaciones de alertas nuevas. */
        const val GRUPO = "ar.edu.uade.zonda.ALERTAS_NUEVAS"

        // El resumen lleva etiqueta para no chocar con los ids que salen de cada red.
        private const val ETIQUETA_RESUMEN = "resumen"
        private const val ID_RESUMEN = 0
    }
}
