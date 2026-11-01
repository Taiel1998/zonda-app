package ar.edu.uade.zonda.ui.ajustes

import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.Tema

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
    /** Cómo avisa Zonda cada severidad y el recordatorio (tarjeta "Notificaciones de Zonda", 1.0.4). */
    val notificaciones: PreferenciasNotificaciones = PreferenciasNotificaciones(),
    /** Las notificaciones mientras se editan, junto con los avisos; null si no se están editando. */
    val borradorNotificaciones: PreferenciasNotificaciones? = null,
    /** La severidad por tipo de la red activa, la que rige. */
    val severidades: SeveridadPorTipo = SeveridadPorTipo.RECOMENDADA,
    /** La que se está editando en Severidad por tipo de ataque; null si no se está editando. */
    val borradorSeveridades: SeveridadPorTipo? = null,
    val alertasDeLaRedActiva: Int = 0,
    /** Todas las alertas guardadas en el teléfono, de todas las redes (lo que borra "Borrar datos locales"). */
    val alertasGuardadas: Int = 0,
    val sincronizando: Boolean = false,
    val nombreDelTelefono: String = "",
    val aviso: AvisoAjustes? = null,
    /** La gente de la red activa, vos primero (decisión 39). */
    val personas: List<Persona> = emptyList(),
    /** Los equipos de la red activa con su importancia (decisión 41). */
    val equipos: List<EquipoDeLaRed> = emptyList(),
    /** El registro de cambios de la red activa, de lo más nuevo a lo más viejo (decisión 40). */
    val registro: List<CambioRegistro> = emptyList(),
    /** El motivo que se está escribiendo en "¿Por qué la cambiás?"; null si la hoja está cerrada. */
    val motivo: String? = null,
    /** Se tocó "Guardar cambios" sin motivo: la hoja dice "Escribí por qué la cambiás.". */
    val faltaMotivo: Boolean = false,
    /** Ajustes → Apariencia → Tema (decisión 43). */
    val tema: Tema = Tema.COMO_EL_TELEFONO,
) {
    /** El perfil de este teléfono en la red activa: los botones de configuración son del administrador. */
    val perfil: Perfil
        get() = redActiva?.perfil ?: Perfil.ADMINISTRADOR

    /** Las computadoras con el tablero web: no son personas y van aparte, en Navegadores, como en el tablero. */
    val navegadores: List<Persona>
        get() = personas.filter { it.navegador }

    /** "Taiel o Meli": quienes pueden cambiar la configuración, para la pantalla de la analista; sin los navegadores. */
    val administradores: List<String>
        get() = personas.filter { it.perfil.esAdministrador && !it.navegador }.map { it.nombreVisible }
}

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
