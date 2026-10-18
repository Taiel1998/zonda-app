package ar.edu.uade.zonda.domain.model

import kotlin.math.roundToInt

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

/**
 * Lo que el detalle dice de cada método, con palabras y en la misma escala para la IA y los umbrales
 * (correcciones 5 y 6, decisión 37). Sale del veredicto de la plataforma y del cociente entre el valor
 * del método y su límite, así nunca contradice la etiqueta ANÓMALO o NORMAL. Los números siguen en la
 * alerta: la pantalla ya no los muestra.
 */
sealed interface LecturaVeredicto {
    /** "12 veces más de lo normal": el cociente redondeado al entero más cercano, desde el doble. */
    data class VecesMas(val veces: Int) : LecturaVeredicto

    /** "Un poco más de lo normal": lo marcó, pero no llega al doble. */
    data object UnPocoMas : LecturaVeredicto

    /** "Dentro de lo normal": no lo marcó. */
    data object DentroDeLoNormal : LecturaVeredicto

    /** "Servicio nuevo en la red": los umbrales no tenían línea base para ese servicio (el límite efectivo es 1). */
    data object ServicioNuevo : LecturaVeredicto

    /** Las reglas: el servicio está en la lista de autorizados. */
    data object ServicioAutorizado : LecturaVeredicto

    /** Las reglas: el servicio no está en la lista de autorizados (el destino y el puerto ya están en los datos del flujo). */
    data object ServicioNoAutorizado : LecturaVeredicto

    companion object {
        /** Desde el doble del límite se dice cuántas veces; antes, "un poco más". */
        const val DESDE_VECES = 2.0

        /** Un método que compara [valor] contra su [limite] y lo marcó si [anomalo]. */
        fun de(anomalo: Boolean, valor: Double, limite: Double): LecturaVeredicto {
            if (!anomalo) return DentroDeLoNormal
            val cociente = valor / limite
            return if (limite > 0 && cociente >= DESDE_VECES) VecesMas(cociente.roundToInt()) else UnPocoMas
        }
    }
}

/** El modelo de IA: el error de reconstrucción dividido su umbral (0,0377 / 0,0031 = 12 veces). */
val VeredictoIa.lectura: LecturaVeredicto
    get() = LecturaVeredicto.de(anomalo, errorReconstruccion, umbral)

/** Los umbrales: las conexiones en 60 s divididas el máximo de la línea base (58 / 2 = 29 veces). */
val VeredictoUmbrales.lectura: LecturaVeredicto
    get() = when {
        // Sin marcarla, "Dentro de lo normal" aunque el servicio sea nuevo: el texto no contradice la etiqueta.
        !anomalo -> LecturaVeredicto.DentroDeLoNormal
        servicioSinLineaBase -> LecturaVeredicto.ServicioNuevo
        else -> LecturaVeredicto.de(anomalo, contexto60s.toDouble(), umbralServicio.toDouble())
    }

/** Las reglas: si el servicio está en la lista de autorizados de la empresa. */
val VeredictoReglas.lectura: LecturaVeredicto
    get() = if (enCatalogo) LecturaVeredicto.ServicioAutorizado else LecturaVeredicto.ServicioNoAutorizado
