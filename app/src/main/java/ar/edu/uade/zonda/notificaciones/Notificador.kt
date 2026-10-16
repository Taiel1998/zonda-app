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
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.AvisosDeZonda
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.navigation.EnlacesInternos
import ar.edu.uade.zonda.ui.theme.VerdeZonda
import java.time.ZoneId

/**
 * La notificación local cuando la sincronización en segundo plano trae alertas altas nuevas
 * (informe, sección 6: el aviso inmediato llega por Telegram y la app suma el suyo), sin repetir las
 * que Telegram ya avisó en este teléfono, y el recordatorio de las altas sin revisar (cambio 13).
 *
 * Hay un solo canal, con el sonido del sistema: con sonido, en silencio o sin aviso son modos de
 * Telegram, y un canal por severidad quedó como mejora posterior. El texto dice la severidad con
 * palabras (RNF09) y no lleva IP ni nombres de equipos (RNF02).
 *
 * Tocarla abre la lista de Alertas de esa red filtrada en "Alta · Sin revisar", o el detalle si trajo
 * una sola alerta, igual que el enlace de Telegram. Las notificaciones van en un grupo con resumen
 * propio: con el grupo cerrado Android usa el resumen, y el que arma el sistema abre la app en Estado.
 */
class Notificador(private val contexto: Context) {

    fun crearCanales() {
        val alertas = NotificationChannelCompat.Builder(CANAL_ALERTAS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(contexto.getString(R.string.canal_alertas))
            .setDescription(contexto.getString(R.string.canal_alertas_descripcion))
            .build()
        // Cambio 7: las revisiones que no se aplicaron van en un canal aparte, que se puede silenciar solo.
        val revisiones = NotificationChannelCompat.Builder(CANAL_REVISIONES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(contexto.getString(R.string.canal_revisiones))
            .setDescription(contexto.getString(R.string.canal_revisiones_descripcion))
            .build()
        NotificationManagerCompat.from(contexto).createNotificationChannelsCompat(listOf(alertas, revisiones))
    }

    /**
     * Una notificación por red con las altas nuevas que Zonda tiene que avisar: las que Telegram no
     * avisó en este teléfono ([AvisosDeZonda.altasParaNotificar], cambio 13). Sin ninguna, nada.
     */
    fun avisarAltasNuevas(red: Red, ids: List<String>) {
        val cantidad = ids.size
        if (cantidad == 0) return
        val texto = contexto.resources.getQuantityString(R.plurals.notificacion_altas, cantidad, cantidad)
        publicarAltas(red, etiqueta = null, ids = ids, texto = texto)
    }

    /**
     * El recordatorio de las altas que siguen sin revisar a los 15 minutos de llegar (cambio 13):
     * "Recordatorio: 1 alerta alta sin revisar desde las 14:00", con la hora de la más vieja. Va aparte
     * de la de alertas nuevas, así una no tapa a la otra, y abre lo mismo.
     */
    fun recordarAltas(red: Red, altas: List<Alerta>, zona: ZoneId = ZoneId.systemDefault()) {
        if (altas.isEmpty()) return
        val desde = Formato.hora(altas.minOf { it.creadaEn }, zona)
        val texto = contexto.resources.getQuantityString(R.plurals.notificacion_recordatorio, altas.size, altas.size, desde)
        publicarAltas(red, ETIQUETA_RECORDATORIO, altas.map { it.id }, texto)
    }

    /**
     * Las revisiones de este teléfono que no se aplicaron porque otra persona revisó antes, detectadas al
     * sincronizar en segundo plano (cambio 7): una notificación por red en el canal "Revisiones", una
     * sola vez por revisión. Con una abre su detalle, donde se elige cuál queda; con varias, Alertas con
     * esas. Con la app abierta lo dice el aviso de Estado.
     */
    fun avisarRevisionesNoAplicadas(red: Red, alertaIds: List<String>) {
        if (alertaIds.isEmpty()) return
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled()) return
        val una = alertaIds.singleOrNull()
        val texto = if (una != null) {
            contexto.getString(R.string.notificacion_no_aplicada)
        } else {
            contexto.resources.getQuantityString(R.plurals.notificacion_no_aplicadas, alertaIds.size, alertaIds.size)
        }
        val destino = if (una != null) EnlacesInternos.detalle(red.id, una) else EnlacesInternos.noAplicadas(red.id)
        val notificacion = NotificationCompat.Builder(contexto, CANAL_REVISIONES)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(VerdeZonda.toArgb())
            .setContentTitle(contexto.getString(R.string.notificacion_titulo, red.nombre))
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir(destino))
            .setAutoCancel(true)
            .build()
        publicar(gestor, ETIQUETA_REVISIONES, idDe(red), notificacion)
    }

    /** Abre el detalle si es una sola alta, o Alertas de esa red filtrada en "Alta · Sin revisar". */
    private fun publicarAltas(red: Red, etiqueta: String?, ids: List<String>, texto: String) {
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled()) return
        val unica = ids.singleOrNull()
        val destino = if (unica != null) {
            EnlacesInternos.detalle(red.id, unica)
        } else {
            EnlacesInternos.alertas(red.id, EstadoRevision.SIN_REVISAR, Severidad.ALTA)
        }
        val notificacion = NotificationCompat.Builder(contexto, CANAL_ALERTAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(VerdeZonda.toArgb())
            .setContentTitle(contexto.getString(R.string.notificacion_titulo, red.nombre))
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GRUPO)
            .setContentIntent(abrir(destino))
            .setAutoCancel(true)
            .build()
        if (publicar(gestor, etiqueta, id = idDe(red), notificacion = notificacion)) {
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

        /** Las revisiones de este teléfono que no se aplicaron (cambio 7). */
        const val CANAL_REVISIONES = "revisiones"

        /** El grupo de las notificaciones de alertas nuevas. */
        const val GRUPO = "ar.edu.uade.zonda.ALERTAS_NUEVAS"

        // El resumen lleva etiqueta para no chocar con los ids que salen de cada red.
        private const val ETIQUETA_RESUMEN = "resumen"
        private const val ID_RESUMEN = 0

        /** El recordatorio usa el id de la red con su etiqueta: no reemplaza a la de alertas nuevas. */
        const val ETIQUETA_RECORDATORIO = "recordatorio"

        const val ETIQUETA_REVISIONES = "revisiones"
    }
}
