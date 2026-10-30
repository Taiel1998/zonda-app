package ar.edu.uade.zonda.domain.model

import java.time.Instant

/** Cómo está una pieza de la plataforma de detección en la tarjeta "Estado del sistema". */
enum class EstadoPieza {
    FUNCIONA,

    /**
     * Solo el sensor: Zeek anda, pero perdió más del 1 % de lo que pasó por el puerto espejo en su última
     * medición (por ejemplo, porque el espejo se satura). Va en amarillo: "Viendo parcialmente".
     */
    PARCIAL,
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
 * El color del semáforo de una pieza (decisión 48): verde si funciona, ámbar si el sensor ve parcialmente y
 * rojo en lo demás, también si no se sabe.
 */
val EstadoPieza.semaforo: Semaforo
    get() = when (this) {
        EstadoPieza.FUNCIONA -> Semaforo.VERDE
        EstadoPieza.PARCIAL -> Semaforo.AMARILLO
        EstadoPieza.DETENIDO, EstadoPieza.SIN_DATOS, EstadoPieza.NO_RESPONDE -> Semaforo.ROJO
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
    /** El porcentaje de pérdida de captura de la última medición de Zeek (cada 15 minutos); null si no se sabe. */
    val perdidaCaptura: Double? = null,
    /** Cuándo fue esa medición, en el reloj del teléfono. */
    val perdidaMedidaEn: Instant? = null,
    /** En esa medición no hubo tráfico TCP: Zeek no tuvo con qué medir la pérdida. */
    val sinTraficoParaMedir: Boolean = false,
    /** La pérdida salió del archivo de prueba del laboratorio, no de Zeek. */
    val perdidaSimulada: Boolean = false,
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
