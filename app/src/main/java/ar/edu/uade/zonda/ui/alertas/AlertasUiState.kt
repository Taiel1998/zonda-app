package ar.edu.uade.zonda.ui.alertas

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.OpcionesDeFiltro
import ar.edu.uade.zonda.ui.ContextoTarjetas
import java.time.Instant

/** Qué se muestra en el cuerpo de la pantalla Alertas (Figma 2-3, 2-218, 2-255 y 2-292). */
sealed interface ContenidoAlertas {

    /** Todavía no llegó la primera lectura de la base: esqueleto (Figma 2-292). */
    data object Cargando : ContenidoAlertas

    /** No hay ninguna red pareada: primer inicio pendiente. */
    data object SinRed : ContenidoAlertas

    /** La lista está vacía; si hay un filtro puesto se ofrece limpiarlo (Figma 2-218). */
    data class Vacio(val conFiltro: Boolean) : ContenidoAlertas

    /**
     * No hay nada guardado y la sincronización falló (Figma 2-255). Con algo guardado no se reemplaza
     * la lista: el error va en el aviso inferior.
     */
    data object Error : ContenidoAlertas

    data class Lista(
        val alertas: List<Alerta>,
        /** Cuántas de la lista siguen sin revisar, para la línea de resumen. */
        val sinRevisar: Int,
    ) : ContenidoAlertas
}

/** Avisos de un solo uso (el aviso inferior): la pantalla los muestra y llama a `avisoMostrado()`. */
sealed interface AvisoAlertas {

    /** [manual]: la persona tocó sincronizar, y el aviso dice "Sincronizado · dom 27/09 · 02:15". */
    data class Sincronizado(
        val hora: Instant,
        val nuevas: Int,
        val altasNuevas: Int,
        val manual: Boolean,
    ) : AvisoAlertas

    data class Error(val error: ErrorZonda) : AvisoAlertas
}

/**
 * Estado completo de la pantalla Alertas (RF02). Todo sale de la base local; por eso la lista
 * cambia sola cuando llega una sincronización o cuando se revisa una alerta desde el detalle.
 */
data class AlertasUiState(
    val contenido: ContenidoAlertas = ContenidoAlertas.Cargando,
    val filtro: FiltroAlertas = FiltroAlertas.TODAS,
    /** Servicios y equipos destino de las alertas guardadas, para Más filtros. */
    val opciones: OpcionesDeFiltro = OpcionesDeFiltro(),
    /** Hora de la última sincronización ("actualizado 02:14"); null si nunca se sincronizó. */
    val datosDe: Instant? = null,
    val sinConexion: Boolean = false,
    /** El servidor rechazó el token de la red activa: hay que conectarla de nuevo (RNF12). */
    val accesoVencido: Boolean = false,
    val sincronizando: Boolean = false,
    val aviso: AvisoAlertas? = null,
    /** Quién es este teléfono, la gente y la importancia de los equipos: chips, líneas y el filtro Responsable. */
    val contexto: ContextoTarjetas = ContextoTarjetas(),
)
