package ar.edu.uade.zonda.domain.model

/**
 * Cómo avisa Telegram una alerta nueva de cada severidad (RF05; Figma 51-1688). Un bot de Telegram
 * solo puede mandar el mensaje con sonido o en silencio (cambio 6): la vibración y el volumen los
 * decide el teléfono. [SILENCIO] antes se llamaba "con vibración" y la API lo sigue llamando así.
 */
enum class ModoAviso { SONIDO, SILENCIO, SIN_AVISO }

/**
 * Preferencias de avisos de este teléfono. Por defecto solo la severidad alta interrumpe con sonido.
 * El servidor de cada red las guarda para que su bot las use (`PUT /telegram/avisos`).
 */
data class PreferenciasAvisos(
    val alta: ModoAviso = ModoAviso.SONIDO,
    val media: ModoAviso = ModoAviso.SILENCIO,
    val baja: ModoAviso = ModoAviso.SIN_AVISO,
) {
    fun para(severidad: Severidad): ModoAviso = when (severidad) {
        Severidad.ALTA -> alta
        Severidad.MEDIA -> media
        Severidad.BAJA -> baja
    }

    fun con(severidad: Severidad, modo: ModoAviso): PreferenciasAvisos = when (severidad) {
        Severidad.ALTA -> copy(alta = modo)
        Severidad.MEDIA -> copy(media = modo)
        Severidad.BAJA -> copy(baja = modo)
    }
}

/**
 * Cómo avisa Zonda con su propia notificación una alerta que no llegó por Telegram (1.0.4; Figma
 * 51-1688 y 463-70). Cada modo es un canal de Android, porque el sonido y la vibración de un canal no
 * se pueden cambiar después de crearlo. [EN_SILENCIO] queda en la barra de notificaciones sin sonar
 * ni vibrar; [SIN_AVISO] no muestra nada.
 */
enum class ModoNotificacion { SONIDO_Y_VIBRACION, SOLO_SONIDO, SOLO_VIBRACION, EN_SILENCIO, SIN_AVISO }

/**
 * Las notificaciones de Zonda de este teléfono, por severidad, y el recordatorio de las altas que
 * siguen sin revisar a los 15 minutos. De fábrica: alta con sonido y vibración, media solo con
 * vibración, baja sin aviso y el recordatorio prendido.
 */
data class PreferenciasNotificaciones(
    val alta: ModoNotificacion = ModoNotificacion.SONIDO_Y_VIBRACION,
    val media: ModoNotificacion = ModoNotificacion.SOLO_VIBRACION,
    val baja: ModoNotificacion = ModoNotificacion.SIN_AVISO,
    val recordatorio: Boolean = true,
) {
    fun para(severidad: Severidad): ModoNotificacion = when (severidad) {
        Severidad.ALTA -> alta
        Severidad.MEDIA -> media
        Severidad.BAJA -> baja
    }

    fun con(severidad: Severidad, modo: ModoNotificacion): PreferenciasNotificaciones = when (severidad) {
        Severidad.ALTA -> copy(alta = modo)
        Severidad.MEDIA -> copy(media = modo)
        Severidad.BAJA -> copy(baja = modo)
    }

    /**
     * El recordatorio suena como las altas de Zonda. Con las altas en "Sin aviso" (porque las avisa
     * Telegram) sale con sonido y vibración, lo de fábrica: si el recordatorio quedó prendido, es
     * porque la persona quiere que la avise.
     */
    val modoDelRecordatorio: ModoNotificacion
        get() = if (alta == ModoNotificacion.SIN_AVISO) ModoNotificacion.SONIDO_Y_VIBRACION else alta

    companion object {
        /** Los modos de la hoja, en su orden; "Sin aviso" es el interruptor apagado. */
        val MODOS_ELEGIBLES = listOf(
            ModoNotificacion.SONIDO_Y_VIBRACION,
            ModoNotificacion.SOLO_SONIDO,
            ModoNotificacion.SOLO_VIBRACION,
            ModoNotificacion.EN_SILENCIO,
        )

        /** Lo que queda si en la 1.0.3 se apagó "Notificaciones del sistema": nada de Zonda. */
        val NINGUNA = PreferenciasNotificaciones(
            alta = ModoNotificacion.SIN_AVISO,
            media = ModoNotificacion.SIN_AVISO,
            baja = ModoNotificacion.SIN_AVISO,
            recordatorio = false,
        )
    }
}

/** Servicios y nombres de equipos que entrega la API (`GET /catalogo`), para los filtros y para mostrar nombres. */
data class Catalogo(
    val equipos: Map<String, String>,
    val servicios: List<String>,
) {
    fun equipo(ip: String): Equipo = Equipo(ip, equipos[ip])

    companion object {
        val VACIO = Catalogo(emptyMap(), emptyList())
    }
}
