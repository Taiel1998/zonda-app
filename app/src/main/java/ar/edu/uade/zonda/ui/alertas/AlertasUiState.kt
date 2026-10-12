package ar.edu.uade.zonda.ui.alertas

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import java.time.Instant

/** Qué se muestra en el cuerpo de la pantalla Alertas (plan, sección 4.4). */
sealed interface ContenidoAlertas {

    /** Todavía no llegó la primera lectura de la base: esqueleto. */
    data object Cargando : ContenidoAlertas

    /** No hay ninguna red pareada: primer inicio pendiente. */
    data object SinRed : ContenidoAlertas

    /** La lista está vacía; si hay un filtro activo se ofrece quitarlo. */
    data class Vacio(val conFiltro: Boolean) : ContenidoAlertas

    data class Lista(
        val alertas: List<Alerta>,
        /** Cuántas de la lista siguen sin revisar, para la línea de resumen. */
        val sinRevisar: Int,
    ) : ContenidoAlertas
}

/** Avisos de un solo uso (snackbar): la pantalla los muestra y llama a `avisoMostrado()`. */
sealed interface AvisoAlertas {

    data class Sincronizado(val nuevas: Int, val altasNuevas: Int, val accionesEnviadas: Int) : AvisoAlertas

    data class Error(val error: ErrorZonda) : AvisoAlertas
}

/**
 * Estado completo de la pantalla Alertas (RF02). Todo sale de la base local; por eso la lista
 * cambia sola cuando llega una sincronización o cuando se revisa una alerta desde el detalle.
 */
data class AlertasUiState(
    val contenido: ContenidoAlertas = ContenidoAlertas.Cargando,
    val filtro: FiltroAlertas = FiltroAlertas.TODAS,
    /** Servicios y nombres de equipo de la red, para las opciones de Más filtros. */
    val catalogo: Catalogo = Catalogo.VACIO,
    /** Hora de la última sincronización ("actualizado 02:14"); null si nunca se sincronizó. */
    val datosDe: Instant? = null,
    val sinConexion: Boolean = false,
    /** El servidor rechazó el token de la red activa: hay que conectarla de nuevo (RNF12). */
    val accesoVencido: Boolean = false,
    val sincronizando: Boolean = false,
    val aviso: AvisoAlertas? = null,
)
