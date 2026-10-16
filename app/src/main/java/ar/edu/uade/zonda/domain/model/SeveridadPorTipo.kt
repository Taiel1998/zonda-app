package ar.edu.uade.zonda.domain.model

/**
 * Severidad elegida para cada tipo de ataque (RF05 y RF06). Vale para todos los teléfonos de la red,
 * por eso se guarda en el servidor con `PUT /severidades` y no como preferencia del teléfono.
 */
data class SeveridadPorTipo(val valores: Map<TipoAtaque, Severidad>) {

    /** Severidad del tipo dado; si la red no eligió una, la recomendada. */
    fun severidadDe(tipo: TipoAtaque): Severidad = valores[tipo] ?: RECOMENDADA.valores.getValue(tipo)

    fun con(tipo: TipoAtaque, severidad: Severidad): SeveridadPorTipo =
        SeveridadPorTipo(valores + (tipo to severidad))

    companion object {
        /** Valores recomendados que vienen marcados en el primer inicio (docs/decisiones.md, decisión 4). */
        val RECOMENDADA = SeveridadPorTipo(
            mapOf(
                TipoAtaque.FUERZA_BRUTA to Severidad.ALTA,
                TipoAtaque.SIN_TIPIFICAR to Severidad.ALTA,
                TipoAtaque.ESCANEO to Severidad.MEDIA,
                TipoAtaque.DDOS to Severidad.MEDIA,
                TipoAtaque.SERVICIO_NO_AUTORIZADO to Severidad.MEDIA,
                TipoAtaque.VOLUMEN_INUSUAL to Severidad.MEDIA,
                TipoAtaque.PROBABLE_FALSA_ALARMA to Severidad.BAJA,
            ),
        )
    }
}
