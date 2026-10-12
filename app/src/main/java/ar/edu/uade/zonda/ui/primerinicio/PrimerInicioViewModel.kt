package ar.edu.uade.zonda.ui.primerinicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del primer inicio (RF05), compartido por todas sus pantallas a través del grafo anidado.
 *
 * Los avisos son del teléfono y se guardan al instante. La severidad de cada tipo es de la red, que
 * todavía no existe: se recuerda acá y se aplica después del pareo, solo si la persona cambió algo de
 * lo recomendado (si no, rige lo que ya tiene la red; docs/decisiones.md, decisión 16).
 */
class PrimerInicioViewModel(
    private val preferencias: PreferenciasRepository,
    private val cambiarSeveridades: CambiarSeveridades,
    private val sincronizarAlertas: SincronizarAlertas,
    private val alcanceDeEnvio: CoroutineScope,
) : ViewModel() {

    val avisos: StateFlow<PreferenciasAvisos> =
        preferencias.observarAvisos().stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasAvisos())

    private val _severidades = MutableStateFlow(SeveridadPorTipo.RECOMENDADA)
    val severidades: StateFlow<SeveridadPorTipo> = _severidades.asStateFlow()

    fun cambiarAviso(severidad: Severidad, modo: ModoAviso) {
        viewModelScope.launch { preferencias.guardarAvisos(avisos.value.con(severidad, modo)) }
    }

    fun cambiarSeveridad(tipo: TipoAtaque, severidad: Severidad) {
        _severidades.update { it.con(tipo, severidad) }
    }

    /** La introducción ya no vuelve a aparecer, aunque el pareo quede para después (RF05). */
    fun terminarIntroduccion() {
        viewModelScope.launch { preferencias.marcarPrimerInicioHecho() }
    }

    /** Después del pareo: si la persona cambió la severidad recomendada, se guarda y se envía a la red. */
    suspend fun alParear(red: Red) {
        val elegidas = _severidades.value
        if (elegidas == SeveridadPorTipo.RECOMENDADA) return
        cambiarSeveridades(red.id, elegidas)
        alcanceDeEnvio.launch {
            try {
                sincronizarAlertas(red.id)
            } catch (e: ErrorZonda) {
                // Queda en la cola y sale en la sincronización siguiente.
            }
        }
    }
}
