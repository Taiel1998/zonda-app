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

/** Unos días seguidos del calendario, con los dos extremos incluidos: un día solo o una semana del gráfico. */
data class Tramo(val desde: LocalDate, val hasta: LocalDate) {
    val unSoloDia: Boolean
        get() = desde == hasta

    operator fun contains(dia: LocalDate): Boolean = !dia.isBefore(desde) && !dia.isAfter(hasta)
}

/**
 * Los dos períodos del Resumen (Figma 52-2629 y 100-7024). Se cuentan por días del calendario del
 * teléfono, así cada barra es un día entero: 7 días son hoy y los 6 anteriores ("del 21/09 al 27/09",
 * de lunes a domingo en el Figma); 30 días arranca 30 días atrás, que es todo lo que guarda el teléfono
 * ("del 28/08 al 27/09").
 */
enum class PeriodoResumen(private val diasAtras: Long) {
    SIETE_DIAS(6),
    TREINTA_DIAS(30);

    fun tramo(hoy: LocalDate): Tramo = Tramo(hoy.minusDays(diasAtras), hoy)

    /**
     * Los días de cada barra del gráfico. En 7 días, una por día. En 30 días, cuatro: las tres semanas
     * que terminan hoy y una primera con lo que queda del período (Figma 100-7024: 28/8, 7/9, 14/9 y 21/9).
     */
    fun barras(hoy: LocalDate): List<Tramo> = when (this) {
        SIETE_DIAS -> (6L downTo 0L).map { atras -> hoy.minusDays(atras).let { dia -> Tramo(dia, dia) } }
        TREINTA_DIAS -> listOf(
            Tramo(tramo(hoy).desde, hoy.minusDays(21)),
            Tramo(hoy.minusDays(20), hoy.minusDays(14)),
            Tramo(hoy.minusDays(13), hoy.minusDays(7)),
            Tramo(hoy.minusDays(6), hoy),
        )
    }
}

/**
 * La cifra que se tocó en el Resumen (RF07; Figma 100-7158 a 100-11534). Recalcular sobre ese grupo es
 * quedarse con las alertas que [incluye] y volver a calcular todo; por eso, con un día elegido, los
 * demás días quedan en cero, igual que en el Figma.
 */
sealed interface GrupoResumen {

    fun incluye(alerta: Alerta, zona: ZoneId): Boolean

    /** "revisadas", "hasta la primera revisión" y la cobertura: las que alguien ya marcó como real o falso positivo. */
    data object Revisadas : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = !alerta.sinRevisar
    }

    data object SinRevisar : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = alerta.sinRevisar
    }

    /** "altas revisadas en la primera hora": todas las altas, revisadas o no. */
    data object Altas : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = alerta.severidad == Severidad.ALTA
    }

    /** Una barra del gráfico: un día en 7 días o una semana en 30 días. */
    data class Barra(val tramo: Tramo) : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = alerta.dia(zona) in tramo
    }

    /**
     * Una fila de "Por tipo de ataque": las alertas de ese tipo (cambio 14: ya no hay "Otros"). Una que
     * vieron las reglas y los umbrales está en las dos filas (cambio 9).
     */
    data class Tipo(val tipo: TipoAtaque) : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = tipo in alerta.tiposDeAtaque
    }

    data object Reales : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = alerta.estadoRevision == EstadoRevision.ATAQUE_REAL
    }

    data object FalsosPositivos : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = alerta.estadoRevision == EstadoRevision.FALSO_POSITIVO
    }

    /** Una fila de la precisión: las alertas que ese método marcó. */
    data class Metodo(val metodo: MetodoDeteccion) : GrupoResumen {
        override fun incluye(alerta: Alerta, zona: ZoneId): Boolean = metodo in alerta.metodos
    }
}

/** Una barra del gráfico de actividad y cuántas alertas empezaron en sus días. */
data class BarraActividad(val tramo: Tramo, val cantidad: Int)

/**
 * Lo que dice la frase debajo del gráfico (Figma 52-2629 y 100-7024): la barra con más alertas, el tipo
 * que más se repite en ella y si todas las de ese tipo fueron al mismo equipo. Nunca lleva el nombre ni
 * la dirección del equipo (RNF02).
 */
data class Destacado(
    val barra: BarraActividad,
    val tipo: TipoAtaque,
    val delTipo: Int,
    val mismoDestino: Boolean,
)

/**
 * Resumen de actividad de 7 o 30 días (RF07). Se calcula en el teléfono a partir de las alertas
 * guardadas, por eso funciona sin conexión (RNF01).
 */
data class ResumenActividad(
    val periodo: PeriodoResumen,
    /** Del primer día del período a hoy ("del 21/09 al 27/09"). */
    val tramo: Tramo,
    /** El grupo sobre el que se calculó todo, o null si son todas las alertas del período. */
    val grupo: GrupoResumen?,
    /** Las alertas del período antes de aplicar el grupo; con cero, la pantalla muestra el estado vacío. */
    val totalDelPeriodo: Int,
    val total: Int,
    val revisadas: Int,
    val sinRevisar: Int,
    val reales: Int,
    val falsosPositivos: Int,
    /** Una barra por día (7 días) o por semana (30 días), con las que quedaron en cero. */
    val barras: List<BarraActividad>,
    /**
     * Cada tipo de ataque, en el orden de la pantalla de severidades y aunque esté en cero (cambio 14):
     * un tipo en cero también dice algo ("esta semana no hubo DDoS").
     */
    val porTipo: Map<TipoAtaque, Int>,
    /** Métrica de éxito 1: tiempo mediano entre la creación de una alerta y su primera revisión. */
    val tiempoMedianoHastaRevision: Duration?,
    /**
     * Métrica de éxito 2: altas revisadas dentro de la primera hora, sobre las altas que ya cumplieron
     * esa hora o que se revisaron antes.
     */
    val altasRevisadasEnUnaHora: Proporcion,
    /** Alertas revisadas sobre el total. */
    val cobertura: Proporcion,
    /** Por método: alertas reales sobre alertas revisadas entre las que ese método detectó. */
    val precisionPorMetodo: Map<MetodoDeteccion, Proporcion>,
    val destacado: Destacado?,
) {

    companion object {

        /**
         * Con menos revisiones que esto, la precisión de un modelo todavía no se puede comparar con la de
         * los otros: su barra va clara (cambio 4). Se cuenta por modelo.
         */
        const val REVISIONES_PARA_COMPARAR = 30

        /**
         * Calcula el resumen del [periodo] que termina hoy. Solo cuentan las alertas que empezaron dentro
         * del período; el que llama puede pasar más y acá se descartan. Con un [grupo], todas las cifras
         * se calculan solo sobre las alertas de ese grupo.
         */
        fun calcular(
            alertas: List<Alerta>,
            periodo: PeriodoResumen,
            ahora: Instant,
            zona: ZoneId,
            grupo: GrupoResumen? = null,
        ): ResumenActividad {
            val hoy = ahora.atZone(zona).toLocalDate()
            val tramo = periodo.tramo(hoy)
            val delPeriodo = alertas.filter { it.dia(zona) in tramo }
            val lista = if (grupo == null) delPeriodo else delPeriodo.filter { grupo.incluye(it, zona) }
            val revisadas = lista.filter { !it.sinRevisar }

            val tiemposHastaRevision = revisadas
                .mapNotNull { alerta -> alerta.primeraRevision()?.let { Duration.between(alerta.creadaEn, it) } }
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

            // Una alta cuenta cuando ya pasó su primera hora o cuando se revisó dentro de ella; una alta de
            // hace 20 minutos sin revisar todavía está a tiempo (Figma 100-8900: "ninguna alta con más de 1 h").
            val altasEnUnaHora = lista.count { it.severidad == Severidad.ALTA && it.revisadaEnLaPrimeraHora() }
            val altasConsideradas = lista.count { alerta ->
                alerta.severidad == Severidad.ALTA && (alerta.revisadaEnLaPrimeraHora() || Duration.between(alerta.creadaEn, ahora) > UNA_HORA)
            }

            val precisionPorMetodo = MetodoDeteccion.entries.associateWith { metodo ->
                val revisadasDelMetodo = revisadas.filter { metodo in it.metodos }
                Proporcion(
                    parte = revisadasDelMetodo.count { it.estadoRevision == EstadoRevision.ATAQUE_REAL },
                    total = revisadasDelMetodo.size,
                )
            }

            val barras = periodo.barras(hoy).map { dias -> BarraActividad(dias, lista.count { it.dia(zona) in dias }) }

            return ResumenActividad(
                periodo = periodo,
                tramo = tramo,
                grupo = grupo,
                totalDelPeriodo = delPeriodo.size,
                total = lista.size,
                revisadas = revisadas.size,
                sinRevisar = lista.size - revisadas.size,
                reales = revisadas.count { it.estadoRevision == EstadoRevision.ATAQUE_REAL },
                falsosPositivos = revisadas.count { it.estadoRevision == EstadoRevision.FALSO_POSITIVO },
                barras = barras,
                porTipo = TipoAtaque.EN_PANTALLA.associateWith { tipo -> lista.count { tipo in it.tiposDeAtaque } },
                tiempoMedianoHastaRevision = mediana,
                altasRevisadasEnUnaHora = Proporcion(altasEnUnaHora, altasConsideradas),
                cobertura = Proporcion(revisadas.size, lista.size),
                precisionPorMetodo = precisionPorMetodo,
                destacado = destacado(lista, barras, zona),
            )
        }

        /**
         * La barra con más alertas (si empatan, la más reciente), el tipo que más se repite en ella (si
         * empatan, el primero en pantalla) y si todas las de ese tipo, siendo dos o más, fueron al mismo equipo.
         */
        private fun destacado(lista: List<Alerta>, barras: List<BarraActividad>, zona: ZoneId): Destacado? {
            val barra = barras
                .filter { it.cantidad > 0 }
                .maxWithOrNull(compareBy<BarraActividad>({ it.cantidad }, { it.tramo.desde }))
                ?: return null
            val (tipo, delTipo) = lista
                .filter { it.dia(zona) in barra.tramo }
                .flatMap { alerta -> alerta.tiposDeAtaque.map { it to alerta } }
                .groupBy({ it.first }, { it.second })
                .entries
                .maxWith(compareBy<Map.Entry<TipoAtaque, List<Alerta>>>({ it.value.size }, { -TipoAtaque.EN_PANTALLA.indexOf(it.key) }))
            return Destacado(
                barra = barra,
                tipo = tipo,
                delTipo = delTipo.size,
                mismoDestino = delTipo.size >= 2 && delTipo.map { it.destino.ip }.distinct().size == 1,
            )
        }
    }
}

private val UNA_HORA: Duration = Duration.ofHours(1)

/** El día del calendario en que empezó la alerta, en la zona del teléfono. */
private fun Alerta.dia(zona: ZoneId): LocalDate = inicio.atZone(zona).toLocalDate()

/**
 * Cuándo se revisó por primera vez: la revisión más vieja del historial, con la hora del teléfono que
 * la hizo (decisión 21). Si después se cambió, el tiempo hasta la revisión sigue siendo el de la primera.
 */
private fun Alerta.primeraRevision(): Instant? = historial.ifEmpty { listOfNotNull(revisionActual) }.minOfOrNull { it.hechaEn }

/** Si se revisó (y sigue revisada) dentro de la primera hora desde que se creó. */
private fun Alerta.revisadaEnLaPrimeraHora(): Boolean {
    val primera = primeraRevision() ?: return false
    return !sinRevisar && Duration.between(creadaEn, primera) <= UNA_HORA
}
