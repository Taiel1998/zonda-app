package ar.edu.uade.zonda.ui.ajustes

import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo

/** Estado de la pantalla Ajustes (RF05 y RF06; informe, 11.10). */
data class AjustesUiState(
    val redes: List<Red> = emptyList(),
    val redActiva: Red? = null,
    val avisos: PreferenciasAvisos = PreferenciasAvisos(),
    /** La severidad por tipo de la red activa, la que rige. */
    val severidades: SeveridadPorTipo = SeveridadPorTipo.RECOMENDADA,
    /** La que se está editando en la pantalla de severidades; null si no se está editando. */
    val borradorSeveridades: SeveridadPorTipo? = null,
    val sincronizando: Boolean = false,
    val nombreDelTelefono: String = "",
    val versionDeLaApp: String = "",
    val aviso: AvisoAjustes? = null,
)

/** Avisos de un solo uso (snackbar). */
sealed interface AvisoAjustes {

    data class Sincronizado(val nuevas: Int, val accionesEnviadas: Int) : AvisoAjustes

    data object SeveridadesGuardadas : AvisoAjustes

    data object DatosBorrados : AvisoAjustes

    /** Se quitó el dispositivo de una red; si no quedan redes, la pantalla vuelve a Conexión. */
    data class DispositivoQuitado(val quedanRedes: Boolean) : AvisoAjustes

    data class Error(val error: ErrorZonda) : AvisoAjustes
}
