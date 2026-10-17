package ar.edu.uade.zonda.ui.resumen

import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import java.time.Instant

/** Qué se muestra en el cuerpo del Resumen (Figma 52-2629, 100-7024 y 52-2976). */
sealed interface ContenidoResumen {

    /** Todavía no llegó la primera lectura de la base. */
    data object Cargando : ContenidoResumen

    /** No hay ninguna red pareada. */
    data object SinRed : ContenidoResumen

    /** No hubo alertas en el período (Figma 52-2976); el resumen igual se puede compartir. */
    data class Vacio(val resumen: ResumenActividad) : ContenidoResumen

    data class Datos(val resumen: ResumenActividad) : ContenidoResumen
}

/** Avisos de un solo uso (el aviso inferior), después de compartir (Figma 107-4941 a 107-5349). */
sealed interface AvisoResumen {

    /** "Resumen enviado por Telegram." */
    data class Enviado(val destino: DestinoCompartir) : AvisoResumen

    /** "Imagen copiada. Pegala donde quieras." */
    data object Copiado : AvisoResumen

    /** No se pudo armar la imagen o abrir la app elegida. */
    data object NoSePudoCompartir : AvisoResumen
}

/**
 * Estado completo de la pantalla Resumen (RF07). Todo sale de la base del teléfono, por eso funciona
 * sin conexión y cambia solo cuando llega una sincronización o se revisa una alerta.
 */
data class ResumenUiState(
    val contenido: ContenidoResumen = ContenidoResumen.Cargando,
    val periodo: PeriodoResumen = PeriodoResumen.SIETE_DIAS,
    /** Los filtros puestos (cambio 16); vacío si el Resumen muestra todas las alertas del período. */
    val filtro: FiltroResumen = FiltroResumen(),
    /** Nombre de la red activa ("Oficina central"). */
    val red: String? = null,
    /** Hora de la última sincronización ("actualizado 02:14"); null si nunca se sincronizó. */
    val datosDe: Instant? = null,
    val sinConexion: Boolean = false,
    val aviso: AvisoResumen? = null,
    /** La severidad que eligió la red para cada tipo: el punto de color de "Por tipo de ataque" (cambio 14). */
    val severidades: SeveridadPorTipo = SeveridadPorTipo.RECOMENDADA,
) {
    /** Los tipos que la red tiene en alta: los que filtra "altas revisadas en la primera hora" (cambio 16). */
    val altas: Set<TipoAtaque>
        get() = severidades.tiposAltos
}
