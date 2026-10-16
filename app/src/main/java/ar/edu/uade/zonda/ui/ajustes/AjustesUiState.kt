package ar.edu.uade.zonda.ui.ajustes

import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo

/** Una red con lo que muestra la pantalla Redes: sus alertas sin revisar y cuántas guarda el teléfono. */
data class RedConEstado(
    val red: Red,
    val contadores: ContadoresSinRevisar,
    val alertasGuardadas: Int,
)

/**
 * Estado de Ajustes y de sus pantallas (Redes, Avisos por severidad y Severidad por tipo de ataque), que
 * comparten el mismo ViewModel (RF05 y RF06; Figma 4-66 y siguientes).
 */
data class AjustesUiState(
    val redes: List<RedConEstado> = emptyList(),
    val redActiva: Red? = null,
    /** Cómo avisa Telegram cada severidad (Figma 51-1688). */
    val avisos: PreferenciasAvisos = PreferenciasAvisos(),
    /** Los avisos mientras se editan; null si no se están editando. */
    val borradorAvisos: PreferenciasAvisos? = null,
    /** La severidad por tipo de la red activa, la que rige. */
    val severidades: SeveridadPorTipo = SeveridadPorTipo.RECOMENDADA,
    /** La que se está editando en Severidad por tipo de ataque; null si no se está editando. */
    val borradorSeveridades: SeveridadPorTipo? = null,
    /** El interruptor "Notificaciones del sistema". */
    val notificacionesActivas: Boolean = true,
    val alertasDeLaRedActiva: Int = 0,
    /** Todas las alertas guardadas en el teléfono, de todas las redes (lo que borra "Borrar datos locales"). */
    val alertasGuardadas: Int = 0,
    val sincronizando: Boolean = false,
    val nombreDelTelefono: String = "",
    val aviso: AvisoAjustes? = null,
)

/** Avisos inferiores de un solo uso (snackbar). */
sealed interface AvisoAjustes {

    /** "Sincronizado: llegaron N alertas nuevas." (Figma 66-3808). */
    data class Sincronizado(val nuevas: Int) : AvisoAjustes

    data object SeveridadesGuardadas : AvisoAjustes

    /** "Datos locales borrados. Tocá Sincronizar ahora para descargar de nuevo." (Figma 66-4011). */
    data object DatosBorrados : AvisoAjustes

    /** Se quitó el dispositivo de una red; si no quedan redes, la app vuelve a Conexión. */
    data class DispositivoQuitado(val quedanRedes: Boolean) : AvisoAjustes

    /** "Red activa: <red>. Estado y Alertas ya muestran sus datos." (Figma 66-4123). */
    data class RedActivada(val nombre: String) : AvisoAjustes

    data class Error(val error: ErrorZonda) : AvisoAjustes
}
