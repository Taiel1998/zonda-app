package ar.edu.uade.zonda.domain.model

import java.time.Instant

/** Cómo está una pieza de la plataforma de detección en la tarjeta "Estado del sistema". */
enum class EstadoPieza {
    FUNCIONA,
    DETENIDO,

    /**
     * No se sabe: el análisis en vivo nunca mandó su latido o el último ya venció. El sensor lo informa el
     * análisis, así que sin latido tampoco se sabe del sensor. En la tarjeta va en rojo, nunca en verde.
     */
    SIN_DATOS,

    /** El servidor no contestó la consulta (solo para el servidor). */
    NO_RESPONDE,
}

/**
 * El estado de la plataforma de detección de la red activa (`GET /salud` con token, API 0.7.0): el
 * sensor de red, el análisis en vivo, que cada 20 segundos lee el `conn.log` de Zeek y le manda un
 * latido al servidor, y el servidor de alertas.
 *
 * Las horas están en el reloj del teléfono: [ultimaCorrida] es la hora de la consulta menos los segundos
 * que contó el servidor desde el último latido, así un reloj corrido no cambia "Última corrida hace 12 s".
 */
data class EstadoSistema(
    val sensor: EstadoPieza,
    val analisis: EstadoPieza,
    val servidor: EstadoPieza,
    /** Cuándo llegó el último latido del análisis en vivo; null si nunca llegó ninguno. */
    val ultimaCorrida: Instant?,
    /** La hora del flujo más nuevo que vio el sensor; null si no se sabe. */
    val ultimoFlujo: Instant?,
    /** Cuándo contestó el servidor (o cuándo se dejó de esperarlo). */
    val consultadoEn: Instant,
) {
    companion object {
        /** El servidor no contestó: del sensor y del análisis no se sabe nada. */
        fun sinRespuesta(consultadoEn: Instant) = EstadoSistema(
            sensor = EstadoPieza.SIN_DATOS,
            analisis = EstadoPieza.SIN_DATOS,
            servidor = EstadoPieza.NO_RESPONDE,
            ultimaCorrida = null,
            ultimoFlujo = null,
            consultadoEn = consultadoEn,
        )
    }
}
