package ar.edu.uade.zonda.ui.primerinicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel del primer inicio (RF05), compartido por todas sus pantallas a través del grafo anidado.
 *
 * Los avisos son del teléfono y se guardan al instante. Desde la 1.0.5 el primer inicio no elige la
 * severidad de cada tipo: es configuración de toda la red y la cambia un administrador desde Ajustes
 * (decisión 39); hasta entonces rigen las recomendadas.
 */
class PrimerInicioViewModel(
    private val preferencias: PreferenciasRepository,
) : ViewModel() {

    val avisos: StateFlow<PreferenciasAvisos> =
        preferencias.observarAvisos().stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasAvisos())

    /** De a un cambio por vez: DataStore tarda en escribir, y el segundo partía de lo que se veía, sin el primero. */
    private val escritura = Mutex()

    fun cambiarAviso(severidad: Severidad, modo: ModoAviso) {
        viewModelScope.launch {
            escritura.withLock { preferencias.guardarAvisos(preferencias.observarAvisos().first().con(severidad, modo)) }
        }
    }

    /** La introducción ya no vuelve a aparecer, aunque el pareo quede para después (RF05). */
    fun terminarIntroduccion() {
        viewModelScope.launch { preferencias.marcarPrimerInicioHecho() }
    }
}
