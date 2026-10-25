package ar.edu.uade.zonda.ui.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroResponsable
import ar.edu.uade.zonda.domain.model.OpcionesDeFiltro
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ContextoTarjetas
import ar.edu.uade.zonda.ui.observarContextoTarjetas
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ar.edu.uade.zonda.domain.model.Periodo
import java.time.Instant
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
    private val alertas: AlertasRepository,
    private val conectividad: Conectividad,
) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroAlertas.TODAS)
    private val sincronizando = MutableStateFlow(false)
    private val aviso = MutableStateFlow<AvisoAlertas?>(null)
    private val primeraLectura = MutableStateFlow(false)

    /** El error de la última sincronización, o null si salió bien; sin nada guardado, reemplaza la lista. */
    private val ultimoError = MutableStateFlow<ErrorZonda?>(null)

    private val lista: Flow<List<Alerta>> = filtro
        .flatMapLatest { observarAlertas(it) }
        .onEach { primeraLectura.value = true }

    /** Las horas de inicio con los demás filtros y sin período ni fechas: cuenta "Mostrar N alertas" de Fechas. */
    private val iniciosSinFechas: Flow<List<Instant>> = filtro
        .map { it.copy(periodo = Periodo.TODO, fechas = null) }
        .distinctUntilChanged()
        .flatMapLatest { observarAlertas(it) }
        .map { alertas -> alertas.map(Alerta::inicio) }

    private val opciones: Flow<OpcionesDeFiltro> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(OpcionesDeFiltro()) else alertas.observarOpcionesDeFiltro(red.id)
    }

    private val guardadas: Flow<Int> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(0) else alertas.observarCantidad(red.id)
    }

    private data class Datos(
        val red: Red?,
        val alertas: List<Alerta>,
        val opciones: OpcionesDeFiltro,
        val guardadas: Int,
        val leida: Boolean,
        val contexto: ContextoTarjetas = ContextoTarjetas(),
        val inicios: List<Instant> = emptyList(),
    )

    private val contexto: Flow<ContextoTarjetas> = redes.observarContextoTarjetas()

    private data class Controles(
        val filtro: FiltroAlertas,
        val conectado: Boolean,
        val sincronizando: Boolean,
        val aviso: AvisoAlertas?,
        val error: ErrorZonda?,
    )

    val estado: StateFlow<AlertasUiState> = combine(
        combine(
            combine(redes.observarRedActiva(), lista, opciones, guardadas, primeraLectura) { red, alertas, opciones, guardadas, leida ->
                Datos(red, alertas, opciones, guardadas, leida)
            },
            contexto,
            iniciosSinFechas,
        ) { datos, contexto, inicios -> datos.copy(contexto = contexto, inicios = inicios) },
        combine(filtro, conectividad.conectado, sincronizando, aviso, ultimoError) { filtro, conectado, sincronizando, aviso, error ->
            Controles(filtro, conectado, sincronizando, aviso, error)
        },
    ) { datos, controles ->
        AlertasUiState(
            contenido = when {
                datos.red == null -> ContenidoAlertas.SinRed
                !datos.leida -> ContenidoAlertas.Cargando
                datos.alertas.isNotEmpty() -> ContenidoAlertas.Lista(datos.alertas, sinRevisar = datos.alertas.count { it.sinRevisar })
                datos.guardadas == 0 && controles.error != null -> ContenidoAlertas.Error
                else -> ContenidoAlertas.Vacio(conFiltro = controles.filtro.activo)
            },
            filtro = controles.filtro,
            opciones = datos.opciones,
            datosDe = datos.red?.ultimaSincronizacion,
            sinConexion = !controles.conectado,
            accesoVencido = datos.red?.accesoVencido == true,
            sincronizando = controles.sincronizando,
            aviso = controles.aviso,
            contexto = datos.contexto,
            iniciosSinFechas = datos.inicios,
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

    /**
     * "Ver" del aviso de Estado (decisión 42; Figma 468-1603): todas las alertas asignadas a este
     * teléfono. Sin el id del teléfono (servidor anterior a la 1.0.5) no hay asignaciones: no cambia nada.
     */
    fun verAsignadasAMi() {
        viewModelScope.launch {
            val miId = contexto.first().miId ?: return@launch
            filtro.value = FiltroAlertas(responsable = FiltroResponsable.AsignadasAMi(miId))
        }
    }

    /** "Limpiar filtros": la lista vuelve a mostrar todo lo guardado. */
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
                ultimoError.value = null
                if (manual || resultado.nuevas > 0) {
                    aviso.value = AvisoAlertas.Sincronizado(resultado.hora, resultado.nuevas, resultado.altasNuevas, manual)
                }
            } catch (e: ErrorZonda) {
                ultimoError.value = e
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
