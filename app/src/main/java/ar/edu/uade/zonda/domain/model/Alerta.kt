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
) {
    val duracion: Duration
        get() = Duration.between(inicio, fin)

    val metodos: Set<MetodoDeteccion>
        get() = veredictos.metodosQueDetectaron

    val sinRevisar: Boolean
        get() = estadoRevision == EstadoRevision.SIN_REVISAR

    val pendienteDeEnvio: Boolean
        get() = envio != null && envio != EstadoEnvio.CONFLICTO
}
