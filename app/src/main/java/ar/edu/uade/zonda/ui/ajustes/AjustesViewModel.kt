package ar.edu.uade.zonda.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.BorrarDatosLocales
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.QuitarDispositivo
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

/**
 * ViewModel de Ajustes (RF05 y RF06): redes y red activa, avisos, severidad por tipo (que vale para
 * toda la red y viaja al servidor), sincronizar, borrar datos locales y quitar este dispositivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModel(
    private val redes: RedesRepository,
    private val preferencias: PreferenciasRepository,
    private val sincronizarAlertas: SincronizarAlertas,
    private val cambiarSeveridades: CambiarSeveridades,
    private val quitarDispositivo: QuitarDispositivo,
    private val borrarDatosLocales: BorrarDatosLocales,
    private val alcanceDeEnvio: CoroutineScope,
    nombreDelTelefono: String,
    versionDeLaApp: String,
) : ViewModel() {

    private data class Controles(
        val borrador: SeveridadPorTipo?,
        val sincronizando: Boolean,
        val aviso: AvisoAjustes?,
    )

    private val controles = MutableStateFlow(Controles(borrador = null, sincronizando = false, aviso = null))

    private val severidadesDeLaRedActiva = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(SeveridadPorTipo.RECOMENDADA) else redes.observarSeveridades(red.id)
    }

    val estado: StateFlow<AjustesUiState> = combine(
        redes.observarRedes(),
        redes.observarRedActiva(),
        preferencias.observarAvisos(),
        severidadesDeLaRedActiva,
        controles,
    ) { lista, activa, avisos, severidades, controles ->
        AjustesUiState(
            redes = lista,
            redActiva = activa,
            avisos = avisos,
            severidades = severidades,
            borradorSeveridades = controles.borrador,
            sincronizando = controles.sincronizando,
            nombreDelTelefono = nombreDelTelefono,
            versionDeLaApp = versionDeLaApp,
            aviso = controles.aviso,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState(nombreDelTelefono = nombreDelTelefono, versionDeLaApp = versionDeLaApp))

    /** Cambiar de red: todas las pantallas observan la red activa y cambian solas (RF06). */
    fun activarRed(redId: String) {
        viewModelScope.launch { redes.activar(redId) }
    }

    fun cambiarAviso(severidad: Severidad, modo: ModoAviso) {
        viewModelScope.launch { preferencias.guardarAvisos(preferencias.observarAvisos().first().con(severidad, modo)) }
    }

    // ---- Severidad por tipo: se edita en un borrador y se guarda de una vez ----

    fun empezarAEditarSeveridades() {
        viewModelScope.launch {
            val actuales = redes.observarRedActiva().first()?.let { redes.observarSeveridades(it.id).first() } ?: SeveridadPorTipo.RECOMENDADA
            controles.update { it.copy(borrador = actuales) }
        }
    }

    fun cambiarSeveridadDelBorrador(tipo: TipoAtaque, severidad: Severidad) {
        controles.update { it.copy(borrador = it.borrador?.con(tipo, severidad)) }
    }

    fun descartarBorrador() {
        controles.update { it.copy(borrador = null) }
    }

    /** Guarda en el teléfono, encola `PUT /severidades` para toda la red y lo envía (RF06). */
    fun guardarSeveridades() {
        val borrador = controles.value.borrador ?: return
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            cambiarSeveridades(red.id, borrador)
            controles.update { it.copy(borrador = null, aviso = AvisoAjustes.SeveridadesGuardadas) }
            alcanceDeEnvio.launch { runCatching { sincronizarAlertas(red.id) } }
        }
    }

    // ---- Datos locales ----

    fun sincronizarAhora() {
        if (controles.value.sincronizando) return
        viewModelScope.launch {
            controles.update { it.copy(sincronizando = true) }
            try {
                val resultado = sincronizarAlertas()
                if (resultado != null) controles.update { it.copy(aviso = AvisoAjustes.Sincronizado(resultado.nuevas, resultado.accionesEnviadas)) }
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

    /** Quita la red activa: revoca el token y borra lo suyo. Si queda otra red, pasa a ser la activa. */
    fun quitarDispositivo() {
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            quitarDispositivo(red.id)
            val restantes = redes.observarRedes().first()
            restantes.firstOrNull()?.let { redes.activar(it.id) }
            controles.update { it.copy(aviso = AvisoAjustes.DispositivoQuitado(quedanRedes = restantes.isNotEmpty())) }
        }
    }

    fun avisoMostrado() {
        controles.update { it.copy(aviso = null) }
    }
}
