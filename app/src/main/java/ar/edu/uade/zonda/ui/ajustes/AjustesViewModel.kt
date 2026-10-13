package ar.edu.uade.zonda.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.BorrarDatosLocales
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.QuitarDispositivo
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/**
 * ViewModel de Ajustes y de sus pantallas Redes, Avisos por severidad y Severidad por ataque, que lo
 * comparten (RF05 y RF06). Redes y red activa, avisos de Telegram, severidad de cada tipo (vale para
 * toda la red y viaja al servidor), el interruptor de notificaciones, sincronizar, borrar datos
 * locales y quitar este dispositivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModel(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
    private val preferencias: PreferenciasRepository,
    private val sincronizarAlertas: SincronizarAlertas,
    private val cambiarSeveridades: CambiarSeveridades,
    private val quitarDispositivo: QuitarDispositivo,
    private val borrarDatosLocales: BorrarDatosLocales,
    private val alcanceDeEnvio: CoroutineScope,
    nombreDelTelefono: String,
) : ViewModel() {

    private data class Controles(
        val borradorSeveridades: SeveridadPorTipo? = null,
        val borradorAvisos: PreferenciasAvisos? = null,
        val sincronizando: Boolean = false,
        val aviso: AvisoAjustes? = null,
    )

    private val controles = MutableStateFlow(Controles())

    private val redesConEstado: Flow<List<RedConEstado>> = redes.observarRedes().flatMapLatest { lista ->
        if (lista.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                lista.map { red ->
                    combine(alertas.observarContadoresSinRevisar(red.id), alertas.observarCantidad(red.id)) { contadores, cantidad ->
                        RedConEstado(red, contadores, cantidad)
                    }
                },
            ) { it.toList() }
        }
    }

    private data class DeLaRedActiva(val red: Red?, val severidades: SeveridadPorTipo, val alertas: Int)

    private val deLaRedActiva: Flow<DeLaRedActiva> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) {
            flowOf(DeLaRedActiva(null, SeveridadPorTipo.RECOMENDADA, 0))
        } else {
            combine(redes.observarSeveridades(red.id), alertas.observarCantidad(red.id)) { severidades, cantidad ->
                DeLaRedActiva(red, severidades, cantidad)
            }
        }
    }

    private data class DelTelefono(val avisos: PreferenciasAvisos, val notificaciones: Boolean, val alertasGuardadas: Int)

    private val delTelefono: Flow<DelTelefono> = combine(
        preferencias.observarAvisos(),
        preferencias.observarNotificacionesActivas(),
        alertas.observarCantidadTotal(),
    ) { avisos, notificaciones, total -> DelTelefono(avisos, notificaciones, total) }

    val estado: StateFlow<AjustesUiState> = combine(redesConEstado, deLaRedActiva, delTelefono, controles) { lista, activa, telefono, c ->
        AjustesUiState(
            redes = lista,
            redActiva = activa.red,
            avisos = telefono.avisos,
            borradorAvisos = c.borradorAvisos,
            severidades = activa.severidades,
            borradorSeveridades = c.borradorSeveridades,
            notificacionesActivas = telefono.notificaciones,
            alertasDeLaRedActiva = activa.alertas,
            alertasGuardadas = telefono.alertasGuardadas,
            sincronizando = c.sincronizando,
            nombreDelTelefono = nombreDelTelefono,
            aviso = c.aviso,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState(nombreDelTelefono = nombreDelTelefono))

    // ---- Redes ----

    /**
     * Cambiar de red: todas las pantallas observan la red activa y cambian solas (RF06). Al cambiar de
     * red también se sincroniza (informe, 14.2).
     */
    fun activarRed(redId: String) {
        viewModelScope.launch {
            redes.activar(redId)
            val nombre = redes.observarRedes().first().firstOrNull { it.id == redId }?.nombre ?: return@launch
            controles.update { it.copy(aviso = AvisoAjustes.RedActivada(nombre)) }
            alcanceDeEnvio.launch { runCatching { sincronizarAlertas(redId) } }
        }
    }

    /**
     * Quita este dispositivo de una red (la activa si no se dice cuál): revoca el token y borra lo
     * suyo. Si era la activa y queda otra red, la otra pasa a ser la activa.
     */
    fun quitarDispositivo(redId: String? = null) {
        viewModelScope.launch {
            val activa = redes.observarRedActiva().first()?.id
            val id = redId ?: activa ?: return@launch
            quitarDispositivo.invoke(id)
            val restantes = redes.observarRedes().first()
            if (id == activa) restantes.firstOrNull()?.let { redes.activar(it.id) }
            controles.update { it.copy(aviso = AvisoAjustes.DispositivoQuitado(quedanRedes = restantes.isNotEmpty())) }
        }
    }

    // ---- Avisos de Telegram: se editan en un borrador y se guardan con "Guardar" ----

    fun empezarAEditarAvisos() {
        viewModelScope.launch {
            val actuales = preferencias.observarAvisos().first()
            controles.update { it.copy(borradorAvisos = actuales) }
        }
    }

    fun cambiarAvisoDelBorrador(severidad: Severidad, modo: ModoAviso) {
        controles.update { it.copy(borradorAvisos = it.borradorAvisos?.con(severidad, modo)) }
    }

    fun descartarBorradorAvisos() {
        controles.update { it.copy(borradorAvisos = null) }
    }

    fun guardarAvisos() {
        val borrador = controles.value.borradorAvisos ?: return
        viewModelScope.launch {
            preferencias.guardarAvisos(borrador)
            controles.update { it.copy(borradorAvisos = null) }
        }
    }

    // ---- Severidad por tipo: se edita en un borrador y se guarda de una vez ----

    fun empezarAEditarSeveridades() {
        viewModelScope.launch {
            val actuales = redes.observarRedActiva().first()?.let { redes.observarSeveridades(it.id).first() } ?: SeveridadPorTipo.RECOMENDADA
            controles.update { it.copy(borradorSeveridades = actuales) }
        }
    }

    fun cambiarSeveridadDelBorrador(tipo: TipoAtaque, severidad: Severidad) {
        controles.update { it.copy(borradorSeveridades = it.borradorSeveridades?.con(tipo, severidad)) }
    }

    fun descartarBorrador() {
        controles.update { it.copy(borradorSeveridades = null) }
    }

    /** Guarda en el teléfono, encola `PUT /severidades` para toda la red y lo envía (RF06); sin red queda en la cola. */
    fun guardarSeveridades() {
        val borrador = controles.value.borradorSeveridades ?: return
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            cambiarSeveridades(red.id, borrador)
            controles.update { it.copy(borradorSeveridades = null, aviso = AvisoAjustes.SeveridadesGuardadas) }
            alcanceDeEnvio.launch { runCatching { sincronizarAlertas(red.id) } }
        }
    }

    // ---- Notificaciones y Telegram ----

    /** El interruptor "Notificaciones del sistema": si la app muestra la notificación local de altas nuevas. */
    fun cambiarNotificaciones(activas: Boolean) {
        viewModelScope.launch { preferencias.guardarNotificacionesActivas(activas) }
    }

    /** "Conectar Telegram" anda con la fase 10; hasta entonces lo dice un aviso. */
    fun conectarTelegram() {
        controles.update { it.copy(aviso = AvisoAjustes.TelegramPronto) }
    }

    // ---- Datos locales ----

    fun sincronizarAhora() {
        if (controles.value.sincronizando) return
        viewModelScope.launch {
            controles.update { it.copy(sincronizando = true) }
            try {
                val resultado = sincronizarAlertas()
                if (resultado != null) controles.update { it.copy(aviso = AvisoAjustes.Sincronizado(resultado.nuevas)) }
            } catch (e: ErrorZonda) {
                controles.update { it.copy(aviso = AvisoAjustes.Error(e)) }
            } finally {
                controles.update { it.copy(sincronizando = false) }
            }
        }
    }

    fun borrarDatosLocales() {
        viewModelScope.launch {
            borrarDatosLocales.invoke()
            controles.update { it.copy(aviso = AvisoAjustes.DatosBorrados) }
        }
    }

    fun avisoMostrado() {
        controles.update { it.copy(aviso = null) }
    }
}
