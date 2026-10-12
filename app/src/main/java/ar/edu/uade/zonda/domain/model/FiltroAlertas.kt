package ar.edu.uade.zonda.domain.model

import java.time.Duration
import java.time.Instant

/** Período de la lista de alertas (RF02) y del Resumen (RF07). */
enum class Periodo(val horas: Long?) {
    ULTIMAS_24_HORAS(24),
    ULTIMOS_7_DIAS(7 * 24),
    ULTIMOS_30_DIAS(30 * 24),
    TODO(null);

    /** Instante desde el que cuenta el período, o null si abarca todo lo guardado. */
    fun desde(ahora: Instant): Instant? = horas?.let { ahora.minus(Duration.ofHours(it)) }
}

/**
 * Filtros de la lista de alertas (RF02). Un valor nulo significa "todas". Por defecto no filtra nada:
 * ninguna alerta se oculta por defecto (docs/decisiones.md, decisión 4).
 */
data class FiltroAlertas(
    val estado: EstadoRevision? = null,
    val severidad: Severidad? = null,
    val periodo: Periodo = Periodo.TODO,
    val servicio: String? = null,
    val destinoIp: String? = null,
    val metodo: MetodoDeteccion? = null,
) {
    val activo: Boolean
        get() = this != TODAS

    companion object {
        val TODAS = FiltroAlertas()

        /** El filtro que aplica un contador de la pantalla Estado: solo las sin revisar de esa severidad (RF01, criterio 4). */
        fun sinRevisarDe(severidad: Severidad) = FiltroAlertas(estado = EstadoRevision.SIN_REVISAR, severidad = severidad)
    }
}
