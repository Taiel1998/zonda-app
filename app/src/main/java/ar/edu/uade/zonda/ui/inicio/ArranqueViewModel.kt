package ar.edu.uade.zonda.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Por dónde entra la app al tocar el ícono (plan, sección 4.4). */
enum class DestinoInicial {
    /** Hay una red pareada: Estado. */
    PRINCIPAL,

    /** Primera vez: bienvenida, tutorial, avisos, severidades y pareo (RF05). */
    PRIMER_INICIO,

    /** La introducción ya se hizo pero no hay red (se quitó, o el pareo quedó para después): Conexión. */
    CONEXION;

    companion object {
        fun para(hayRedPareada: Boolean, primerInicioHecho: Boolean): DestinoInicial = when {
            hayRedPareada -> PRINCIPAL
            !primerInicioHecho -> PRIMER_INICIO
            else -> CONEXION
        }
    }
}

/** Decide la pantalla de entrada. Es null mientras se abre la base: la primera vez, SQLCipher tarda unos segundos. */
class ArranqueViewModel(
    redes: RedesRepository,
    preferencias: PreferenciasRepository,
) : ViewModel() {

    val destinoInicial: StateFlow<DestinoInicial?> =
        combine(redes.observarRedActiva(), preferencias.observarPrimerInicioHecho()) { red, hecho ->
            DestinoInicial.para(hayRedPareada = red != null, primerInicioHecho = hecho)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
