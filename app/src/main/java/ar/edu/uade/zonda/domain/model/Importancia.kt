package ar.edu.uade.zonda.domain.model

/**
 * Qué tan importante es un equipo de la red (corrección 4, decisión 41). Todos arrancan en
 * [NORMAL]: hasta que un administrador marca uno, la app funciona como antes.
 */
enum class Importancia { CRITICO, NORMAL, POCO_IMPORTANTE }

/**
 * La severidad final de una alerta: la de su tipo de ataque, un nivel más si el equipo destino es
 * crítico y uno menos si es poco importante, con tope en alta y piso en baja. Pesan lo mismo: un solo
 * escalón, así una probable falsa alarma a un servidor crítico no sale en rojo. Es la misma tabla que
 * el servidor (`ajustar` en backend/app/semilla.py), así siempre coinciden:
 *
 * | Tipo de ataque | Crítico | Normal | Poco importante |
 * |----------------|---------|--------|-----------------|
 * | Alta           | Alta    | Alta   | Media           |
 * | Media          | Alta    | Media  | Baja            |
 * | Baja           | Media   | Baja   | Baja            |
 */
fun Severidad.ajustada(importancia: Importancia): Severidad = when (importancia) {
    Importancia.NORMAL -> this
    Importancia.CRITICO -> when (this) {
        Severidad.BAJA -> Severidad.MEDIA
        Severidad.MEDIA, Severidad.ALTA -> Severidad.ALTA
    }
    Importancia.POCO_IMPORTANTE -> when (this) {
        Severidad.ALTA -> Severidad.MEDIA
        Severidad.MEDIA, Severidad.BAJA -> Severidad.BAJA
    }
}

/** Un equipo de la red con nombre en el catálogo y su importancia (Ajustes → Equipos, Figma 468-3089). */
data class EquipoDeLaRed(
    val ip: String,
    val nombre: String,
    val importancia: Importancia,
)

/**
 * Cómo se calculó la severidad de una alerta (Figma 468-1086): la del tipo, la importancia del
 * equipo destino y el resultado.
 */
data class CalculoSeveridad(
    val delTipo: Severidad,
    val equipo: String,
    val importancia: Importancia,
) {
    val final: Severidad
        get() = delTipo.ajustada(importancia)

    /** El equipo cambia algo (o lo cambiaría si no estuviera en el tope): la tarjeta y la línea aparecen solo si no es normal. */
    val conImportancia: Boolean
        get() = importancia != Importancia.NORMAL

    /** 1 si subió un nivel, -1 si bajó y 0 si quedó igual (en los topes: "ya era alta"). */
    val cambio: Int
        get() = delTipo.ordinal.compareTo(final.ordinal)

    companion object {
        /**
         * El cálculo de [alerta] con las severidades de la red y la importancia de sus equipos. Una alerta
         * que vieron las reglas y los umbrales sin la IA toma la más alta de sus dos tipos (cambio 9).
         */
        fun de(alerta: Alerta, severidades: SeveridadPorTipo, importancias: Map<String, Importancia>): CalculoSeveridad =
            CalculoSeveridad(
                delTipo = alerta.tiposDeAtaque.map { severidades.severidadDe(it) }.minBy { it.ordinal },
                equipo = alerta.destino.etiqueta,
                importancia = importancias[alerta.destino.ip] ?: Importancia.NORMAL,
            )
    }
}
