package ar.edu.uade.zonda.ui.estado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Cómo va la primera descarga de la red activa, para la tarjeta "Descargando alertas…" de Estado
 * (Figma 50-616). Mientras Estado sea provisoria, la tarjeta va arriba de esa pantalla; cuando se
 * escriba Estado, esto pasa a su ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DescargaInicialViewModel(
    redes: RedesRepository,
    alertas: AlertasRepository,
) : ViewModel() {

    val progreso: StateFlow<ProgresoDescarga?> = redes.observarRedActiva()
        .flatMapLatest { red -> if (red == null) flowOf(null) else alertas.observarProgresoDescarga(red.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
