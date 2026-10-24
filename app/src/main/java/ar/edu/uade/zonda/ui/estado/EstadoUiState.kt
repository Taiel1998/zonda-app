package ar.edu.uade.zonda.ui.estado

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.ContextoTarjetas
import java.time.Instant

/** Qué se muestra debajo de la barra de Estado (Figma 1-3, 1-178, 1-222, 1-256 y 50-616). */
sealed interface ContenidoEstado {

    /** Todavía no llegó la primera lectura de la base: esqueleto (Figma 1-256). */
    data object Cargando : ContenidoEstado

    /** No hay ninguna red pareada: "Conectá con la red de tu empresa" y Configurar conexión (Figma 1-222). */
    data object SinRed : ContenidoEstado

    /** La primera descarga después del pareo: la tarjeta con la barra y el esqueleto debajo (Figma 50-616). */
    data class Descargando(val descargadas: Int, val total: Int?) : ContenidoEstado

    /**
     * La red nunca se sincronizó y no hay ninguna descarga en curso (la primera falló): el semáforo gris de
     * "Sin datos todavía", nunca verde (RF01, criterio 3; Figma 1-222), y el botón para volver a probar.
     */
    data object SinDatos : ContenidoEstado

    /**
     * El estado de la red (Figma 1-3): semáforo, contadores y la lista de las sin revisar, que ya viene
     * filtrada por el contador elegido. [ultimaDeteccion] es la hora de la sin revisar más reciente de la
     * severidad que pone el color: las altas en rojo y las medias en amarillo; en verde no hay.
     */
    data class Datos(
        val semaforo: Semaforo,
        val sinRevisar: ContadoresSinRevisar,
        val ultimaDeteccion: Instant?,
        val alertas: List<Alerta>,
    ) : ContenidoEstado
}

/** Avisos de un solo uso: la pantalla los muestra y llama a `avisoMostrado()`. */
sealed interface AvisoEstado {

    /**
     * La persona pidió sincronizar y terminó bien: el aviso del Figma con la fecha y la hora
     * ("Sincronizado · dom 27/09 · 02:15", cuadro 99-2; RF01, criterio 5).
     */
    data class Sincronizado(val hora: Instant, val nuevas: Int) : AvisoEstado

    /** La sincronización falló: aviso inferior con Reintentar, sin tapar lo que ya se ve (informe, 11.4). */
    data class Error(val error: ErrorZonda) : AvisoEstado
}

/**
 * Estado completo de la pantalla Estado (RF01). Todo sale de la base local, así que con datos ya
 * sincronizados aparece enseguida (criterio 1) y cambia solo cuando llega una sincronización o se revisa
 * una alerta desde el detalle.
 */
data class EstadoUiState(
    val contenido: ContenidoEstado = ContenidoEstado.Cargando,
    /** El contador tocado: la lista muestra solo las sin revisar de esa severidad; null, todas (criterio 4). */
    val severidadElegida: Severidad? = null,
    /** Hora de la última sincronización, para "actualizado 02:14" y el banner de sin conexión; null si nunca. */
    val datosDe: Instant? = null,
    val sinConexion: Boolean = false,
    /** El servidor rechazó el token de la red activa: hay que conectarla de nuevo (RNF12). */
    val accesoVencido: Boolean = false,
    /** Hay una sincronización pedida por la persona en curso: el ícono gira hasta que termina. */
    val sincronizando: Boolean = false,
    val aviso: AvisoEstado? = null,
    /**
     * Alertas con una revisión de este teléfono que no se aplicó porque otra persona la revisó antes: el
     * aviso azul arriba del semáforo (cambio 7; Figma 443-113 y 443-189). Vacía, no hay aviso.
     */
    val revisionesNoAplicadas: List<String> = emptyList(),
    /** Las sin revisar asignadas a este teléfono: el aviso azul "1 alerta asignada a vos" (decisión 42; Figma 468-2018). */
    val asignadasAMi: List<Alerta> = emptyList(),
    /** Quién es este teléfono y la importancia de los equipos, para las tarjetas (Figma 468-1454). */
    val contexto: ContextoTarjetas = ContextoTarjetas(),
    /** La tarjeta "Estado del sistema": sensor, análisis en vivo y servidor. Null hasta la primera respuesta. */
    val sistema: EstadoSistema? = null,
)
