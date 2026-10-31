package ar.edu.uade.zonda.domain.model

import java.time.Instant

/**
 * En qué quedó el bloqueo del origen de una alerta (decisión 51). Un analista lo pide y lo aprueba un
 * administrador; aprobado ya cuenta y entra al firewall en su consulta siguiente, y aplicado es cuando el
 * firewall dijo que lo puso. Rechazado, desbloqueado y vencido ya terminaron.
 */
enum class EstadoBloqueo {
    PEDIDO,
    APROBADO,
    APLICADO,
    RECHAZADO,
    DESBLOQUEADO,
    VENCIDO,
    ;

    /** El equipo tiene un bloqueo o un pedido abierto: no se le puede pedir otro. */
    val activo: Boolean
        get() = this == PEDIDO || this == APROBADO || this == APLICADO

    /** Corta el tráfico del equipo, o lo va a cortar en la consulta siguiente del firewall. */
    val enElFirewall: Boolean
        get() = this == APROBADO || this == APLICADO
}

/**
 * Las duraciones que acepta el servidor: 1 hora para contenerlo mientras se investiga, 24 horas si nadie lo
 * puede mirar hasta el día siguiente, y hasta desbloquear cuando ya se sabe que es un ataque. Siempre se
 * puede desbloquear antes.
 */
enum class DuracionBloqueo(val segundos: Int?) {
    UNA_HORA(3600),
    UN_DIA(86_400),
    HASTA_DESBLOQUEAR(null),
    ;

    companion object {
        /** La que viene marcada al abrir la hoja: un bloqueo vence solo salvo que alguien decida otra cosa. */
        val PREDETERMINADA = UNA_HORA

        fun de(segundos: Int?): DuracionBloqueo? = entries.firstOrNull { it.segundos == segundos }
    }
}

/** El bloqueo del origen de una alerta, como lo cuenta el servidor. Las horas son las del servidor. */
data class Bloqueo(
    val id: String,
    val alertaId: String,
    val ip: String,
    /** El nombre del equipo en el catálogo ("pc-rrhh"); null si no tiene. */
    val nombre: String?,
    val estado: EstadoBloqueo,
    /** Cuánto dura desde que se aprueba; null es hasta desbloquear. */
    val segundos: Int?,
    val motivo: String,
    val pedidoPor: String,
    val pedidoEn: Instant,
    val aprobadoPor: String?,
    val aprobadoEn: Instant?,
    /** Null en un pedido (la duración corre desde que se aprueba) y en uno hasta desbloquear. */
    val venceEn: Instant?,
    val aplicadoEn: Instant?,
    val terminadoPor: String?,
    val terminadoEn: Instant?,
    val detalle: String?,
) {
    val etiqueta: String
        get() = nombre ?: ip
}

/** El firewall de la red, según el servidor. Sin [habilitado], la red está en modo de observación. */
data class EstadoFirewall(
    val habilitado: Boolean,
    /** Preguntó hace menos de un minuto. */
    val conectado: Boolean,
    /** Cuándo preguntó por última vez; null si nunca. */
    val ultimaConsulta: Instant?,
    /** Las redes que el firewall nunca bloquea ("192.168.20.0/24"), según su último informe. */
    val nuncaBloquear: List<String>,
) {
    /** El firewall nunca bloquea [ip]: la app no ofrece el botón y el servidor no lo aceptaría. */
    fun nuncaBloquea(ip: String): Boolean = nuncaBloquear.any { enLaRed(ip, it) }
}

/** Los bloqueos de la red, de lo más nuevo a lo más viejo, y cómo está su firewall. */
data class BloqueosDeLaRed(val bloqueos: List<Bloqueo>, val firewall: EstadoFirewall) {

    /** El bloqueo o pedido abierto de [ip], si tiene. */
    fun activoDe(ip: String): Bloqueo? = bloqueos.firstOrNull { it.ip == ip && it.estado.activo }
}

/** Si la IPv4 [ip] está dentro de [red] ("192.168.20.0/24", o una dirección sola). Con IPv6 o algo raro, no. */
internal fun enLaRed(ip: String, red: String): Boolean {
    val partes = red.trim().split("/")
    val direccion = ipv4(ip) ?: return false
    val base = ipv4(partes[0]) ?: return false
    val prefijo = partes.getOrNull(1)?.toIntOrNull() ?: 32
    if (partes.size > 2 || prefijo !in 0..32) return false
    val mascara = if (prefijo == 0) 0L else (0xFFFF_FFFFL shl (32 - prefijo)) and 0xFFFF_FFFFL
    return direccion and mascara == base and mascara
}

private fun ipv4(texto: String): Long? {
    val octetos = texto.trim().split(".")
    if (octetos.size != 4) return null
    return octetos.fold(0L) { acumulado, octeto ->
        val valor = octeto.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        (acumulado shl 8) or valor.toLong()
    }
}
