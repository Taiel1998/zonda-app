package ar.edu.uade.zonda.domain.model

/** Cómo avisa la app cuando la sincronización trae alertas nuevas de cada severidad (RF05). */
enum class ModoAviso { SONIDO, VIBRACION, SIN_AVISO }

/** Preferencias de avisos de este teléfono. Por defecto solo la severidad alta interrumpe con sonido. */
data class PreferenciasAvisos(
    val alta: ModoAviso = ModoAviso.SONIDO,
    val media: ModoAviso = ModoAviso.VIBRACION,
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
