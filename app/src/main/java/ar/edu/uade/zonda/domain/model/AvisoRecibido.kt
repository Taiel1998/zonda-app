package ar.edu.uade.zonda.domain.model

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Lo que trajo el aviso de Telegram en su enlace (RF03, criterio 3): la severidad, el servicio, la
 * hora de detección y la hora del aviso. Si la alerta todavía no está en el teléfono, el detalle lo
 * muestra hasta poder descargarla (Figma 3-139).
 *
 * Llega en un enlace, y un enlace lo puede armar cualquiera: cada valor se valida y el que no cierra
 * se descarta. Solo se muestra, rotulado como "Lo que trajo el aviso"; no se guarda ni dispara nada.
 */
data class AvisoRecibido(
    val severidad: Severidad,
    val servicio: String,
    val deteccion: Instant?,
    val enviadoEn: Instant?,
) {
    companion object {
        /** Los nombres de servicio de Zeek: "ssh", "ftp", "dce_rpc"... */
        private val SERVICIO = Regex("^[a-z0-9][a-z0-9_-]{0,23}$")

        /**
         * Null si el enlace no trae una severidad y un servicio válidos, por ejemplo un enlace que solo
         * lleva el id. Una hora mal escrita se pierde sola, sin invalidar lo demás.
         */
        fun desdeEnlace(severidad: String?, servicio: String?, deteccion: String?, aviso: String?): AvisoRecibido? {
            val nivel = Severidad.entries.firstOrNull { it.name.equals(severidad?.trim(), ignoreCase = true) } ?: return null
            val nombre = servicio?.trim()?.lowercase()?.takeIf { SERVICIO.matches(it) } ?: return null
            return AvisoRecibido(nivel, nombre, leerHora(deteccion), leerHora(aviso))
        }

        private fun leerHora(texto: String?): Instant? =
            try {
                texto?.let(Instant::parse)
            } catch (e: DateTimeParseException) {
                null
            }
    }
}
