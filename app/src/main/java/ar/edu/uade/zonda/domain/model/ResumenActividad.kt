package ar.edu.uade.zonda.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Una cifra "x de y", por ejemplo 27 alertas reales de 33 revisadas. [valor] es la fracción, o null si no hay base. */
data class Proporcion(val parte: Int, val total: Int) {
    val valor: Double?
        get() = if (total == 0) null else parte.toDouble() / total
}

data class AlertasPorDia(val dia: LocalDate, val cantidad: Int)

/**
 * Resumen de actividad de 7 o 30 días (RF07). Se calcula en el teléfono a partir de las alertas
 * guardadas, por eso funciona sin conexión.
 */
data class ResumenActividad(
    val periodo: Periodo,
    val desde: Instant,
    val hasta: Instant,
    val total: Int,
    val porDia: List<AlertasPorDia>,
    val porTipo: Map<TipoAtaque, Int>,
    val porSeveridad: Map<Severidad, Int>,
    val reales: Int,
    val falsosPositivos: Int,
    val sinRevisar: Int,
    /** Métrica de éxito 1: tiempo mediano entre la creación de una alerta y su revisión. */
    val tiempoMedianoHastaRevision: Duration?,
    /** Métrica de éxito 2: alertas altas revisadas dentro de la primera hora, sobre el total de altas. */
    val altasRevisadasEnUnaHora: Proporcion,
    /** Alertas revisadas sobre el total. */
    val cobertura: Proporcion,
    /** Por método: alertas reales sobre alertas revisadas entre las que ese método detectó. */
    val precisionPorMetodo: Map<MetodoDeteccion, Proporcion>,
) {
    companion object {
        /**
         * Calcula el resumen sobre las alertas dadas. Solo cuenta las que empezaron dentro del período;
         * el que llama puede pasar más y acá se descartan.
         */
        fun calcular(
            alertas: List<Alerta>,
            periodo: Periodo,
            hasta: Instant,
            zona: ZoneId,
        ): ResumenActividad {
            val desde = periodo.desde(hasta) ?: alertas.minOfOrNull { it.inicio } ?: hasta
            val delPeriodo = alertas.filter { !it.inicio.isBefore(desde) && !it.inicio.isAfter(hasta) }
            val revisadas = delPeriodo.filter { it.revisionActual != null && !it.sinRevisar }
            val reales = revisadas.count { it.estadoRevision == EstadoRevision.ATAQUE_REAL }
            val falsos = revisadas.count { it.estadoRevision == EstadoRevision.FALSO_POSITIVO }

            val tiemposHastaRevision = revisadas
                .mapNotNull { alerta -> alerta.revisionActual?.let { Duration.between(alerta.creadaEn, it.fecha) } }
                .filter { !it.isNegative }
                .sorted()
            val mediana = when {
                tiemposHastaRevision.isEmpty() -> null
                tiemposHastaRevision.size % 2 == 1 -> tiemposHastaRevision[tiemposHastaRevision.size / 2]
                else -> {
                    val a = tiemposHastaRevision[tiemposHastaRevision.size / 2 - 1]
                    val b = tiemposHastaRevision[tiemposHastaRevision.size / 2]
                    a.plus(b).dividedBy(2)
                }
            }

            val altas = delPeriodo.filter { it.severidad == Severidad.ALTA }
            val altasEnUnaHora = altas.count { alerta ->
                val revision = alerta.revisionActual
                revision != null && !alerta.sinRevisar &&
                    Duration.between(alerta.creadaEn, revision.fecha) <= Duration.ofHours(1)
            }

            val precisionPorMetodo = MetodoDeteccion.entries.associateWith { metodo ->
                val revisadasDelMetodo = revisadas.filter { metodo in it.metodos }
                Proporcion(
                    parte = revisadasDelMetodo.count { it.estadoRevision == EstadoRevision.ATAQUE_REAL },
                    total = revisadasDelMetodo.size,
                )
            }

            return ResumenActividad(
                periodo = periodo,
                desde = desde,
                hasta = hasta,
                total = delPeriodo.size,
                porDia = delPeriodo
                    .groupBy { it.inicio.atZone(zona).toLocalDate() }
                    .map { (dia, lista) -> AlertasPorDia(dia, lista.size) }
                    .sortedBy { it.dia },
                porTipo = delPeriodo.groupingBy { it.tipoAtaque }.eachCount(),
                porSeveridad = delPeriodo.groupingBy { it.severidad }.eachCount(),
                reales = reales,
                falsosPositivos = falsos,
                sinRevisar = delPeriodo.count { it.sinRevisar },
                tiempoMedianoHastaRevision = mediana,
                altasRevisadasEnUnaHora = Proporcion(altasEnUnaHora, altas.size),
                cobertura = Proporcion(revisadas.size, delPeriodo.size),
                precisionPorMetodo = precisionPorMetodo,
            )
        }
    }
}
