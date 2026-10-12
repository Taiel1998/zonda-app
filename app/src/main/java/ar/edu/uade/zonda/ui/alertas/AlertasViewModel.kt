package ar.edu.uade.zonda.ui.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla Alertas (RF02). Expone un único [StateFlow] con todo lo que la pantalla
 * muestra y recibe las acciones de la persona (cambiar el filtro, sincronizar, aviso mostrado).
 *
 * La lista se observa desde la base local a través de [ObservarAlertas]; cambiar el filtro cambia la
 * consulta y la lista se actualiza al instante, con o sin conexión (RF02, criterios 1 y 3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertasViewModel(
    private val observarAlertas: ObservarAlertas,
    private val sincronizarAlertas: SincronizarAlertas,
    private val redes: RedesRepository,
    private val conectividad: Conectividad,
) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroAlertas.TODAS)
    private val sincronizando = MutableStateFlow(false)
    private val aviso = MutableStateFlow<AvisoAlertas?>(null)
    private val primeraLectura = MutableStateFlow(false)

    private val lista: Flow<List<Alerta>> = filtro
        .flatMapLatest { observarAlertas(it) }
        .onEach { primeraLectura.value = true }

    private val catalogo: Flow<Catalogo> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(Catalogo.VACIO) else redes.observarCatalogo(red.id)
    }

    private data class Datos(val red: Red?, val alertas: List<Alerta>, val catalogo: Catalogo, val leida: Boolean)

    private data class Controles(val filtro: FiltroAlertas, val conectado: Boolean, val sincronizando: Boolean, val aviso: AvisoAlertas?)

    val estado: StateFlow<AlertasUiState> = combine(
        combine(redes.observarRedActiva(), lista, catalogo, primeraLectura) { red, alertas, catalogo, leida -> Datos(red, alertas, catalogo, leida) },
        combine(filtro, conectividad.conectado, sincronizando, aviso) { filtro, conectado, sincronizando, aviso -> Controles(filtro, conectado, sincronizando, aviso) },
    ) { datos, controles ->
        AlertasUiState(
            contenido = when {
                datos.red == null -> ContenidoAlertas.SinRed
                !datos.leida -> ContenidoAlertas.Cargando
                datos.alertas.isEmpty() -> ContenidoAlertas.Vacio(conFiltro = controles.filtro.activo)
                else -> ContenidoAlertas.Lista(datos.alertas, sinRevisar = datos.alertas.count { it.sinRevisar })
            },
            filtro = controles.filtro,
            catalogo = datos.catalogo,
            datosDe = datos.red?.ultimaSincronizacion,
            sinConexion = !controles.conectado,
            accesoVencido = datos.red?.accesoVencido == true,
            sincronizando = controles.sincronizando,
            aviso = controles.aviso,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertasUiState())

    init {
        // Al abrir la pantalla con una red pareada y conexión, se sincroniza en segundo plano (informe, 14.2).
        viewModelScope.launch {
            if (redes.observarRedActiva().first() != null && conectividad.hayConexion()) sincronizar(manual = false)
        }
        viewModelScope.launch {
            // Cuando vuelve la conexión se envía lo pendiente y se traen las novedades (RF04, criterio 3).
            conectividad.conectado.drop(1).filter { it }.collect {
                if (redes.observarRedActiva().first() != null) sincronizar(manual = false)
            }
        }
    }

    fun aplicarFiltro(nuevo: FiltroAlertas) {
        filtro.value = nuevo
    }

    fun quitarFiltros() {
        filtro.value = FiltroAlertas.TODAS
    }

    /**
     * Envía lo pendiente y trae las novedades. La sincronización manual siempre avisa cómo terminó;
     * la automática solo si trajo algo o si falló por algo que la persona tiene que resolver.
     */
    fun sincronizar(manual: Boolean = true) {
        if (sincronizando.value) return
        viewModelScope.launch {
            sincronizando.value = true
            try {
                val resultado = sincronizarAlertas() ?: return@launch
                if (manual || resultado.nuevas > 0) {
                    aviso.value = AvisoAlertas.Sincronizado(resultado.nuevas, resultado.altasNuevas, resultado.accionesEnviadas)
                }
            } catch (e: ErrorZonda) {
                if (manual || e !is ErrorZonda.SinConexion) aviso.value = AvisoAlertas.Error(e)
            } finally {
                sincronizando.value = false
            }
        }
    }

    fun avisoMostrado() {
        aviso.value = null
    }
}
