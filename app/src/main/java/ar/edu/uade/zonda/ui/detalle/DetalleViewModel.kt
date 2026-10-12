package ar.edu.uade.zonda.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import ar.edu.uade.zonda.domain.usecase.ResolverEnvioRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de una alerta (RF03 y RF04).
 *
 * La alerta y su acción pendiente se observan desde la base local, así que la pantalla cambia sola
 * al revisar, al deshacer o cuando llega una sincronización. Si la alerta todavía no está en el
 * teléfono (se abrió desde el enlace del aviso), se descarga; sin conexión queda "no disponible".
 *
 * Revisar (decisión 15): la revisión se guarda al instante y queda en la cola; durante
 * [esperaDeshacer] se puede deshacer. Cuando termina esa ventana se envía, en [alcanceDeEnvio]
 * (un alcance de toda la app), para que salga aunque la persona ya haya cerrado el detalle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModel(
    private val obtenerDetalle: ObtenerDetalleAlerta,
    private val revisarAlerta: RevisarAlerta,
    private val deshacerRevision: DeshacerRevision,
    private val resolverEnvio: ResolverEnvioRevision,
    private val sincronizarAlertas: SincronizarAlertas,
    private val redes: RedesRepository,
    private val acciones: AccionesPendientesRepository,
    private val conectividad: Conectividad,
    private val alcanceDeEnvio: CoroutineScope,
    private val alertaId: String,
    private val esperaDeshacer: Long = ESPERA_DESHACER_MS,
) : ViewModel() {

    private val descargando = MutableStateFlow(false)
    private val errorDeDescarga = MutableStateFlow<ErrorZonda?>(null)
    private val _formulario = MutableStateFlow<FormularioRevision?>(null)
    private val _aviso = MutableStateFlow<AvisoDetalle?>(null)
    private var ventanaDeshacer: Job? = null

    /** La hoja Revisar abierta, o null si está cerrada. */
    val formulario: StateFlow<FormularioRevision?> = _formulario.asStateFlow()

    /** Aviso de un solo uso para el snackbar: revisión guardada (con Deshacer) o un error. */
    val aviso: StateFlow<AvisoDetalle?> = _aviso.asStateFlow()

    private val alerta: Flow<Alerta?> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(null) else obtenerDetalle.observar(red.id, alertaId)
    }

    private val accion: Flow<AccionPendiente?> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(null) else acciones.observar(red.id).map { lista -> lista.firstOrNull { it.alertaId == alertaId } }
    }

    val estado: StateFlow<DetalleUiState> = combine(
        redes.observarRedActiva(),
        alerta,
        accion,
        conectividad.conectado,
        combine(descargando, errorDeDescarga) { enCurso, error -> enCurso to error },
    ) { red, alertaActual, accionActual, conectado, (enCurso, error) ->
        when {
            red == null -> DetalleUiState.SinRed
            alertaActual != null -> DetalleUiState.Contenido(alertaActual, accionActual, sinConexion = !conectado, datosDe = red.ultimaSincronizacion)
            enCurso -> DetalleUiState.Cargando
            else -> DetalleUiState.NoDisponible(sinConexion = !conectado, error = error)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleUiState.Cargando)

    init {
        descargarSiFalta()
        viewModelScope.launch {
            // Cuando vuelve la conexión: se completa lo que faltaba descargar y sale lo pendiente (RF03 y RF04, criterio 3).
            conectividad.conectado.drop(1).filter { it }.collect {
                descargarSiFalta()
                enviarSiQuedoPendiente()
            }
        }
    }

    fun reintentarDescarga() = descargarSiFalta()

    // ---- Hoja Revisar en dos pasos ----

    fun abrirRevision() {
        _formulario.value = FormularioRevision()
    }

    /** Paso 1. Si cambia el resultado, el motivo elegido deja de valer y el comentario propuesto se borra. */
    fun elegirResultado(resultado: ResultadoRevision) {
        _formulario.update { actual ->
            when {
                actual == null -> null
                actual.resultado == resultado -> actual.copy(paso = PasoRevision.MOTIVO)
                else -> actual.copy(
                    paso = PasoRevision.MOTIVO,
                    resultado = resultado,
                    motivo = null,
                    comentario = if (actual.comentarioEditado) actual.comentario else "",
                )
            }
        }
    }

    /** Paso 2. Cada motivo propone un comentario; reemplaza al anterior salvo que la persona haya escrito el suyo. */
    fun elegirMotivo(motivo: Motivo, comentarioPropuesto: String) {
        _formulario.update { actual ->
            actual?.let {
                val reemplazar = !it.comentarioEditado || it.comentario.isBlank()
                it.copy(
                    motivo = motivo,
                    comentario = if (reemplazar) comentarioPropuesto else it.comentario,
                    comentarioEditado = !reemplazar,
                )
            }
        }
    }

    fun cambiarComentario(texto: String) {
        _formulario.update { it?.copy(comentario = texto, comentarioEditado = true) }
    }

    fun volverAlResultado() {
        _formulario.update { it?.copy(paso = PasoRevision.RESULTADO) }
    }

    fun cancelarRevision() {
        _formulario.value = null
    }

    /** Guarda la revisión al instante y abre la ventana de Deshacer; el envío sale cuando termina. */
    fun confirmarRevision() {
        val formulario = _formulario.value ?: return
        val resultado = formulario.resultado ?: return
        val motivo = formulario.motivo ?: return
        if (!formulario.puedeConfirmar) return
        conRedActiva { redId ->
            revisarAlerta(redId, alertaId, resultado, motivo, formulario.comentario)
            _formulario.value = null
            abrirVentanaDeshacer(redId)
        }
    }

    /** Durante la ventana: la revisión se descarta antes de salir y la alerta vuelve a como estaba (RF04, criterio 1). */
    fun deshacer() {
        ventanaDeshacer?.cancel()
        ventanaDeshacer = null
        _aviso.value = null
        conRedActiva { redId -> deshacerRevision(redId, alertaId) }
    }

    // ---- Envío que no pudo llegar ----

    fun aplicarLaMiaIgual() = conRedActiva { redId ->
        resolverEnvio.aplicarLaMiaIgual(redId, alertaId)
        enviarAhora(redId)
    }

    fun quedarmeConLaOtra() = conRedActiva { redId -> resolverEnvio.quedarmeConLaOtra(redId, alertaId) }

    fun reintentarEnvio() = conRedActiva { redId ->
        resolverEnvio.reintentar(redId, alertaId)
        enviarAhora(redId)
    }

    /** La pantalla mostró un aviso de error. La revisión guardada se cierra sola al terminar la ventana. */
    fun avisoMostrado() {
        if (_aviso.value is AvisoDetalle.Error) _aviso.value = null
    }

    private suspend fun abrirVentanaDeshacer(redId: String) {
        val aviso = AvisoDetalle.RevisionGuardada(conConexion = conectividad.hayConexion())
        _aviso.value = aviso
        ventanaDeshacer?.cancel()
        ventanaDeshacer = alcanceDeEnvio.launch {
            delay(esperaDeshacer)
            _aviso.compareAndSet(aviso, null)
            enviar(redId)
        }
    }

    private fun enviarAhora(redId: String) {
        alcanceDeEnvio.launch { enviar(redId) }
    }

    /** Manda la cola y trae las novedades. Sin conexión no hace nada: sale cuando vuelva la red. */
    private suspend fun enviar(redId: String) {
        if (!conectividad.hayConexion()) return
        try {
            sincronizarAlertas(redId)
        } catch (e: ErrorZonda) {
            if (e !is ErrorZonda.SinConexion) _aviso.value = AvisoDetalle.Error(e)
        }
    }

    private suspend fun enviarSiQuedoPendiente() {
        if (ventanaDeshacer?.isActive == true) return
        val red = redes.observarRedActiva().first() ?: return
        if (acciones.deAlerta(red.id, alertaId)?.estado == EstadoEnvio.PENDIENTE) enviarAhora(red.id)
    }

    private fun conRedActiva(bloque: suspend (redId: String) -> Unit) {
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            try {
                bloque(red.id)
            } catch (e: ErrorZonda) {
                _aviso.value = AvisoDetalle.Error(e)
            }
        }
    }

    private fun descargarSiFalta() {
        viewModelScope.launch {
            if (descargando.value) return@launch
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

    companion object {
        /** Cuánto dura el aviso con Deshacer antes de enviar la revisión (RF04, criterio 1). */
        const val ESPERA_DESHACER_MS = 5_000L
    }
}
