package ar.edu.uade.zonda.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
     * Las barras del gráfico. En 7 días, una por día. En 30 días, cuatro: las tres semanas que terminan
     * hoy y una primera con lo que queda del período (Figma 100-7024: 28/8, 7/9, 14/9 y 21/9).
     */
    fun barras(hoy: LocalDate): List<Franja> = when (this) {
        SIETE_DIAS -> (6L downTo 0L).map { atras -> Franja.dia(hoy.minusDays(atras)) }
        TREINTA_DIAS -> listOf(
            Franja.semana(tramo(hoy).desde, hoy.minusDays(21)),
            Franja.semana(hoy.minusDays(20), hoy.minusDays(14)),
            Franja.semana(hoy.minusDays(13), hoy.minusDays(7)),
            Franja.semana(hoy.minusDays(6), hoy),
        )
    }
}

/** De qué es cada barra del gráfico del Resumen: un día o una semana (cambio 16). */
enum class EscalaBarra { DIA, SEMANA }

/**
 * Una barra del gráfico: desde (incluido) hasta (sin incluir), en la hora del teléfono. Un día va de
 * las 00:00 a las 00:00 del siguiente; una semana, de las 00:00 de su primer día a las 00:00 del día
 * después del último.
 */
data class Franja(val desde: LocalDateTime, val hasta: LocalDateTime, val escala: EscalaBarra) {

    operator fun contains(momento: LocalDateTime): Boolean = !momento.isBefore(desde) && momento.isBefore(hasta)

    /** El primer día de la barra: el que la nombra ("vie", "28/8"). */
    val primerDia: LocalDate
        get() = desde.toLocalDate()

    companion object {
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
 * Resumen de actividad de 7 o 30 días (RF07). Se calcula en el teléfono a partir de las alertas
 * guardadas, por eso funciona sin conexión (RNF01).
 */
data class ResumenActividad(
    val periodo: PeriodoResumen,
    /** Del primer día del período a hoy ("del 21/09 al 27/09"). */
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
    /** Las alertas que pasan los filtros, de la más nueva a la más vieja: la lista "Ver estas alertas". */
    val alertas: List<Alerta> = emptyList(),
    /** Todas las del período, sin filtros: la hoja de filtros cuenta con ellas cuántas quedarían. */
    val delPeriodo: List<Alerta> = emptyList(),
) {

    companion object {

        /**
         * Con menos revisiones que esto, la precisión de un modelo todavía no se puede comparar con la de
         * los otros: su barra va clara (cambio 4). Se cuenta por modelo.
         */
        const val REVISIONES_PARA_COMPARAR = 30

        /**
         * Calcula el resumen del [periodo] que termina hoy. Solo cuentan las alertas que empezaron dentro
         * del período; el que llama puede pasar más y acá se descartan. Con [filtro], todas las cifras se
         * calculan solo sobre las alertas que lo pasan.
         */
        fun calcular(
            alertas: List<Alerta>,
            periodo: PeriodoResumen,
            ahora: Instant,
            zona: ZoneId,
            filtro: FiltroResumen = FiltroResumen(),
        ): ResumenActividad {
            val hoy = ahora.atZone(zona).toLocalDate()
            val tramo = periodo.tramo(hoy)
            val delPeriodo = alertas.filter { it.dia(zona) in tramo }.sortedByDescending { it.inicio }
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

            val barras = periodo.barras(hoy).map { franja -> BarraActividad(franja, lista.count { it.momento(zona) in franja }) }

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

/** El día del calendario en que empezó la alerta, en la zona del teléfono. */
private fun Alerta.dia(zona: ZoneId): LocalDate = inicio.atZone(zona).toLocalDate()

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
