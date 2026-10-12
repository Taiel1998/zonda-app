package ar.edu.uade.zonda.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de una alerta (RF03). La alerta se observa desde la base local, así que si
 * se revisa o llega una actualización del servidor la pantalla cambia sola. Si la alerta todavía no
 * está en el teléfono (se abrió desde el enlace del aviso), se descarga; sin conexión queda en
 * [DetalleUiState.NoDisponible] y se vuelve a intentar cuando la red vuelve (criterio 3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModel(
    private val obtenerDetalle: ObtenerDetalleAlerta,
    private val redes: RedesRepository,
    private val conectividad: Conectividad,
    private val alertaId: String,
) : ViewModel() {

    private val descargando = MutableStateFlow(false)
    private val errorDeDescarga = MutableStateFlow<ErrorZonda?>(null)

    private val alerta: Flow<Alerta?> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(null) else obtenerDetalle.observar(red.id, alertaId)
    }

    val estado: StateFlow<DetalleUiState> = combine(
        redes.observarRedActiva(),
        alerta,
        conectividad.conectado,
        descargando,
        errorDeDescarga,
    ) { red, alerta, conectado, descargando, error ->
        when {
            red == null -> DetalleUiState.SinRed
            alerta != null -> DetalleUiState.Contenido(alerta, sinConexion = !conectado, datosDe = red.ultimaSincronizacion)
            descargando -> DetalleUiState.Cargando
            else -> DetalleUiState.NoDisponible(sinConexion = !conectado, error = error)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleUiState.Cargando)

    init {
        descargarSiFalta()
        viewModelScope.launch {
            // Cuando vuelve la conexión, se completa lo que quedó pendiente de descarga.
            conectividad.conectado.distinctUntilChanged().filter { it }.collect { descargarSiFalta() }
        }
    }

    fun reintentar() = descargarSiFalta()

    private fun descargarSiFalta() {
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            if (obtenerDetalle.observar(red.id, alertaId).first() != null) return@launch
            descargando.value = true
            errorDeDescarga.value = null
            try {
                if (obtenerDetalle.descargarSiFalta(red.id, alertaId) == null) errorDeDescarga.value = ErrorZonda.SinConexion()
            } catch (e: ErrorZonda) {
                errorDeDescarga.value = e
            } finally {
                descargando.value = false
            }
        }
    }
}
