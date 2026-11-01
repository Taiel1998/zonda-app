package ar.edu.uade.zonda.ui

import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.Proporcion
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Cómo se muestran fechas, duraciones y números en toda la app. Kotlin puro, sin Compose, para
 * probarlo con JUnit. Los textos van en español rioplatense (coma decimal, "dom 27/09").
 */
object Formato {

    private val LOCALE: Locale = Locale.forLanguageTag("es-AR")
    private const val SERVICIO_SIN_IDENTIFICAR = "otro"
    private val HORA = DateTimeFormatter.ofPattern("HH:mm", LOCALE)
    private val HORA_CON_SEGUNDOS = DateTimeFormatter.ofPattern("HH:mm:ss", LOCALE)
    private val DIA = DateTimeFormatter.ofPattern("EEE dd/MM", LOCALE)
    private val FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm", LOCALE)
    private val FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE)
    private val DIA_Y_MES = DateTimeFormatter.ofPattern("dd/MM", LOCALE)
    private val DIA_Y_MES_CORTO = DateTimeFormatter.ofPattern("d/M", LOCALE)
    private val DIA_Y_MES_ARCHIVO = DateTimeFormatter.ofPattern("dd-MM", LOCALE)

    /** `02:09` */
    fun hora(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String = HORA.format(instante.atZone(zona))

    /** `22:00`: una hora del día sin fecha, como las de "Desde" y "Hasta" del Resumen. */
    fun horaDelDia(hora: LocalTime): String = HORA.format(hora)

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

    /** `07/12/2026`, para fechas de vencimiento. */
    fun fecha(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String = FECHA.format(instante.atZone(zona))

    /** Cuántos días hay entre una fecha y otra (Detalles técnicos: "Vence 27/12/2026 (90 días)"). */
    fun diasEntre(desde: Instant, hasta: Instant): Long = Duration.between(desde, hasta).toDays()

    /** Si dos instantes caen el mismo día del calendario (para "Hoy 02:14"). */
    fun mismoDia(a: Instant, b: Instant, zona: ZoneId = ZoneId.systemDefault()): Boolean =
        a.atZone(zona).toLocalDate() == b.atZone(zona).toLocalDate()

    /** `hoy`, `ayer` o `dom 27/09`: el día de una revisión ("Celular Martín · hoy 02:12 · enviada"). */
    fun diaRelativo(instante: Instant, ahora: Instant, zona: ZoneId = ZoneId.systemDefault()): String = when {
        mismoDia(instante, ahora, zona) -> "hoy"
        ayer(instante, ahora, zona) -> "ayer"
        else -> dia(instante, zona)
    }

    /** Si [instante] cae el día anterior a [ahora] (para "Ayer 23:50"). */
    fun ayer(instante: Instant, ahora: Instant, zona: ZoneId = ZoneId.systemDefault()): Boolean =
        instante.atZone(zona).toLocalDate() == ahora.atZone(zona).toLocalDate().minusDays(1)

    /**
     * `5 min`, `3 h`, `2 días`: cuánto pasó desde [desde] hasta [ahora], para "Última alta sin revisar: hace
     * 5 min" en el semáforo. Menos de un minuto es `menos de 1 min`.
     */
    fun haceCuanto(desde: Instant, ahora: Instant): String {
        val minutos = Duration.between(desde, ahora).toMinutes().coerceAtLeast(0)
        val dias = minutos / (24 * 60)
        return when {
            minutos < 1 -> "menos de 1 min"
            minutos < 60 -> "$minutos min"
            dias < 1 -> "${minutos / 60} h"
            dias == 1L -> "1 día"
            else -> "$dias días"
        }
    }

    /**
     * La hora de los datos de "actualizado 02:14": sola si es de hoy, como el Figma, y con el día si no
     * (`ayer 23:50`, `dom 27/09 02:14`), para que datos viejos no parezcan de hoy.
     */
    fun horaDeLosDatos(instante: Instant, ahora: Instant, zona: ZoneId = ZoneId.systemDefault()): String =
        if (mismoDia(instante, ahora, zona)) hora(instante, zona) else "${diaRelativo(instante, ahora, zona)} ${hora(instante, zona)}"

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

    /**
     * `SSH`: el servicio en mayúsculas, como los protocolos. "otro" es lo que Zeek no pudo reconocer (un
     * escaneo al puerto 1, por ejemplo) y dice `Sin identificar`: "OTRO" al lado de "Los demás" confundía.
     */
    fun servicio(servicio: String): String =
        if (servicio.equals(SERVICIO_SIN_IDENTIFICAR, ignoreCase = true)) "Sin identificar" else servicio.uppercase(LOCALE)

    /** `SSH · 22`, como en la tarjeta de la lista. */
    fun servicioYPuerto(servicio: String, puerto: Int): String = "${this.servicio(servicio)} · $puerto"

    /** `SSH · TCP/22`, como en el detalle. Sin el protocolo (alertas guardadas antes de que la API lo mandara), `SSH · 22`. */
    fun servicioProtocoloYPuerto(servicio: String, protocolo: String?, puerto: Int): String =
        if (protocolo == null) servicioYPuerto(servicio, puerto) else "${this.servicio(servicio)} · ${protocolo.uppercase(LOCALE)}/$puerto"

    // Resumen (RF07)

    /** `27/09`: los extremos del período ("del 21/09 al 27/09"). */
    fun diaYMes(dia: LocalDate): String = DIA_Y_MES.format(dia)

    /** `28/8`: el comienzo de cada semana del gráfico de 30 días. */
    fun diaYMesCorto(dia: LocalDate): String = DIA_Y_MES_CORTO.format(dia)

    /** `21-09`: para el nombre de la imagen ("Resumen_21-09_27-09.png"). */
    fun diaYMesParaArchivo(dia: LocalDate): String = DIA_Y_MES_ARCHIVO.format(dia)

    /** `vie`: debajo de cada barra de 7 días. */
    fun diaDeLaSemanaCorto(dia: LocalDate): String =
        dia.dayOfWeek.getDisplayName(TextStyle.SHORT, LOCALE).replace(".", "").lowercase(LOCALE)

    /** `viernes`: "El viernes concentra 18 alertas". */
    fun diaDeLaSemana(dia: LocalDate): String = dia.dayOfWeek.getDisplayName(TextStyle.FULL, LOCALE).lowercase(LOCALE)

    /** `Viernes 25/09`: el chip del día elegido. */
    fun diaDeLaSemanaYFecha(dia: LocalDate): String =
        diaDeLaSemana(dia).replaceFirstChar { it.titlecase(LOCALE) } + " " + diaYMes(dia)

    /** `71 %`, redondeado y con espacio, como el Figma; null si no hay base. */
    fun porcentaje(proporcion: Proporcion): String? = proporcion.valor?.let { "${(it * 100).roundToInt()} %" }

    /** `14 min`, `1 h 20 min`, `2 d 3 h`: el tiempo hasta la primera revisión, sin segundos salvo que sea menos de un minuto. */
    fun duracionCorta(duracion: Duration): String {
        val segundos = duracion.seconds.coerceAtLeast(0)
        if (segundos < 60) return "$segundos s"
        val minutos = (segundos + 30) / 60
        return when {
            minutos < 60 -> "$minutos min"
            minutos < 24 * 60 -> if (minutos % 60 == 0L) "${minutos / 60} h" else "${minutos / 60} h ${minutos % 60} min"
            else -> {
                val horas = minutos / 60
                if (horas % 24 == 0L) "${horas / 24} d" else "${horas / 24} d ${horas % 24} h"
            }
        }
    }

    /** "IA", "IA y umbrales", "IA, umbrales y reglas": los métodos en una frase, en el detalle y en la tarjeta. */
    fun enumerar(partes: List<String>): String = when (partes.size) {
        0 -> ""
        1 -> partes.single()
        else -> partes.dropLast(1).joinToString(", ") + " y " + partes.last()
    }
}
