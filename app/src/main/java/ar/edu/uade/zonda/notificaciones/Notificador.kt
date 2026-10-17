package ar.edu.uade.zonda.notificaciones

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
import ar.edu.uade.zonda.domain.model.ModoNotificacion
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.navigation.EnlacesInternos
import ar.edu.uade.zonda.ui.theme.VerdeZonda
import java.time.ZoneId

/**
 * Las notificaciones propias de Zonda después de la sincronización en segundo plano (informe, sección
 * 6: el aviso inmediato llega por Telegram y la app suma el suyo): las alertas nuevas que Telegram no
 * avisó en este teléfono, de cada severidad que no esté en "Sin aviso", el recordatorio de las altas
 * sin revisar (cambio 13) y las revisiones que no se aplicaron (cambio 7).
 *
 * Cada modo de "Notificaciones de Zonda" es un canal de Android (1.0.4): el sonido y la vibración de
 * un canal no se pueden cambiar después de crearlo, así que elegir otro modo es publicar en otro canal.
 * El texto dice la severidad con palabras (RNF09) y no lleva IP ni nombres de equipos (RNF02).
 *
 * Tocarla abre la lista de Alertas de esa red filtrada en esa severidad y "Sin revisar", o el detalle
 * si trajo una sola alerta, igual que el enlace de Telegram. Las notificaciones van en un grupo con
 * resumen propio: con el grupo cerrado Android usa el resumen, y el que arma el sistema abre la app en
 * Estado.
 */
class Notificador(private val contexto: Context) {

    fun crearCanales() {
        val gestor = NotificationManagerCompat.from(contexto)
        val porModo = MODOS_CON_CANAL.map { modo -> canalDe(modo) }
        // Cambio 7: las revisiones que no se aplicaron van en un canal aparte, que se puede silenciar solo.
        val revisiones = NotificationChannelCompat.Builder(CANAL_REVISIONES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(contexto.getString(R.string.canal_revisiones))
            .setDescription(contexto.getString(R.string.canal_revisiones_descripcion))
            .build()
        gestor.createNotificationChannelsCompat(porModo + revisiones)
        // Hasta la 1.0.3 había un solo canal para las altas; ahora cada una va en el canal de su modo.
        gestor.deleteNotificationChannel(CANAL_ANTERIOR)
    }

    /** El canal de un modo: con o sin sonido, con o sin vibración, y en silencio con importancia baja. */
    private fun canalDe(modo: ModoNotificacion): NotificationChannelCompat {
        val (nombre, descripcion) = when (modo) {
            ModoNotificacion.SONIDO_Y_VIBRACION -> R.string.canal_sonido_y_vibracion to R.string.canal_sonido_y_vibracion_descripcion
            ModoNotificacion.SOLO_SONIDO -> R.string.canal_solo_sonido to R.string.canal_solo_sonido_descripcion
            ModoNotificacion.SOLO_VIBRACION -> R.string.canal_solo_vibracion to R.string.canal_solo_vibracion_descripcion
            ModoNotificacion.EN_SILENCIO, ModoNotificacion.SIN_AVISO -> R.string.canal_en_silencio to R.string.canal_en_silencio_descripcion
        }
        val importancia = if (modo == ModoNotificacion.EN_SILENCIO) NotificationManagerCompat.IMPORTANCE_LOW else NotificationManagerCompat.IMPORTANCE_HIGH
        val suena = modo == ModoNotificacion.SONIDO_Y_VIBRACION || modo == ModoNotificacion.SOLO_SONIDO
        val vibra = modo == ModoNotificacion.SONIDO_Y_VIBRACION || modo == ModoNotificacion.SOLO_VIBRACION
        val sonido = if (suena) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null
        val atributos = if (suena) {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            null
        }
        return NotificationChannelCompat.Builder(canal(modo), importancia)
            .setName(contexto.getString(nombre))
            .setDescription(contexto.getString(descripcion))
            .setSound(sonido, atributos)
            .setVibrationEnabled(vibra)
            .build()
    }

    /**
     * Una notificación por red y severidad con las alertas nuevas que Zonda tiene que avisar
     * ([AvisosDeZonda.paraNotificar]), en el canal de su [modo]. Sin ninguna, o en "Sin aviso", nada.
     */
    fun avisarNuevas(red: Red, severidad: Severidad, ids: List<String>, modo: ModoNotificacion) {
        val cantidad = ids.size
        if (cantidad == 0 || modo == ModoNotificacion.SIN_AVISO) return
        val plural = when (severidad) {
            Severidad.ALTA -> R.plurals.notificacion_altas
            Severidad.MEDIA -> R.plurals.notificacion_medias
            Severidad.BAJA -> R.plurals.notificacion_bajas
        }
        val texto = contexto.resources.getQuantityString(plural, cantidad, cantidad)
        publicarAlertas(red, etiquetaDe(severidad), severidad, ids, texto, modo)
    }

    /**
     * El recordatorio de las altas que siguen sin revisar a los 15 minutos de llegar (cambio 13):
     * "Recordatorio: 1 alerta alta sin revisar desde las 14:00", con la hora de la más vieja. Va aparte
     * de la de alertas nuevas, así una no tapa a la otra, abre lo mismo y suena como las altas de Zonda
     * ([PreferenciasNotificaciones.modoDelRecordatorio]).
     */
    fun recordarAltas(red: Red, altas: List<Alerta>, modo: ModoNotificacion, zona: ZoneId = ZoneId.systemDefault()) {
        if (altas.isEmpty() || modo == ModoNotificacion.SIN_AVISO) return
        val desde = Formato.hora(altas.minOf { it.creadaEn }, zona)
        val texto = contexto.resources.getQuantityString(R.plurals.notificacion_recordatorio, altas.size, altas.size, desde)
        publicarAlertas(red, ETIQUETA_RECORDATORIO, Severidad.ALTA, altas.map { it.id }, texto, modo)
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

    /** Abre el detalle si es una sola alerta, o Alertas de esa red filtrada en esa severidad y "Sin revisar". */
    private fun publicarAlertas(red: Red, etiqueta: String, severidad: Severidad, ids: List<String>, texto: String, modo: ModoNotificacion) {
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled()) return
        val unica = ids.singleOrNull()
        val destino = if (unica != null) {
            EnlacesInternos.detalle(red.id, unica)
        } else {
            EnlacesInternos.alertas(red.id, EstadoRevision.SIN_REVISAR, severidad)
        }
        val canal = canal(modo)
        val notificacion = NotificationCompat.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(VerdeZonda.toArgb())
            .setContentTitle(contexto.getString(R.string.notificacion_titulo, red.nombre))
            .setContentText(texto)
            .setPriority(if (modo == ModoNotificacion.EN_SILENCIO) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setGroup(GRUPO)
            .setContentIntent(abrir(destino))
            .setAutoCancel(true)
            .build()
        if (publicar(gestor, etiqueta, id = idDe(red), notificacion = notificacion)) {
            publicar(gestor, ETIQUETA_RESUMEN, ID_RESUMEN, resumen(canalDelResumen(canal)))
        }
    }

    /**
     * El canal del resumen del grupo: el más fuerte entre las notificaciones de alertas que están a la
     * vista. Android ubica el grupo en la barra según el resumen, así que en silencio una alta quedaría
     * entre las silenciosas.
     */
    private fun canalDelResumen(canalNuevo: String): String {
        val aLaVista = contexto.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
            .map { it.notification }
            .filter { it.group == GRUPO && it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .map { it.channelId }
            .toSet() + canalNuevo
        return MODOS_CON_CANAL.map(::canal).firstOrNull { it in aLaVista } ?: canalNuevo
    }

    /** El resumen del grupo; abre la lista de alertas. No suena por su cuenta (GROUP_ALERT_CHILDREN). */
    private fun resumen(canal: String): Notification =
        NotificationCompat.Builder(contexto, canal)
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
        /** El canal único de las altas hasta la 1.0.3; se borra al crear los de la 1.0.4. */
        const val CANAL_ANTERIOR = "alertas_altas"

        /** Los modos que publican algo, cada uno con su canal, del más fuerte al más suave. */
        val MODOS_CON_CANAL = ModoNotificacion.entries - ModoNotificacion.SIN_AVISO

        /** El id del canal de cada modo: "alertas_sonido_y_vibracion", "alertas_en_silencio"... */
        fun canal(modo: ModoNotificacion): String = "alertas_" + modo.name.lowercase()

        /** Las revisiones de este teléfono que no se aplicaron (cambio 7). */
        const val CANAL_REVISIONES = "revisiones"

        /** El grupo de las notificaciones de alertas nuevas. */
        const val GRUPO = "ar.edu.uade.zonda.ALERTAS_NUEVAS"

        // El resumen lleva etiqueta para no chocar con los ids que salen de cada red.
        private const val ETIQUETA_RESUMEN = "resumen"
        private const val ID_RESUMEN = 0

        /** Las alertas nuevas usan el id de la red con la severidad como etiqueta: una por red y severidad. */
        fun etiquetaDe(severidad: Severidad): String = "nuevas_" + severidad.name.lowercase()

        /** El recordatorio usa el id de la red con su etiqueta: no reemplaza a la de alertas nuevas. */
        const val ETIQUETA_RECORDATORIO = "recordatorio"

        const val ETIQUETA_REVISIONES = "revisiones"
    }
}
