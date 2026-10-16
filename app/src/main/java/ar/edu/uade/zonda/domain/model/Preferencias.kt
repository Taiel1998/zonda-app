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
