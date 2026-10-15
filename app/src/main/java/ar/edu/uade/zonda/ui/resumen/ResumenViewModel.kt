package ar.edu.uade.zonda.ui.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.ObservarResumen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel del Resumen (RF07). Guarda el período y la cifra tocada, y observa el resumen que calcula
 * [ObservarResumen] con las alertas del teléfono: cambiar el período o tocar una cifra cambia la
 * consulta y todas las cifras se recalculan al instante, con o sin conexión.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModel(
    private val observarResumen: ObservarResumen,
    redes: RedesRepository,
    conectividad: Conectividad,
) : ViewModel() {

    /** El período y la cifra tocada van juntos: al cambiar el período, el filtro cambia en el mismo paso. */
    private data class Consulta(val periodo: PeriodoResumen = PeriodoResumen.SIETE_DIAS, val grupo: GrupoResumen? = null)

    private val consulta = MutableStateFlow(Consulta())
    private val aviso = MutableStateFlow<AvisoResumen?>(null)

    private val contenido: Flow<ContenidoResumen> = consulta
        .flatMapLatest { observarResumen(it.periodo, it.grupo) }
        .map { resumen ->
            when {
                resumen == null -> ContenidoResumen.SinRed
                resumen.totalDelPeriodo == 0 -> ContenidoResumen.Vacio(resumen)
                else -> ContenidoResumen.Datos(resumen)
            }
        }

    /**
     * Hasta la primera lectura de la base el contenido es [ContenidoResumen.Cargando]. Al volver a la
     * pantalla (por ejemplo, de la app con la que se compartió) queda lo último que se mostró hasta que
     * llega la lectura nueva, sin pasar por el esqueleto.
     */
    val estado: StateFlow<ResumenUiState> = combine(
        consulta,
        contenido,
        redes.observarRedActiva(),
        conectividad.conectado,
        aviso,
    ) { consulta, contenido, red, conectado, aviso ->
        ResumenUiState(
            contenido = contenido,
            periodo = consulta.periodo,
            grupo = consulta.grupo,
            red = red?.nombre,
            datosDe = red?.ultimaSincronizacion,
            sinConexion = !conectado,
            aviso = aviso,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenUiState())

    /** 7 o 30 días. Un día o una semana elegidos no existen en el otro período: ese filtro se quita. */
    fun elegirPeriodo(nuevo: PeriodoResumen) {
        consulta.update { actual ->
            if (actual.periodo == nuevo) actual else Consulta(nuevo, actual.grupo.takeUnless { it is GrupoResumen.Barra })
        }
    }

    /**
     * Tocar una cifra recalcula todo el Resumen sobre ese grupo; tocarla de nuevo vuelve a todas
     * (RF07). Tocar el total de alertas ([nuevo] null) también vuelve a todas.
     */
    fun tocarCifra(nuevo: GrupoResumen?) {
        consulta.update { actual -> actual.copy(grupo = if (nuevo == null || nuevo == actual.grupo) null else nuevo) }
    }

    /** La cruz del chip del filtro. */
    fun quitarFiltro() {
        consulta.update { it.copy(grupo = null) }
    }

    /** La imagen salió por [destino]: el aviso lo confirma al volver (Figma 107-4941 a 107-5349). */
    fun compartido(destino: DestinoCompartir) {
        aviso.value = if (destino == DestinoCompartir.COPIAR) AvisoResumen.Copiado else AvisoResumen.Enviado(destino)
    }

    fun compartirFallo() {
        aviso.value = AvisoResumen.NoSePudoCompartir
    }

    fun avisoMostrado() {
        aviso.value = null
    }
}
