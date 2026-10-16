package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Duration
import java.time.Instant

/**
 * Qué avisa Zonda con sus propias notificaciones después de sincronizar en segundo plano (cambio 13).
 *
 * Telegram avisa al instante y Zonda, hasta 15 minutos después. Para no avisar dos veces lo mismo,
 * Zonda calla las altas nuevas que Telegram ya avisó en este teléfono: si hay un chat vinculado, el
 * aviso de esa alerta salió y en Telegram las altas no están en "Sin aviso". El servidor dice si salió
 * el aviso de cada alerta, no a qué chat, pero manda a todos los chats vinculados a la vez.
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

    /** Las altas nuevas de esta sincronización que Zonda tiene que avisar. */
    fun altasParaNotificar(resultado: ResultadoSincronizacion, telegramVinculado: Boolean, avisos: PreferenciasAvisos): List<String> {
        val telegramAvisaLasAltas = telegramVinculado && avisos.alta != ModoAviso.SIN_AVISO
        if (!telegramAvisaLasAltas) return resultado.idsAltasNuevas
        return resultado.idsAltasNuevas.filterNot { it in resultado.idsAltasNuevasConAvisoTelegram }
    }

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
