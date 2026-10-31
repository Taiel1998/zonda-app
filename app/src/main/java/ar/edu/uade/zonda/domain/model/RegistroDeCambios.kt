package ar.edu.uade.zonda.domain.model

import java.time.Instant

/**
 * Lo que anota el registro de cambios (corrección 3, decisión 40). Desde la API 0.9.0 también los bloqueos
 * del firewall y los desbloqueos (decisión 51); un cambio de duración es un bloqueo con [CambioRegistro.antes].
 */
enum class TipoCambio { SEVERIDAD, IMPORTANCIA, PERSONA_NUEVA, PERFIL, PERSONA_QUITADA, BLOQUEO, DESBLOQUEO }

/**
 * Una línea del registro de cambios de la red: quién cambió la configuración, cuándo, de qué a qué y
 * por qué. La guarda el servidor y desde la app nadie la puede borrar ni editar. [objeto] es el tipo de
 * ataque (`fuerza_bruta`), la IP del equipo o el id del teléfono de la persona.
 */
data class CambioRegistro(
    val id: Long,
    val fecha: Instant,
    val tipo: TipoCambio,
    /** null si el cambio salió de la terminal del servidor. */
    val autorId: String?,
    val autor: String,
    val objeto: String,
    val nombre: String?,
    val antes: String?,
    val despues: String?,
    val motivo: String?,
) {
    /** El tipo de ataque de un cambio de severidad (`fuerza_bruta` → FUERZA_BRUTA); null si no es uno conocido. */
    val tipoDeAtaque: TipoAtaque?
        get() = TipoAtaque.entries.firstOrNull { it.name == objeto.uppercase() }.takeIf { tipo == TipoCambio.SEVERIDAD }

    val severidadAntes: Severidad?
        get() = antes?.let { valor -> Severidad.entries.firstOrNull { it.name == valor.uppercase() } }

    val severidadDespues: Severidad?
        get() = despues?.let { valor -> Severidad.entries.firstOrNull { it.name == valor.uppercase() } }

    val importanciaAntes: Importancia?
        get() = antes?.let { valor -> Importancia.entries.firstOrNull { it.name == valor.uppercase() } }

    val importanciaDespues: Importancia?
        get() = despues?.let { valor -> Importancia.entries.firstOrNull { it.name == valor.uppercase() } }

    val perfilAntes: Perfil?
        get() = antes?.let { valor -> Perfil.entries.firstOrNull { it.name == valor.uppercase() } }

    val perfilDespues: Perfil?
        get() = despues?.let { valor -> Perfil.entries.firstOrNull { it.name == valor.uppercase() } }

    /** Los segundos de un bloqueo ("3600"); null si es hasta desbloquear o no es un bloqueo. */
    val segundosAntes: Int?
        get() = antes?.toIntOrNull()

    val segundosDespues: Int?
        get() = despues?.toIntOrNull()
}

/**
 * Un cambio de configuración que cambió el color de una alerta, para su historial (Figma 468-1198):
 * "Pasó de alta a media" / "Taiel cambió fuerza bruta: “El backup...”".
 */
data class CambioDeSeveridad(
    val fecha: Instant,
    val antes: Severidad,
    val despues: Severidad,
    /** La línea del registro que lo causó: un cambio de severidad del tipo o de importancia del equipo destino. */
    val cambio: CambioRegistro,
)

/**
 * Los cambios de configuración que cambiaron el color de [alerta] desde que llegó. El teléfono los arma
 * con el registro: la severidad con la que llegó una alerta es la de su tipo en ese momento. Se parte de
 * cómo están hoy las severidades y la importancia de su equipo y se recorre el registro hacia atrás,
 * deshaciendo cada cambio que la toca; los que no cambiaron su severidad final no aparecen.
 */
fun cambiosDeSeveridad(
    alerta: Alerta,
    registro: List<CambioRegistro>,
    severidades: SeveridadPorTipo,
    importancias: Map<String, Importancia>,
): List<CambioDeSeveridad> {
    val tipos = alerta.tiposDeAtaque
    val ip = alerta.destino.ip
    var porTipo = tipos.associateWith { severidades.severidadDe(it) }
    var importancia = importancias[ip] ?: Importancia.NORMAL
    fun final() = porTipo.values.minBy { it.ordinal }.ajustada(importancia)

    val eventos = mutableListOf<CambioDeSeveridad>()
    val relevantes = registro
        .filter { it.fecha.isAfter(alerta.creadaEn) }
        .filter { (it.tipo == TipoCambio.SEVERIDAD && it.tipoDeAtaque in tipos) || (it.tipo == TipoCambio.IMPORTANCIA && it.objeto == ip) }
        .sortedWith(compareByDescending<CambioRegistro> { it.fecha }.thenByDescending { it.id })
    for (cambio in relevantes) {
        val despues = final()
        when (cambio.tipo) {
            TipoCambio.SEVERIDAD -> {
                val tipo = cambio.tipoDeAtaque ?: continue
                val anterior = cambio.severidadAntes ?: continue
                porTipo = porTipo + (tipo to anterior)
            }
            else -> importancia = cambio.importanciaAntes ?: continue
        }
        val antes = final()
        if (antes != despues) eventos += CambioDeSeveridad(cambio.fecha, antes, despues, cambio)
    }
    return eventos.reversed()
}
