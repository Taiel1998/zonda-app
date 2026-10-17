package ar.edu.uade.zonda.ui.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.RangoResumen
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.ToqueResumen
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel del Resumen (RF07) y de su lista "Ver estas alertas", que lo comparte. Guarda el período y
 * los filtros (cambio 16), y observa el resumen que calcula [ObservarResumen] con las alertas del
 * teléfono: cambiar el período, tocar una cifra o aplicar la hoja de filtros cambia la consulta y
 * todas las cifras se recalculan al instante, con o sin conexión.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModel(
    private val observarResumen: ObservarResumen,
    redes: RedesRepository,
    conectividad: Conectividad,
) : ViewModel() {

    /**
     * El período, las fechas elegidas y los filtros van juntos: al cambiar el período o las fechas, los
     * filtros cambian en el mismo paso. Con [fechas], el período no cuenta.
     */
    private data class Consulta(
        val periodo: PeriodoResumen = PeriodoResumen.SIETE_DIAS,
        val fechas: RangoResumen? = null,
        val filtro: FiltroResumen = FiltroResumen(),
    )

    private val consulta = MutableStateFlow(Consulta())
    private val aviso = MutableStateFlow<AvisoResumen?>(null)

    private val contenido: Flow<ContenidoResumen> = consulta
        .flatMapLatest { observarResumen(it.periodo, it.fechas, it.filtro) }
        .map { resumen ->
            when {
                resumen == null -> ContenidoResumen.SinRed
                resumen.totalDelPeriodo == 0 -> ContenidoResumen.Vacio(resumen)
                else -> ContenidoResumen.Datos(resumen)
            }
        }

    /** La red activa con la severidad que eligió para cada tipo (cambio 14). */
    private val redYSeveridades: Flow<Pair<Red?, SeveridadPorTipo>> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(null to SeveridadPorTipo.RECOMENDADA) else redes.observarSeveridades(red.id).map { red to it }
    }

    /**
     * Hasta la primera lectura de la base el contenido es [ContenidoResumen.Cargando]. Al volver a la
     * pantalla (por ejemplo, de la app con la que se compartió) queda lo último que se mostró hasta que
     * llega la lectura nueva, sin pasar por el esqueleto.
     */
    val estado: StateFlow<ResumenUiState> = combine(
        consulta,
        contenido,
        redYSeveridades,
        conectividad.conectado,
        aviso,
    ) { consulta, contenido, (red, severidades), conectado, aviso ->
        ResumenUiState(
            contenido = contenido,
            periodo = consulta.periodo,
            fechas = consulta.fechas,
            filtro = consulta.filtro,
            red = red?.nombre,
            datosDe = red?.ultimaSincronizacion,
            sinConexion = !conectado,
            aviso = aviso,
            severidades = severidades,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenUiState())

    /**
     * 7 o 30 días. Los días o las semanas elegidos eran del otro período: se sacan y quedan los demás
     * filtros (cambio 16).
     */
    fun elegirPeriodo(nuevo: PeriodoResumen) {
        consulta.update { actual ->
            if (actual.periodo == nuevo && actual.fechas == null) actual else Consulta(nuevo, fechas = null, filtro = actual.filtro.sinBarras())
        }
    }

    /**
     * "Mostrar" de "Elegí las fechas" (cambio 19). Si son justo los días de 7 o 30 días, se marca ese
     * atajo. Las barras elegidas eran de las fechas de antes: se sacan y quedan los demás filtros.
     */
    fun elegirFechas(rango: RangoResumen) {
        val hoy = observarResumen.hoy()
        val atajo = PeriodoResumen.entries.firstOrNull { it.rango(hoy) == rango }
        consulta.update { actual ->
            val nueva = Consulta(atajo ?: actual.periodo, fechas = if (atajo == null) rango else null, filtro = actual.filtro.sinBarras())
            if (nueva.periodo == actual.periodo && nueva.fechas == actual.fechas) actual else nueva
        }
    }

    /**
     * Tocar una cifra suma o saca su filtro y todo el Resumen se recalcula (RF07, cambio 16): los días y
     * los tipos de a varios, la revisión y el modelo de a uno; el total saca todos. Las altas son los
     * tipos que la red tiene en alta.
     */
    fun tocar(toque: ToqueResumen) {
        val altas = estado.value.altas
        consulta.update { actual -> actual.copy(filtro = actual.filtro.tocar(toque, altas)) }
    }

    /** "Mostrar N alertas" (o "Ver estas alertas en la lista") de la hoja de filtros: reemplaza los filtros. */
    fun aplicarFiltro(filtro: FiltroResumen) {
        consulta.update { it.copy(filtro = filtro) }
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
