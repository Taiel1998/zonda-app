package ar.edu.uade.zonda.ui.estado

/**
 * Estado de la pantalla Estado (RF01). Lo completa Taiel junto con el ViewModel y la pantalla
 * (PLAN_ETAPA2, fase 5): por ahora solo existe la carga.
 */
sealed interface EstadoUiState {
    data object Cargando : EstadoUiState
}
