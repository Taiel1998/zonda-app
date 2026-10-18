package ar.edu.uade.zonda.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.CambiarImportancia
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un cambio de importancia que espera su motivo en "¿Por qué la cambiás?". */
data class CambioDeImportancia(val equipo: EquipoDeLaRed, val nueva: Importancia)

data class EquiposUiState(
    val red: Red? = null,
    /** Ordenados por dirección, como el Figma (468-3089): quedan juntos los de cada parte de la red. */
    val equipos: List<EquipoDeLaRed> = emptyList(),
    /** El equipo con la hoja de importancia abierta. */
    val elegido: EquipoDeLaRed? = null,
    /** El cambio elegido, con la hoja del motivo abierta. */
    val cambio: CambioDeImportancia? = null,
    val motivo: String = "",
    val faltaMotivo: Boolean = false,
    val aviso: AvisoEquipos? = null,
) {
    val esAdministrador: Boolean
        get() = (red?.perfil ?: Perfil.ADMINISTRADOR).esAdministrador
}

sealed interface AvisoEquipos {
    data class Guardado(val equipo: String) : AvisoEquipos
    data class Error(val error: ErrorZonda) : AvisoEquipos
}

/**
 * Ajustes → Equipos (corrección 4, decisión 41): la importancia de cada equipo con nombre. La ve todo el
 * equipo; el administrador la cambia con un motivo, se guarda en el teléfono y sale a la red, que
 * recalcula la severidad de sus alertas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquiposViewModel(
    private val redes: RedesRepository,
    private val cambiarImportancia: CambiarImportancia,
    private val sincronizarAlertas: SincronizarAlertas,
    private val alcanceDeEnvio: CoroutineScope,
) : ViewModel() {

    private data class Controles(
        val elegidoIp: String? = null,
        val cambio: CambioDeImportancia? = null,
        val motivo: String = "",
        val faltaMotivo: Boolean = false,
        val aviso: AvisoEquipos? = null,
    )

    private val controles = MutableStateFlow(Controles())

    val estado: StateFlow<EquiposUiState> = redes.observarRedActiva().flatMapLatest { red ->
        val equipos = if (red == null) flowOf(emptyList()) else redes.observarEquipos(red.id)
        combine(equipos, controles) { lista, c ->
            val ordenados = lista.sortedWith(compareBy(ORDEN_POR_IP) { it.ip })
            EquiposUiState(
                red = red,
                equipos = ordenados,
                elegido = ordenados.firstOrNull { it.ip == c.elegidoIp },
                cambio = c.cambio,
                motivo = c.motivo,
                faltaMotivo = c.faltaMotivo,
                aviso = c.aviso,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquiposUiState())

    /** Abre la hoja de importancia de un equipo (solo el administrador). */
    fun elegir(equipo: EquipoDeLaRed) {
        if (!estado.value.esAdministrador) return
        controles.update { it.copy(elegidoIp = equipo.ip) }
    }

    fun cerrarHoja() {
        controles.update { it.copy(elegidoIp = null) }
    }

    /** Elegir otra importancia cierra la hoja y pide el motivo; la misma no cambia nada. */
    fun elegirImportancia(importancia: Importancia) {
        val equipo = estado.value.elegido ?: return
        controles.update {
            if (importancia == equipo.importancia) {
                it.copy(elegidoIp = null)
            } else {
                it.copy(elegidoIp = null, cambio = CambioDeImportancia(equipo, importancia), motivo = "", faltaMotivo = false)
            }
        }
    }

    fun cambiarMotivo(texto: String) {
        controles.update { it.copy(motivo = texto, faltaMotivo = false) }
    }

    fun cancelarMotivo() {
        controles.update { it.copy(cambio = null, motivo = "", faltaMotivo = false) }
    }

    /** "Guardar cambios": sin motivo no guarda; con él, se guarda, se encola y se sincroniza. */
    fun guardar() {
        val cambio = controles.value.cambio ?: return
        val motivo = controles.value.motivo.trim()
        if (motivo.isEmpty()) {
            controles.update { it.copy(faltaMotivo = true) }
            return
        }
        controles.update { it.copy(cambio = null, motivo = "", faltaMotivo = false) }
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            try {
                cambiarImportancia(red.id, cambio.equipo.ip, cambio.nueva, motivo)
                controles.update { it.copy(aviso = AvisoEquipos.Guardado(cambio.equipo.nombre)) }
                alcanceDeEnvio.launch { runCatching { sincronizarAlertas(red.id) } }
            } catch (e: ErrorZonda) {
                controles.update { it.copy(aviso = AvisoEquipos.Error(e)) }
            }
        }
    }

    fun avisoMostrado() {
        controles.update { it.copy(aviso = null) }
    }

    private companion object {
        /** 192.168.20.20 antes que 192.168.100.1: por número, no por texto. */
        val ORDEN_POR_IP = Comparator<String> { a, b ->
            val partesA = a.split('.').map { it.toIntOrNull() ?: 0 }
            val partesB = b.split('.').map { it.toIntOrNull() ?: 0 }
            partesA.zip(partesB).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: partesA.size.compareTo(partesB.size)
        }
    }
}
