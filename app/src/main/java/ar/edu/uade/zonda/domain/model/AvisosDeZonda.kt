package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Duration
import java.time.Instant

/**
 * Qué avisa Zonda con sus propias notificaciones después de sincronizar en segundo plano (cambio 13 y
 * 1.0.4).
 *
 * Telegram avisa al instante y Zonda, hasta 15 minutos después. Para no avisar dos veces lo mismo,
 * Zonda calla las alertas nuevas que Telegram ya avisó en este teléfono: si hay un chat vinculado, el
 * aviso de esa alerta salió y en Telegram esa severidad no está en "Sin aviso". El servidor dice si
 * salió el aviso de cada alerta, no a qué chat, pero manda a todos los chats vinculados a la vez. Lo
 * que queda se avisa como eligió la persona para esa severidad en "Notificaciones de Zonda".
 *
 * Y si una alta sigue sin revisar a los 15 minutos de llegar, Zonda la recuerda una sola vez, aunque
 * Telegram la haya avisado. Para no recordar dos veces la misma, cada red guarda hasta qué hora ya
 * recordó (la de la alta más nueva que entró en un recordatorio).
 */
object AvisosDeZonda {

    /** A los 15 minutos de llegar, una alta que sigue sin revisar tiene su recordatorio. */
    val ESPERA_RECORDATORIO: Duration = Duration.ofMinutes(15)

    /**
     * Solo se recuerdan las altas del último día: así no aparece un recordatorio de altas viejas, por
     * ejemplo la primera vez después de actualizar la app.
     */
    val VENTANA_RECORDATORIO: Duration = Duration.ofHours(24)

    /**
     * Las alertas nuevas de esta sincronización que Zonda tiene que avisar, por severidad: las que
     * Telegram no avisó en este teléfono, solo de las severidades que no están en "Sin aviso" en
     * [notificaciones]. Una severidad sin nada para avisar no aparece.
     */
    fun paraNotificar(
        resultado: ResultadoSincronizacion,
        telegramVinculado: Boolean,
        avisos: PreferenciasAvisos,
        notificaciones: PreferenciasNotificaciones,
    ): Map<Severidad, List<String>> =
        Severidad.entries
            .filter { notificaciones.para(it) != ModoNotificacion.SIN_AVISO }
            .associateWith { severidad ->
                val telegramLaAvisa = telegramVinculado && avisos.para(severidad) != ModoAviso.SIN_AVISO
                resultado.alertasNuevas
                    .filter { it.severidad == severidad && !(telegramLaAvisa && it.conAvisoTelegram) }
                    .map { it.id }
            }
            .filterValues { it.isNotEmpty() }

    /**
     * Las altas que hay que recordar ahora, de la más vieja a la más nueva: sin revisar, que llegaron
     * hace 15 minutos o más, después de [recordadasHasta] y dentro del último día. Las que llegaron en
     * esta misma sincronización ([recienLlegadas]) esperan a la siguiente, para no sumar un
     * recordatorio a la notificación de alertas nuevas.
     */
    fun altasParaRecordar(
        sinRevisar: List<Alerta>,
        recordadasHasta: Instant?,
        recienLlegadas: Collection<String>,
        ahora: Instant,
    ): List<Alerta> {
        val ventana = ahora.minus(VENTANA_RECORDATORIO)
        val desde = if (recordadasHasta != null && recordadasHasta.isAfter(ventana)) recordadasHasta else ventana
        val hasta = ahora.minus(ESPERA_RECORDATORIO)
        return sinRevisar
            .filter { it.severidad == Severidad.ALTA && it.sinRevisar && it.id !in recienLlegadas }
            .filter { it.creadaEn.isAfter(desde) && !it.creadaEn.isAfter(hasta) }
            .sortedBy { it.creadaEn }
    }
}
