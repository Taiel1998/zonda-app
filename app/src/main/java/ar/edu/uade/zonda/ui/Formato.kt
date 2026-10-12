package ar.edu.uade.zonda.ui

import ar.edu.uade.zonda.domain.model.Equipo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cómo se muestran fechas, duraciones y números en toda la app. Kotlin puro, sin Compose, para
 * probarlo con JUnit. Los textos van en español rioplatense (coma decimal, "dom 27/09").
 */
object Formato {

    private val LOCALE: Locale = Locale.forLanguageTag("es-AR")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm", LOCALE)
    private val HORA_CON_SEGUNDOS = DateTimeFormatter.ofPattern("HH:mm:ss", LOCALE)
    private val DIA = DateTimeFormatter.ofPattern("EEE dd/MM", LOCALE)
    private val FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm", LOCALE)

    /** `02:09` */
    fun hora(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String = HORA.format(instante.atZone(zona))

    /** `02:09:12` */
    fun horaConSegundos(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String =
        HORA_CON_SEGUNDOS.format(instante.atZone(zona))

    /** `dom 27/09` (sin el punto que agrega el locale a la abreviatura del día). */
    fun dia(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String =
        DIA.format(instante.atZone(zona)).replace(".", "").lowercase(LOCALE)

    /** `02:09 · dom 27/09`, como en la tarjeta de la lista. */
    fun horaYDia(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String = "${hora(instante, zona)} · ${dia(instante, zona)}"

    /** `27/09 02:12`, para el historial y la hora de los datos de otro día. */
    fun fechaYHora(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String = FECHA_Y_HORA.format(instante.atZone(zona))

    /** `42 s`, `18 min 10 s`, `1 h 5 min`. Las alertas tienen resolución de segundos. */
    fun duracion(duracion: Duration): String {
        val segundos = duracion.seconds.coerceAtLeast(0)
        return when {
            segundos < 60 -> "$segundos s"
            segundos < 3600 -> {
                val minutos = segundos / 60
                val resto = segundos % 60
                if (resto == 0L) "$minutos min" else "$minutos min $resto s"
            }
            else -> {
                val horas = segundos / 3600
                val minutos = (segundos % 3600) / 60
                if (minutos == 0L) "$horas h" else "$horas h $minutos min"
            }
        }
    }

    /** `0,0412`: coma decimal y la cantidad de decimales pedida. */
    fun numero(valor: Double, decimales: Int = 4): String = String.format(LOCALE, "%.${decimales}f", valor)

    /** `192.168.30.50 · pc-rrhh`, o solo la IP si el catálogo no tiene el nombre. */
    fun ipYNombre(equipo: Equipo): String = if (equipo.nombre == null) equipo.ip else "${equipo.ip} · ${equipo.nombre}"

    /** `SSH · puerto 22` */
    fun servicioYPuerto(servicio: String, puerto: Int): String = "${servicio.uppercase(LOCALE)} · $puerto"
}
