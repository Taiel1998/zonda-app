package ar.edu.uade.zonda.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.AvisoRecibido
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.BloqueosDeLaRed
import ar.edu.uade.zonda.domain.model.CalculoSeveridad
import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.cambiosDeSeveridad
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.AsignarAlerta
import ar.edu.uade.zonda.domain.usecase.GestionarBloqueos
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import ar.edu.uade.zonda.domain.usecase.ResolverEnvioRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlin.time.Duration.Companion.seconds
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
 * Revisar (decisiones 15 y 38): la revisión se guarda al instante y queda en la cola, se abre la
 * [ventana] de Deshacer de la app y el detalle se cierra ([cerrar]): la lista de la que se vino
 * muestra "Revisaste ..." y la revisión sale cuando termina la ventana. Si se deshace, el detalle
 * vuelve a abrir la hoja como estaba.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModel(
    private val obtenerDetalle: ObtenerDetalleAlerta,
    private val revisarAlerta: RevisarAlerta,
    private val asignarAlerta: AsignarAlerta,
    private val ventana: VentanaDeshacer,
    private val resolverEnvio: ResolverEnvioRevision,
    private val sincronizarAlertas: SincronizarAlertas,
    private val redes: RedesRepository,
    private val acciones: AccionesPendientesRepository,
    private val conectividad: Conectividad,
    private val alcanceDeEnvio: CoroutineScope,
    private val alertaId: String,
    private val nombreDelTelefono: String = "",
    /** Lo que trajo el enlace del aviso, para mostrarlo mientras la alerta no está en el teléfono. */
    private val avisoRecibido: AvisoRecibido? = null,
    /** Los bloqueos del firewall (decisión 51). Sin él, el detalle no tiene la tarjeta del bloqueo. */
    private val gestionarBloqueos: GestionarBloqueos? = null,
) : ViewModel() {

    private val descargando = MutableStateFlow(false)
    private val errorDeDescarga = MutableStateFlow<ErrorZonda?>(null)
    // Si se deshizo la revisión de esta alerta, la hoja vuelve abierta en el paso 2 con lo que se había elegido.
    private val _formulario = MutableStateFlow(ventana.recuperarFormulario(alertaId)?.copy(paso = PasoRevision.MOTIVO))
    private val _aviso = MutableStateFlow<AvisoDetalle?>(null)
    private val _cerrar = MutableStateFlow(false)
    private val bloqueos = MutableStateFlow<BloqueosDeLaRed?>(null)
    private val _hojaBloqueo = MutableStateFlow<FormularioBloqueo?>(null)
    private var seguimiento: Job? = null

    /** La hoja Revisar abierta, o null si está cerrada. */
    val formulario: StateFlow<FormularioRevision?> = _formulario.asStateFlow()

    /** La hoja del bloqueo abierta, o null si está cerrada. */
    val hojaBloqueo: StateFlow<FormularioBloqueo?> = _hojaBloqueo.asStateFlow()

    /** Aviso de un solo uso para el snackbar: un error. */
    val aviso: StateFlow<AvisoDetalle?> = _aviso.asStateFlow()

    /** La revisión quedó guardada: el detalle se cierra y la lista muestra "Revisaste ..." (corrección 7). */
    val cerrar: StateFlow<Boolean> = _cerrar.asStateFlow()

    private val alerta: Flow<Alerta?> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(null) else obtenerDetalle.observar(red.id, alertaId)
    }

    private val accion: Flow<AccionPendiente?> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(null) else acciones.observar(red.id).map { lista -> lista.firstOrNull { it.alertaId == alertaId } }
    }

    private data class Descarga(val enCurso: Boolean, val error: ErrorZonda?)

    private data class Extras(val descarga: Descarga, val datos: DeLaRed, val bloqueos: BloqueosDeLaRed?)

    /** Lo de la red que hace falta para el responsable y el cálculo de la severidad (decisiones 40 a 42). */
    private data class DeLaRed(
        val personas: List<Persona> = emptyList(),
        val severidades: SeveridadPorTipo = SeveridadPorTipo.RECOMENDADA,
        val importancias: Map<String, Importancia> = emptyMap(),
        val registro: List<CambioRegistro> = emptyList(),
    )

    private val deLaRed: Flow<DeLaRed> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) {
            flowOf(DeLaRed())
        } else {
            combine(
                redes.observarPersonas(red.id),
                redes.observarSeveridades(red.id),
                redes.observarEquipos(red.id),
                redes.observarRegistro(red.id),
            ) { personas, severidades, equipos, registro ->
                DeLaRed(Persona.ordenar(personas), severidades, equipos.associate { it.ip to it.importancia }, registro)
            }
        }
    }

    val estado: StateFlow<DetalleUiState> = combine(
        redes.observarRedActiva(),
        alerta,
        accion,
        conectividad.conectado,
        combine(descargando, errorDeDescarga, deLaRed, bloqueos) { enCurso, error, datos, actuales -> Extras(Descarga(enCurso, error), datos, actuales) },
    ) { red, alertaActual, accionActual, conectado, (descarga, datos, bloqueosDeLaRed) ->
        val (enCurso, error) = descarga
        when {
            red == null -> DetalleUiState.SinRed
            alertaActual != null -> DetalleUiState.Contenido(
                alerta = alertaActual,
                accion = accionActual,
                sinConexion = !conectado,
                datosDe = red.ultimaSincronizacion,
                nombreDelTelefono = nombreDelTelefono,
                nombreDeLaRed = red.nombre,
                personas = datos.personas,
                perfil = red.perfil,
                miId = red.dispositivoId ?: datos.personas.firstOrNull { it.vos }?.id,
                calculo = CalculoSeveridad.de(alertaActual, datos.severidades, datos.importancias),
                cambiosDeSeveridad = cambiosDeSeveridad(alertaActual, datos.registro, datos.severidades, datos.importancias),
                bloqueo = bloqueosDeLaRed?.takeIf { it.firewall.habilitado }?.let {
                    SeccionBloqueo(
                        firewall = it.firewall,
                        actual = it.activoDe(alertaActual.origen.ip),
                        nuncaSeBloquea = it.firewall.nuncaBloquea(alertaActual.origen.ip),
                    )
                },
            )
            enCurso -> DetalleUiState.Cargando
            else -> DetalleUiState.NoDisponible(sinConexion = !conectado, error = error, aviso = avisoRecibido)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleUiState.Cargando)

    init {
        descargarSiFalta()
        consultarBloqueos()
        viewModelScope.launch {
            // Cuando vuelve la conexión: se completa lo que faltaba descargar y sale lo pendiente (RF03 y RF04, criterio 3).
            conectividad.conectado.drop(1).filter { it }.collect {
                descargarSiFalta()
                enviarSiQuedoPendiente()
                consultarBloqueos()
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

    /**
     * Guarda la revisión al instante, abre la ventana de Deshacer de la app y cierra el detalle: el envío
     * sale cuando termina la ventana, aunque ya no esté abierta esta pantalla.
     */
    fun confirmarRevision() {
        val formulario = _formulario.value ?: return
        val resultado = formulario.resultado ?: return
        val motivo = formulario.motivo ?: return
        if (!formulario.puedeConfirmar) return
        conRedActiva { redId ->
            revisarAlerta(redId, alertaId, resultado, motivo, formulario.comentario)
            val revisada = obtenerDetalle.observar(redId, alertaId).first()
            ventana.abrir(
                RevisionPorDeshacer(
                    redId = redId,
                    alertaId = alertaId,
                    alerta = revisada?.let { "${it.origen.etiqueta} → ${it.destino.etiqueta}" }.orEmpty(),
                    formulario = formulario,
                    conConexion = conectividad.hayConexion(),
                ),
            )
            _formulario.value = null
            _cerrar.value = true
        }
    }

    // ---- Responsable (decisión 42) ----

    /**
     * Asigna la alerta a [persona], o la deja sin asignar con null: "Asignar a", "Me la asigno" y
     * "Soltarla". Se ve al instante y sale con la cola, como las revisiones.
     */
    fun asignar(persona: Persona?) = conRedActiva { redId ->
        asignarAlerta(redId, alertaId, persona)
        enviarAhora(redId)
    }

    // ---- Bloqueo del origen (decisión 51) ----

    /** "Bloquear" o "Pedir bloqueo": la hoja abre con una hora y el motivo que propone la pantalla. */
    fun abrirBloqueo(motivoPropuesto: String) {
        _hojaBloqueo.value = FormularioBloqueo(AccionBloqueo.BLOQUEAR, motivo = motivoPropuesto)
    }

    /** La hoja abre con la duración que tiene el bloqueo; la nueva corre desde ahora. */
    fun abrirCambioDeDuracion() = conBloqueoActual { actual ->
        _hojaBloqueo.value = FormularioBloqueo(AccionBloqueo.CAMBIAR_DURACION, duracion = DuracionBloqueo.de(actual.segundos) ?: DuracionBloqueo.PREDETERMINADA)
    }

    fun abrirDesbloqueo() {
        _hojaBloqueo.value = FormularioBloqueo(AccionBloqueo.DESBLOQUEAR)
    }

    fun abrirRechazo() {
        _hojaBloqueo.value = FormularioBloqueo(AccionBloqueo.RECHAZAR)
    }

    fun elegirDuracion(duracion: DuracionBloqueo) {
        _hojaBloqueo.update { it?.copy(duracion = duracion) }
    }

    fun cambiarMotivoBloqueo(texto: String) {
        _hojaBloqueo.update { it?.copy(motivo = texto, conError = false) }
    }

    fun cerrarBloqueo() {
        _hojaBloqueo.value = null
    }

    /** Sin motivo no sale ("Escribí el motivo."); con motivo, sale en el momento o no sale. */
    fun confirmarBloqueo() {
        val formulario = _hojaBloqueo.value ?: return
        if (formulario.enviando) return
        if (formulario.motivo.isBlank()) {
            _hojaBloqueo.value = formulario.copy(conError = true)
            return
        }
        _hojaBloqueo.value = formulario.copy(enviando = true)
        enElServidor { gestionar, redId, actual ->
            when (formulario.accion) {
                AccionBloqueo.BLOQUEAR -> gestionar.bloquear(redId, alertaId, formulario.duracion, formulario.motivo)
                AccionBloqueo.CAMBIAR_DURACION -> actual?.let { gestionar.cambiarDuracion(redId, it.id, formulario.duracion, formulario.motivo) }
                AccionBloqueo.DESBLOQUEAR -> actual?.let { gestionar.desbloquear(redId, it.id, formulario.motivo) }
                AccionBloqueo.RECHAZAR -> actual?.let { gestionar.rechazar(redId, it.id, formulario.motivo) }
            }
        }
    }

    /** Aprobar un pedido no lleva hoja: la duración y el motivo son los del pedido. */
    fun aprobarBloqueo() = enElServidor { gestionar, redId, actual ->
        actual?.let { gestionar.aprobar(redId, it.id) }
    }

    /**
     * Lo que se hace con un bloqueo sale en el momento: sin conexión no sale y no queda en la cola (un
     * bloqueo que llega tarde es otra decisión). Pase lo que pase, se cierra la hoja y se vuelve a mirar
     * cómo quedó; si salió, se mira otra vez mientras el firewall lo aplica.
     */
    private fun enElServidor(accion: suspend (GestionarBloqueos, String, Bloqueo?) -> Unit) {
        val gestionar = gestionarBloqueos ?: return
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            try {
                if (!conectividad.hayConexion()) throw ErrorZonda.SinConexion()
                accion(gestionar, red.id, bloqueoActual(red.id))
                _hojaBloqueo.value = null
                seguirAlFirewall(red.id)
            } catch (e: ErrorZonda) {
                _hojaBloqueo.value = null
                _aviso.value = if (e is ErrorZonda.SinConexion) AvisoDetalle.BloqueoSinConexion else AvisoDetalle.Error(e)
                actualizarBloqueos(red.id)
            }
        }
    }

    private fun conBloqueoActual(bloque: (Bloqueo) -> Unit) {
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            bloqueoActual(red.id)?.let(bloque)
        }
    }

    /** El bloqueo o pedido abierto del origen de la alerta, según la última consulta. */
    private suspend fun bloqueoActual(redId: String): Bloqueo? {
        val origen = obtenerDetalle.observar(redId, alertaId).first()?.origen?.ip ?: return null
        return bloqueos.value?.activoDe(origen)
    }

    private fun consultarBloqueos() {
        if (gestionarBloqueos == null) return
        viewModelScope.launch { redes.observarRedActiva().first()?.let { actualizarBloqueos(it.id) } }
    }

    /** Si el servidor no contesta, queda lo último que se supo. */
    private suspend fun actualizarBloqueos(redId: String) {
        val gestionar = gestionarBloqueos ?: return
        bloqueos.value = try {
            gestionar.consultar(redId)
        } catch (e: ErrorZonda) {
            bloqueos.value
        }
    }

    /** El firewall pregunta cada 10 s: después de un cambio se vuelve a mirar unas veces, hasta que lo aplica. */
    private fun seguirAlFirewall(redId: String) {
        seguimiento?.cancel()
        seguimiento = viewModelScope.launch {
            actualizarBloqueos(redId)
            for (espera in SEGUIMIENTO) {
                delay(espera)
                actualizarBloqueos(redId)
            }
        }
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

    /** La pantalla mostró el aviso de error. */
    fun avisoMostrado() {
        _aviso.value = null
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
        // Mientras se puede deshacer, sale cuando termina la ventana.
        if (ventana.abierta.value?.alertaId == alertaId) return
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

    private companion object {
        /** Cuándo se vuelve a mirar un bloqueo después de un cambio: a los 4, 12 y 24 segundos. */
        val SEGUIMIENTO = listOf(4.seconds, 8.seconds, 12.seconds)
    }
}
