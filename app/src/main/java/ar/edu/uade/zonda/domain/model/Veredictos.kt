package ar.edu.uade.zonda.domain.model

/** Los tres métodos de detección de la plataforma. El detalle muestra el veredicto de cada uno (RF03). */
enum class MetodoDeteccion { IA, UMBRALES, REGLAS }

/** Modelo de IA: el error de reconstrucción del episodio superó el umbral calibrado. */
data class VeredictoIa(
    val anomalo: Boolean,
    val errorReconstruccion: Double,
    val umbral: Double,
)

/**
 * Umbrales estadísticos: la cantidad de conexiones del mismo origen al mismo servicio en 60 segundos
 * superó el máximo observado en la línea base para ese servicio. Si el servicio no aparecía en la
 * línea base, el umbral efectivo es 1 y [servicioSinLineaBase] lo indica.
 */
data class VeredictoUmbrales(
    val anomalo: Boolean,
    val contexto60s: Int,
    val umbralServicio: Int,
    val servicioSinLineaBase: Boolean,
)

/** Reglas estáticas: el par destino:puerto no figura en el catálogo de servicios autorizados. */
data class VeredictoReglas(
    val anomalo: Boolean,
    val parDestino: String,
    val enCatalogo: Boolean,
)

data class Veredictos(
    val ia: VeredictoIa,
    val umbrales: VeredictoUmbrales,
    val reglas: VeredictoReglas,
) {
    /** Métodos que marcaron el episodio como anómalo. Toda alerta tiene al menos uno. */
    val metodosQueDetectaron: Set<MetodoDeteccion>
        get() = buildSet {
            if (ia.anomalo) add(MetodoDeteccion.IA)
            if (umbrales.anomalo) add(MetodoDeteccion.UMBRALES)
            if (reglas.anomalo) add(MetodoDeteccion.REGLAS)
        }
}
