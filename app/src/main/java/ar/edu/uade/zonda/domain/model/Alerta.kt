package ar.edu.uade.zonda.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Una alerta es un episodio: los flujos de red con el mismo origen y el mismo destino, agrupados
 * mientras no pasen más de unos minutos sin flujos nuevos (docs/decisiones.md, decisión 3).
 */
data class Alerta(
    val id: String,
    val redId: String,
    /** Cuándo la plataforma emitió la alerta. Se usa para las métricas de tiempo de revisión. */
    val creadaEn: Instant,
    val inicio: Instant,
    val fin: Instant,
    val cantidadFlujos: Int,
    val uidReferencia: String,
    val origen: Equipo,
    val destino: Equipo,
    val servicio: String,
    /** Protocolo de transporte ("tcp" o "udp"); null en las alertas guardadas antes de que la API lo mandara. */
    val protocolo: String? = null,
    /** Puerto más frecuente del episodio. */
    val puerto: Int,
    /** Puerto del flujo de referencia (el primero del episodio). */
    val puertoReferencia: Int,
    /** Cantidad de puertos distintos; en un escaneo son cientos. */
    val puertosDistintos: Int,
    val tipoAtaque: TipoAtaque,
    /** Fracción de votos del clasificador para el tipo asignado; vacía cuando el clasificador no corrió. */
    val confianzaTipo: Double?,
    val severidad: Severidad,
    val veredictos: Veredictos,
    val estadoRevision: EstadoRevision,
    val revisionActual: Revision?,
    val historial: List<Revision>,
    /** Estado de envío de la acción pendiente sobre esta alerta, si hay una. */
    val envio: EstadoEnvio?,
    /** Versión que lleva el servidor; sirve para detectar que otra persona la revisó antes (409). */
    val version: Int,
    val actualizadaEn: Instant,
    /** Cuándo salió el primer aviso de Telegram de esta alerta; null si no salió ninguno. */
    val avisoTelegram: Instant? = null,
    /** El responsable de ahora; null si está sin asignar (decisión 42). */
    val asignacion: Asignacion? = null,
    /** Cada cambio de responsable, del primero al último, para el historial del detalle. */
    val asignaciones: List<Asignacion> = emptyList(),
) {
    val duracion: Duration
        get() = Duration.between(inicio, fin)

    val metodos: Set<MetodoDeteccion>
        get() = veredictos.metodosQueDetectaron

    /**
     * Los tipos en los que cuenta la alerta: el suyo y, si la IA no la vio pero la marcaron las reglas y
     * los umbrales, los dos de lo que la IA no detectó. Así la cuenta el Resumen, y el servidor le da la
     * severidad más alta de las dos (cambio 9).
     */
    val tiposDeAtaque: Set<TipoAtaque>
        get() = if (tipoAtaque in TipoAtaque.NO_DETECTADOS_POR_LA_IA && veredictos.reglas.anomalo && veredictos.umbrales.anomalo) {
            TipoAtaque.NO_DETECTADOS_POR_LA_IA.toSet()
        } else {
            setOf(tipoAtaque)
        }

    val sinRevisar: Boolean
        get() = estadoRevision == EstadoRevision.SIN_REVISAR

    val pendienteDeEnvio: Boolean
        get() = envio != null && envio != EstadoEnvio.CONFLICTO
}
