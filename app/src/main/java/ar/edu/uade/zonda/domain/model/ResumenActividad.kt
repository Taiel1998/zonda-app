package ar.edu.uade.zonda.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
 * Los dos atajos del Resumen (Figma 52-2629 y 100-7024). Se cuentan por días del calendario del
 * teléfono, así cada barra es un día entero: 7 días son hoy y los 6 anteriores ("del 21/09 al 27/09",
 * de lunes a domingo en el Figma); 30 días arranca 30 días atrás, que es todo lo que guarda el teléfono
 * ("del 28/08 al 27/09").
 */
enum class PeriodoResumen(private val diasAtras: Long) {
    SIETE_DIAS(6),
    TREINTA_DIAS(30);

    fun tramo(hoy: LocalDate): Tramo = Tramo(hoy.minusDays(diasAtras), hoy)

    /** Los mismos días, de las 00:00 del primero a las 23:59 de hoy. */
    fun rango(hoy: LocalDate): RangoResumen = RangoResumen.dias(hoy.minusDays(diasAtras), hoy)

    /**
     * Las barras del gráfico. En 7 días, una por día. En 30 días, cuatro: las tres semanas que terminan
     * hoy y una primera con lo que queda del período (Figma 100-7024: 28/8, 7/9, 14/9 y 21/9).
     */
    fun barras(hoy: LocalDate): List<Franja> = rango(hoy).barras()

    companion object {
        /** Todo lo que guarda el teléfono: los días que se pueden elegir en "Elegí las fechas". */
        fun guardados(hoy: LocalDate): Tramo = TREINTA_DIAS.tramo(hoy)
    }
}

/**
 * Lo que abarca el Resumen (cambio 19): desde un día y una hora hasta otro día y otra hora, los dos al
 * minuto. "Hasta" incluye ese minuto: de 00:00 a 23:59 es el día entero. Los atajos de 7 y 30 días son
 * rangos de días enteros que terminan hoy.
 */
data class RangoResumen(val desde: LocalDateTime, val hasta: LocalDateTime) {

    /** El minuto siguiente a [hasta]: lo primero que ya no cuenta. */
    val fin: LocalDateTime
        get() = hasta.plusMinutes(1)

    val primerDia: LocalDate
        get() = desde.toLocalDate()

    val ultimoDia: LocalDate
        get() = hasta.toLocalDate()

    /** Cuántos días del calendario toca, contando el primero y el último. */
    val dias: Long
        get() = ChronoUnit.DAYS.between(primerDia, ultimoDia) + 1

    /** De las 00:00 del primer día a las 23:59 del último: el chip "Fechas" no lleva el relojito. */
    val diasEnteros: Boolean
        get() = desde.toLocalTime() == LocalTime.MIDNIGHT && hasta.toLocalTime() == FIN_DEL_DIA

    /** "Hasta" no puede quedar antes que "Desde" (en el mismo día, la hora). */
    val valido: Boolean
        get() = !hasta.isBefore(desde)

    operator fun contains(momento: LocalDateTime): Boolean = !momento.isBefore(desde) && momento.isBefore(fin)

    /**
     * Cómo se agrupa el gráfico (cambio 19): si dura un día o menos, por hora; hasta 14 días, por día;
     * más largo, por semana.
     */
    val escala: EscalaBarra
        get() = when {
            !Duration.between(desde, fin).minus(Duration.ofDays(1)).isPositive -> EscalaBarra.HORA
            dias <= DIAS_POR_DIA -> EscalaBarra.DIA
            else -> EscalaBarra.SEMANA
        }

    /**
     * Las barras del gráfico, de la primera a la última. Por hora, una por cada hora que toca el rango;
     * por día, una por día; por semana, semanas de 7 días que terminan en el último día, y la primera
     * se queda con lo que sobra, como en 30 días.
     */
    fun barras(): List<Franja> = when (escala) {
        EscalaBarra.HORA -> generateSequence(desde.truncatedTo(ChronoUnit.HOURS)) { it.plusHours(1) }
            .takeWhile { it.isBefore(fin) }
            .map { Franja.hora(it) }
            .toList()
        EscalaBarra.DIA -> (0 until dias).map { Franja.dia(primerDia.plusDays(it)) }
        EscalaBarra.SEMANA -> {
            val semanas = (dias / 7).toInt()
            val completas = (semanas - 2 downTo 0).map { atras ->
                Franja.semana(ultimoDia.minusDays(7L * atras + 6), ultimoDia.minusDays(7L * atras))
            }
            listOf(Franja.semana(primerDia, ultimoDia.minusDays(7L * (semanas - 1)))) + completas
        }
    }

    companion object {
        val FIN_DEL_DIA: LocalTime = LocalTime.of(23, 59)

        /** Hasta tantos días, el gráfico va por día; con más, por semana. */
        const val DIAS_POR_DIA = 14L

        /** Días enteros: de las 00:00 de [desde] a las 23:59 de [hasta]. */
        fun dias(desde: LocalDate, hasta: LocalDate) = RangoResumen(desde.atStartOfDay(), hasta.atTime(FIN_DEL_DIA))
    }
}

/** De qué es cada barra del gráfico del Resumen: una hora, un día o una semana (cambios 16 y 19). */
enum class EscalaBarra { HORA, DIA, SEMANA }

/**
 * Una barra del gráfico: desde (incluido) hasta (sin incluir), en la hora del teléfono. Una hora va de
 * las 22:00 a las 23:00; un día, de las 00:00 a las 00:00 del siguiente; una semana, de las 00:00 de su
 * primer día a las 00:00 del día después del último.
 */
data class Franja(val desde: LocalDateTime, val hasta: LocalDateTime, val escala: EscalaBarra) {

    operator fun contains(momento: LocalDateTime): Boolean = !momento.isBefore(desde) && momento.isBefore(hasta)

    /** El primer día de la barra: el que la nombra ("vie", "28/8"). */
    val primerDia: LocalDate
        get() = desde.toLocalDate()

    companion object {
        fun hora(desde: LocalDateTime) = Franja(desde, desde.plusHours(1), EscalaBarra.HORA)

        fun dia(dia: LocalDate) = Franja(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay(), EscalaBarra.DIA)

        fun semana(desde: LocalDate, hasta: LocalDate) = Franja(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(), EscalaBarra.SEMANA)
    }
}

/**
 * La revisión del filtro del Resumen (cambio 16). Va una sola: juntas no dicen nada nuevo (reales con
 * falsos son las revisadas, y revisadas con sin revisar son todas).
 */
enum class RevisionResumen {
    REVISADAS, SIN_REVISAR, REALES, FALSOS_POSITIVOS;

    fun incluye(alerta: Alerta): Boolean = when (this) {
        REVISADAS -> !alerta.sinRevisar
        SIN_REVISAR -> alerta.sinRevisar
        REALES -> alerta.estadoRevision == EstadoRevision.ATAQUE_REAL
        FALSOS_POSITIVOS -> alerta.estadoRevision == EstadoRevision.FALSO_POSITIVO
    }
}

/** Lo que se puede tocar en el Resumen (cambio 16): cada cifra suma o saca su filtro. */
sealed interface ToqueResumen {

    /** El total de alertas: saca todos los filtros. */
    data object Total : ToqueResumen

    /** Una barra del gráfico: un día, o una semana en 30 días. */
    data class Barra(val franja: Franja) : ToqueResumen

    /** Una fila de "Por tipo de ataque". */
    data class Tipo(val tipo: TipoAtaque) : ToqueResumen

    /** "altas revisadas en la primera hora": los tipos que la red tiene en alta. */
    data object Altas : ToqueResumen

    /** "revisadas", "sin revisar", la cobertura, el tiempo hasta la revisión, reales o falsos positivos. */
    data class Revision(val revision: RevisionResumen) : ToqueResumen

    /** Una fila de la precisión: la IA, los umbrales o las reglas. */
    data class Metodo(val metodo: MetodoDeteccion) : ToqueResumen
}

/**
 * Los filtros del Resumen (cambio 16), que se suman: con el domingo y fuerza bruta quedan las de fuerza
 * bruta del domingo. Los días (o semanas) y los tipos van de a varios, como casillas: una alerta empieza
 * un solo día y tiene un solo tipo, así que "domingo y lunes" o "fuerza bruta y escaneo" se leen de una
 * sola manera ("uno u otro"). La revisión y el modelo van de a uno: dos revisiones no dicen nada nuevo y
 * una alerta puede tener dos modelos, así que "IA y umbrales" se leería de dos maneras.
 */
data class FiltroResumen(
    val barras: Set<Franja> = emptySet(),
    val tipos: Set<TipoAtaque> = emptySet(),
    val revision: RevisionResumen? = null,
    val metodo: MetodoDeteccion? = null,
) {
    /** Cuántos hay puestos: lo que dice el botón "Filtros (4)". */
    val cantidad: Int
        get() = barras.size + tipos.size + (if (revision == null) 0 else 1) + (if (metodo == null) 0 else 1)

    val vacio: Boolean
        get() = cantidad == 0

    /**
     * Si la alerta pasa todos los filtros. Una que vieron las reglas y los umbrales tiene dos tipos
     * (cambio 9): con los dos elegidos entra una sola vez.
     */
    fun incluye(alerta: Alerta, zona: ZoneId): Boolean {
        if (barras.isNotEmpty()) {
            val momento = alerta.inicio.atZone(zona).toLocalDateTime()
            if (barras.none { momento in it }) return false
        }
        if (tipos.isNotEmpty() && tipos.none { it in alerta.tiposDeAtaque }) return false
        if (revision != null && !revision.incluye(alerta)) return false
        if (metodo != null && metodo !in alerta.metodos) return false
        return true
    }

    /**
     * Lo que pasa al tocar una cifra: los días y los tipos se suman o se sacan, la revisión y el modelo
     * reemplazan al que estaba (o se sacan si se toca el mismo), y el total saca todo. [altas] son los
     * tipos que la red tiene en alta: tocar "altas revisadas en la primera hora" los suma, o los saca si
     * ya estaban todos.
     */
    fun tocar(toque: ToqueResumen, altas: Set<TipoAtaque>): FiltroResumen = when (toque) {
        ToqueResumen.Total -> FiltroResumen()
        is ToqueResumen.Barra -> copy(barras = barras.alternar(toque.franja))
        is ToqueResumen.Tipo -> copy(tipos = tipos.alternar(toque.tipo))
        ToqueResumen.Altas -> copy(tipos = if (altas.isNotEmpty() && tipos.containsAll(altas)) tipos - altas else tipos + altas)
        is ToqueResumen.Revision -> copy(revision = toque.revision.takeUnless { it == revision })
        is ToqueResumen.Metodo -> copy(metodo = toque.metodo.takeUnless { it == metodo })
    }

    /** Al cambiar de período, los días elegidos eran del otro: se sacan y queda lo demás. */
    fun sinBarras(): FiltroResumen = copy(barras = emptySet())

    /**
     * Si los tipos elegidos son justo los que la red tiene en alta: la métrica de las altas queda
     * resaltada y la frase los nombra como "las altas".
     */
    fun tiposSonLasAltas(altas: Set<TipoAtaque>): Boolean = altas.isNotEmpty() && tipos == altas

    private fun <T> Set<T>.alternar(elemento: T): Set<T> = if (elemento in this) this - elemento else this + elemento
}

/** Una barra del gráfico de actividad y cuántas alertas empezaron en ella. */
data class BarraActividad(val franja: Franja, val cantidad: Int)

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
 * Resumen de actividad de 7 días, 30 días o las fechas elegidas (RF07, cambio 19). Se calcula en el
 * teléfono a partir de las alertas guardadas, por eso funciona sin conexión (RNF01).
 */
data class ResumenActividad(
    /** El atajo elegido, o null con fechas que no son ni 7 ni 30 días. */
    val periodo: PeriodoResumen?,
    /** Del primer al último día ("del 21/09 al 27/09"). */
    val tramo: Tramo,
    /** Los filtros con los que se calculó todo; vacío si son todas las alertas del período. */
    val filtro: FiltroResumen,
    /** Las alertas del período antes de aplicar los filtros; con cero, la pantalla muestra el estado vacío. */
    val totalDelPeriodo: Int,
    val total: Int,
    val revisadas: Int,
    val sinRevisar: Int,
    val reales: Int,
    val falsosPositivos: Int,
    /** Una barra por hora, por día o por semana ([RangoResumen.escala]), con las que quedaron en cero. */
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
    /** Las alertas que pasan los filtros, de la más nueva a la más vieja: la lista "Ver estas alertas". */
    val alertas: List<Alerta> = emptyList(),
    /** Todas las del período, sin filtros: la hoja de filtros cuenta con ellas cuántas quedarían. */
    val delPeriodo: List<Alerta> = emptyList(),
    /** Lo que abarca, con las horas (cambio 19). */
    val rango: RangoResumen = RangoResumen.dias(tramo.desde, tramo.hasta),
    /** El día del teléfono al calcular: su barra va en el verde base. */
    val hoy: LocalDate = tramo.hasta,
    /** Todas las alertas guardadas: "Elegí las fechas" cuenta con ellas cuántas quedarían con otras fechas. */
    val todas: List<Alerta> = emptyList(),
) {
    /** Por hora, por día o por semana. */
    val escala: EscalaBarra
        get() = rango.escala

    /**
     * Cuántas alertas quedarían con [otro] rango y los mismos filtros, menos los días elegidos, que eran
     * de las fechas de antes (cambio 19): lo que dice "Mostrar N alertas" de "Elegí las fechas".
     */
    fun cuantasEn(otro: RangoResumen, zona: ZoneId): Int {
        val sinBarras = filtro.sinBarras()
        return todas.count { it.momento(zona) in otro && sinBarras.incluye(it, zona) }
    }

    companion object {

        /**
         * Con menos revisiones que esto, la precisión de un modelo todavía no se puede comparar con la de
         * los otros: su barra va clara (cambio 4). Se cuenta por modelo.
         */
        const val REVISIONES_PARA_COMPARAR = 30

        /** Calcula el resumen del [periodo] que termina hoy. */
        fun calcular(
            alertas: List<Alerta>,
            periodo: PeriodoResumen,
            ahora: Instant,
            zona: ZoneId,
            filtro: FiltroResumen = FiltroResumen(),
        ): ResumenActividad = calcular(alertas, periodo.rango(ahora.atZone(zona).toLocalDate()), ahora, zona, filtro, periodo)

        /**
         * Calcula el resumen de [rango]. Solo cuentan las alertas que empezaron dentro; el que llama puede
         * pasar más y acá se descartan. Con [filtro], todas las cifras se calculan solo sobre las alertas
         * que lo pasan. [periodo] es el atajo que corresponde, si hay uno.
         */
        fun calcular(
            alertas: List<Alerta>,
            rango: RangoResumen,
            ahora: Instant,
            zona: ZoneId,
            filtro: FiltroResumen = FiltroResumen(),
            periodo: PeriodoResumen? = null,
        ): ResumenActividad {
            val hoy = ahora.atZone(zona).toLocalDate()
            val tramo = Tramo(rango.primerDia, rango.ultimoDia)
            val delPeriodo = alertas.filter { it.momento(zona) in rango }.sortedByDescending { it.inicio }
            val lista = if (filtro.vacio) delPeriodo else delPeriodo.filter { filtro.incluye(it, zona) }
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

            val barras = rango.barras().map { franja -> BarraActividad(franja, lista.count { it.momento(zona) in franja }) }

            return ResumenActividad(
                periodo = periodo,
                tramo = tramo,
                filtro = filtro,
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
                alertas = lista,
                delPeriodo = delPeriodo,
                rango = rango,
                hoy = hoy,
                todas = alertas,
            )
        }

        /**
         * La barra con más alertas (si empatan, la más reciente), el tipo que más se repite en ella (si
         * empatan, el primero en pantalla) y si todas las de ese tipo, siendo dos o más, fueron al mismo equipo.
         */
        private fun destacado(lista: List<Alerta>, barras: List<BarraActividad>, zona: ZoneId): Destacado? {
            val barra = barras
                .filter { it.cantidad > 0 }
                .maxWithOrNull(compareBy<BarraActividad>({ it.cantidad }, { it.franja.desde }))
                ?: return null
            val (tipo, delTipo) = lista
                .filter { it.momento(zona) in barra.franja }
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

/** El día y la hora en que empezó la alerta, en la zona del teléfono. */
private fun Alerta.momento(zona: ZoneId): LocalDateTime = inicio.atZone(zona).toLocalDateTime()

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
