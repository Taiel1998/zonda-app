package ar.edu.uade.zonda.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.ModoNotificacion
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.Tema
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.BorrarDatosLocales
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.EnviarAvisosTelegram
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
 * ViewModel de Ajustes y de sus pantallas Redes, Avisos por severidad y Severidad por tipo de ataque, que lo
 * comparten (RF05 y RF06). Redes y red activa, avisos de Telegram y notificaciones de Zonda, severidad
 * de cada tipo (vale para toda la red y viaja al servidor), sincronizar, borrar datos locales y quitar
 * este dispositivo.
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
    private val enviarAvisosTelegram: EnviarAvisosTelegram,
    private val alcanceDeEnvio: CoroutineScope,
    nombreDelTelefono: String,
) : ViewModel() {

    private data class Controles(
        val borradorSeveridades: SeveridadPorTipo? = null,
        val borradorAvisos: PreferenciasAvisos? = null,
        val borradorNotificaciones: PreferenciasNotificaciones? = null,
        val sincronizando: Boolean = false,
        val aviso: AvisoAjustes? = null,
        val motivo: String? = null,
        val faltaMotivo: Boolean = false,
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

    private data class DeLaRedActiva(
        val red: Red?,
        val severidades: SeveridadPorTipo,
        val alertas: Int,
        val personas: List<Persona> = emptyList(),
        val equipos: List<EquipoDeLaRed> = emptyList(),
        val registro: List<CambioRegistro> = emptyList(),
    )

    private val deLaRedActiva: Flow<DeLaRedActiva> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) {
            flowOf(DeLaRedActiva(null, SeveridadPorTipo.RECOMENDADA, 0))
        } else {
            combine(
                redes.observarSeveridades(red.id),
                alertas.observarCantidad(red.id),
                redes.observarPersonas(red.id),
                redes.observarEquipos(red.id),
                redes.observarRegistro(red.id),
            ) { severidades, cantidad, personas, equipos, registro ->
                DeLaRedActiva(red, severidades, cantidad, Persona.ordenar(personas), equipos, registro)
            }
        }
    }

    private data class DelTelefono(
        val avisos: PreferenciasAvisos,
        val notificaciones: PreferenciasNotificaciones,
        val alertasGuardadas: Int,
        val tema: Tema,
    )

    private val delTelefono: Flow<DelTelefono> = combine(
        preferencias.observarAvisos(),
        preferencias.observarNotificaciones(),
        alertas.observarCantidadTotal(),
        preferencias.observarTema(),
    ) { avisos, notificaciones, total, tema -> DelTelefono(avisos, notificaciones, total, tema) }

    val estado: StateFlow<AjustesUiState> = combine(redesConEstado, deLaRedActiva, delTelefono, controles) { lista, activa, telefono, c ->
        AjustesUiState(
            redes = lista,
            redActiva = activa.red,
            avisos = telefono.avisos,
            borradorAvisos = c.borradorAvisos,
            notificaciones = telefono.notificaciones,
            borradorNotificaciones = c.borradorNotificaciones,
            severidades = activa.severidades,
            borradorSeveridades = c.borradorSeveridades,
            alertasDeLaRedActiva = activa.alertas,
            alertasGuardadas = telefono.alertasGuardadas,
            sincronizando = c.sincronizando,
            nombreDelTelefono = nombreDelTelefono,
            aviso = c.aviso,
            personas = activa.personas,
            equipos = activa.equipos,
            registro = activa.registro,
            motivo = c.motivo,
            faltaMotivo = c.faltaMotivo,
            tema = telefono.tema,
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
            try {
                quitarDispositivo.invoke(id)
            } catch (e: ErrorZonda) {
                // El último administrador de un equipo con más gente: el servidor no lo deja y no se borró nada.
                controles.update { it.copy(aviso = AvisoAjustes.Error(e)) }
                return@launch
            }
            val restantes = redes.observarRedes().first()
            if (id == activa) restantes.firstOrNull()?.let { redes.activar(it.id) }
            controles.update { it.copy(aviso = AvisoAjustes.DispositivoQuitado(quedanRedes = restantes.isNotEmpty())) }
        }
    }

    // ---- Avisos por severidad: Telegram y las notificaciones de Zonda se editan en un borrador y se guardan juntos ----

    fun empezarAEditarAvisos() {
        viewModelScope.launch {
            val avisos = preferencias.observarAvisos().first()
            val notificaciones = preferencias.observarNotificaciones().first()
            controles.update { it.copy(borradorAvisos = avisos, borradorNotificaciones = notificaciones) }
        }
    }

    // Sin borrador, el cambio parte de lo guardado: "Configurar mis avisos" de la introducción abre la pantalla
    // sin pasar por empezarAEditarAvisos, y después de que Android cierra la app el borrador ya no está.

    fun cambiarAvisoDelBorrador(severidad: Severidad, modo: ModoAviso) {
        controles.update { it.copy(borradorAvisos = (it.borradorAvisos ?: estado.value.avisos).con(severidad, modo)) }
    }

    fun cambiarNotificacionDelBorrador(severidad: Severidad, modo: ModoNotificacion) {
        controles.update { it.copy(borradorNotificaciones = (it.borradorNotificaciones ?: estado.value.notificaciones).con(severidad, modo)) }
    }

    fun cambiarRecordatorioDelBorrador(prendido: Boolean) {
        controles.update { it.copy(borradorNotificaciones = (it.borradorNotificaciones ?: estado.value.notificaciones).copy(recordatorio = prendido)) }
    }

    fun descartarBorradorAvisos() {
        controles.update { it.copy(borradorAvisos = null, borradorNotificaciones = null) }
    }

    /**
     * "Guardar" guarda las dos tarjetas en el teléfono, y los avisos de Telegram también se los manda al
     * bot de cada red; sin conexión los manda la sincronización.
     */
    fun guardarAvisos() {
        val avisos = controles.value.borradorAvisos
        val notificaciones = controles.value.borradorNotificaciones
        if (avisos == null && notificaciones == null) return
        viewModelScope.launch {
            notificaciones?.let { preferencias.guardarNotificaciones(it) }
            avisos?.let { preferencias.guardarAvisos(it) }
            controles.update { it.copy(borradorAvisos = null, borradorNotificaciones = null) }
            if (avisos != null) alcanceDeEnvio.launch { enviarAvisosTelegram(avisos) }
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
        // Sin borrador (Android cerró la app con la pantalla abierta), se parte de las de la red, como arriba.
        controles.update { it.copy(borradorSeveridades = (it.borradorSeveridades ?: estado.value.severidades).con(tipo, severidad)) }
    }

    fun descartarBorrador() {
        controles.update { it.copy(borradorSeveridades = null, motivo = null, faltaMotivo = false) }
    }

    /**
     * "Guardar" de Severidad por tipo (decisión 40): si cambió algo, abre "¿Por qué la cambiás?" y
     * devuelve true; si no cambió nada, devuelve false y no hay nada que guardar.
     */
    fun pedirMotivo(): Boolean {
        val borrador = controles.value.borradorSeveridades ?: return false
        if (borrador == estado.value.severidades) return false
        controles.update { it.copy(motivo = "", faltaMotivo = false) }
        return true
    }

    fun cambiarMotivo(texto: String) {
        controles.update { it.copy(motivo = texto, faltaMotivo = false) }
    }

    /** "Cancelar" de la hoja del motivo: vuelve a la pantalla con los cambios sin guardar. */
    fun cancelarMotivo() {
        controles.update { it.copy(motivo = null, faltaMotivo = false) }
    }

    /**
     * "Guardar cambios": con el motivo, guarda en el teléfono, encola `PUT /severidades` para toda la red
     * y lo envía (RF06); sin red queda en la cola. Sin motivo no guarda y devuelve false.
     */
    fun guardarSeveridades(): Boolean {
        val borrador = controles.value.borradorSeveridades ?: return false
        val motivo = controles.value.motivo?.trim().orEmpty()
        if (motivo.isEmpty()) {
            controles.update { it.copy(faltaMotivo = true) }
            return false
        }
        controles.update { it.copy(borradorSeveridades = null, motivo = null, faltaMotivo = false) }
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            try {
                cambiarSeveridades(red.id, borrador, motivo)
                controles.update { it.copy(aviso = AvisoAjustes.SeveridadesGuardadas) }
                alcanceDeEnvio.launch { runCatching { sincronizarAlertas(red.id) } }
            } catch (e: ErrorZonda) {
                controles.update { it.copy(aviso = AvisoAjustes.Error(e)) }
            }
        }
        return true
    }

    // ---- Apariencia (decisión 43) ----

    /** Se guarda en el teléfono y la app cambia al instante. */
    fun cambiarTema(tema: Tema) {
        viewModelScope.launch { preferencias.guardarTema(tema) }
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
