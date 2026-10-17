package ar.edu.uade.zonda.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Período de la lista de alertas (RF02). La lista ofrece los tres primeros (Figma 51-2072); [TODO] es
 * todo lo guardado y lo usa el Resumen para leer las alertas, que después corta por días del calendario
 * (ver [PeriodoResumen]).
 */
enum class Periodo(val horas: Long?) {
    ULTIMAS_24_HORAS(24),
    ULTIMOS_7_DIAS(7 * 24),
    ULTIMOS_30_DIAS(30 * 24),
    TODO(null);

    /** Instante desde el que cuenta el período, o null si abarca todo lo guardado. */
    fun desde(ahora: Instant): Instant? = horas?.let { ahora.minus(Duration.ofHours(it)) }

    companion object {
        /** Los de la hoja Período de la lista. */
        val DE_LA_LISTA = listOf(ULTIMAS_24_HORAS, ULTIMOS_7_DIAS, ULTIMOS_30_DIAS)
    }
}

/**
 * La hoja "Estado de revisión" (Figma 51-1805). Revisadas incluye los falsos positivos, como el cuadro
 * "filtro Revisadas aplicado" del Figma: son todas las que alguien ya marcó.
 */
enum class FiltroEstado(val estados: Set<EstadoRevision>?) {
    SIN_REVISAR(setOf(EstadoRevision.SIN_REVISAR)),
    REVISADAS(setOf(EstadoRevision.ATAQUE_REAL, EstadoRevision.FALSO_POSITIVO)),
    FALSOS_POSITIVOS(setOf(EstadoRevision.FALSO_POSITIVO)),
    TODAS(null),
}

/** El servicio de Más filtros: uno con su chip, o "Otros", los que no tienen chip propio. */
sealed interface FiltroServicio {
    data class Uno(val nombre: String) : FiltroServicio

    data class Otros(val conChip: Set<String>) : FiltroServicio
}

/**
 * El responsable de Más filtros (decisión 42; Figma 468-1921): "Asignadas a mí", "Sin asignar" o una
 * persona. Sin filtro ("Cualquiera"), todas.
 */
sealed interface FiltroResponsable {
    fun acepta(alerta: Alerta): Boolean

    /** Las de este teléfono: lo pone también "Ver" del aviso de Estado. */
    data class AsignadasAMi(val miId: String) : FiltroResponsable {
        override fun acepta(alerta: Alerta): Boolean = alerta.asignacion?.responsableId == miId
    }

    data object SinAsignar : FiltroResponsable {
        override fun acepta(alerta: Alerta): Boolean = alerta.asignacion?.responsableId == null
    }

    data class De(val personaId: String) : FiltroResponsable {
        override fun acepta(alerta: Alerta): Boolean = alerta.asignacion?.responsableId == personaId
    }
}

/**
 * Filtros de la lista de alertas (RF02; Figma 2-3 y las hojas 51-1805 a 51-2202). Por defecto muestra
 * todas las de los últimos 30 días, que es todo lo que guarda el teléfono: ninguna alerta se oculta
 * por defecto (decisión 4). Las severidades se combinan; un conjunto vacío son todas.
 */
data class FiltroAlertas(
    val estado: FiltroEstado = FiltroEstado.TODAS,
    val severidades: Set<Severidad> = emptySet(),
    val periodo: Periodo = Periodo.ULTIMOS_30_DIAS,
    val servicio: FiltroServicio? = null,
    val destinoIp: String? = null,
    val metodo: MetodoDeteccion? = null,
    /**
     * Solo las alertas con una revisión de este teléfono que no se aplicó porque otra persona la revisó
     * antes (el envío quedó en conflicto). Lo pone "Ver" del aviso de Estado con varias (cambio 7).
     */
    val soloNoAplicadas: Boolean = false,
    /** Quién la tiene asignada (decisión 42); null es "Cualquiera". */
    val responsable: FiltroResponsable? = null,
) {
    val activo: Boolean
        get() = this != TODAS

    /** Cuántos de Más filtros hay puestos: "Más filtros (1)". */
    val cantidadMasFiltros: Int
        get() = listOfNotNull(servicio, destinoIp, metodo, responsable).size

    companion object {
        val TODAS = FiltroAlertas()

        /** Solo las sin revisar de una severidad: lo que deja en la lista un contador de Estado (RF01, criterio 4). */
        fun sinRevisarDe(severidad: Severidad) = FiltroAlertas(estado = FiltroEstado.SIN_REVISAR, severidades = setOf(severidad))
    }
}

/**
 * Las opciones de Más filtros (Figma 51-2202), sacadas de las alertas guardadas de la red: los
 * servicios y los equipos destino que aparecen, del más frecuente al menos. Un equipo sin nombre en el
 * catálogo se muestra con su dirección.
 */
data class OpcionesDeFiltro(
    val servicios: List<String> = emptyList(),
    val destinos: List<Equipo> = emptyList(),
) {
    /** Los servicios con chip propio; el resto entra en "Otros". */
    val serviciosConChip: List<String>
        get() = servicios.take(SERVICIOS_CON_CHIP)

    val hayOtrosServicios: Boolean
        get() = servicios.size > SERVICIOS_CON_CHIP

    companion object {
        /** El Figma muestra cinco servicios y "Otros". */
        const val SERVICIOS_CON_CHIP = 5
    }
}
