package ar.edu.uade.zonda.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.CodigoPersona
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.GestionarPersonas
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

/** Lo que se está escribiendo en "Agregar persona" (Figma 468-2642). */
data class FormularioPersona(
    val nombre: String = "",
    val perfil: Perfil = Perfil.ANALISTA,
    val faltaNombre: Boolean = false,
    val generando: Boolean = false,
    /** El servidor no pudo dar el código (sin conexión, por ejemplo): se dice en la hoja. */
    val error: ErrorZonda? = null,
)

/** Estado de Personas y de "Código para Laura", que comparten el ViewModel. */
data class PersonasUiState(
    val red: Red? = null,
    /** Vos primero, después los administradores y los analistas (Figma 468-2568). */
    val personas: List<Persona> = emptyList(),
    /** La hoja "Agregar persona" abierta, o null. */
    val formulario: FormularioPersona? = null,
    /** El último código generado: lo muestra "Código para Laura". */
    val codigo: CodigoPersona? = null,
    /** Recién se generó el código: la pantalla abre "Código para Laura" y lo avisa con [PersonasViewModel.codigoAbierto]. */
    val abrirCodigo: Boolean = false,
    val aviso: AvisoPersonas? = null,
) {
    val esAdministrador: Boolean
        get() = (red?.perfil ?: Perfil.ADMINISTRADOR).esAdministrador
}

/** Avisos inferiores de un solo uso de Personas. */
sealed interface AvisoPersonas {
    data class PerfilCambiado(val nombre: String, val perfil: Perfil) : AvisoPersonas
    data class PermisoBloqueoCambiado(val nombre: String, val puedeBloquear: Boolean) : AvisoPersonas
    data class Quitada(val nombre: String) : AvisoPersonas
    data class Error(val error: ErrorZonda) : AvisoPersonas

    /** El teléfono no tiene huella ni PIN: lo que da poder no sale sin confirmar (decisión 51). */
    data object SinBloqueoDePantalla : AvisoPersonas
}

/**
 * Ajustes → Personas (corrección 2, decisión 39): la gente de la red activa. Todos la ven; el
 * administrador suma gente con un código de un uso y cambia el perfil o quita a los demás. Todo pasa
 * por el servidor, que no deja al equipo sin administradores. Al abrir, pide la lista al servidor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonasViewModel(
    private val redes: RedesRepository,
    private val gestionar: GestionarPersonas,
) : ViewModel() {

    private data class Controles(
        val formulario: FormularioPersona? = null,
        val codigo: CodigoPersona? = null,
        val abrirCodigo: Boolean = false,
        val aviso: AvisoPersonas? = null,
    )

    private val controles = MutableStateFlow(Controles())

    val estado: StateFlow<PersonasUiState> = redes.observarRedActiva().flatMapLatest { red ->
        val personas = if (red == null) flowOf(emptyList()) else redes.observarPersonas(red.id)
        combine(personas, controles) { lista, c ->
            PersonasUiState(
                red = red,
                personas = Persona.ordenar(lista),
                formulario = c.formulario,
                codigo = c.codigo,
                abrirCodigo = c.abrirCodigo,
                aviso = c.aviso,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonasUiState())

    init {
        actualizar()
    }

    /** Vuelve a pedir la gente al servidor; sin conexión queda la que ya estaba. */
    fun actualizar() {
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            try {
                gestionar.actualizar(red.id)
            } catch (e: ErrorZonda) {
                // Se ve la lista guardada; se vuelve a pedir en la próxima sincronización.
            }
        }
    }

    // ---- Agregar persona ----

    fun empezarAAgregar() {
        controles.update { it.copy(formulario = FormularioPersona()) }
    }

    fun cambiarNombre(nombre: String) {
        controles.update { c -> c.copy(formulario = c.formulario?.copy(nombre = nombre, faltaNombre = false, error = null)) }
    }

    fun cambiarPerfilNuevo(perfil: Perfil) {
        controles.update { c -> c.copy(formulario = c.formulario?.copy(perfil = perfil, error = null)) }
    }

    fun cancelarAgregar() {
        controles.update { it.copy(formulario = null) }
    }

    /** "Generar código": sin nombre no sigue; con el código, la pantalla abre "Código para Laura". */
    fun generarCodigo() {
        val formulario = controles.value.formulario ?: return
        if (formulario.generando) return
        if (formulario.nombre.isBlank()) {
            controles.update { c -> c.copy(formulario = c.formulario?.copy(faltaNombre = true)) }
            return
        }
        controles.update { c -> c.copy(formulario = c.formulario?.copy(generando = true, error = null)) }
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            try {
                val codigo = gestionar.crearCodigo(red.id, formulario.nombre, formulario.perfil)
                controles.update { it.copy(formulario = null, codigo = codigo, abrirCodigo = true) }
            } catch (e: ErrorZonda.Validacion) {
                controles.update { c -> c.copy(formulario = c.formulario?.copy(generando = false, faltaNombre = true)) }
            } catch (e: ErrorZonda) {
                controles.update { c -> c.copy(formulario = c.formulario?.copy(generando = false, error = e)) }
            }
        }
    }

    /** La pantalla ya abrió "Código para Laura". */
    fun codigoAbierto() {
        controles.update { it.copy(abrirCodigo = false) }
    }

    // ---- Cada persona ----

    /** "Hacer administrador" o "Pasar a analista". */
    fun cambiarPerfil(persona: Persona, perfil: Perfil) = conRedActiva { redId ->
        gestionar.cambiarPerfil(redId, persona.id, perfil)
        controles.update { it.copy(aviso = AvisoPersonas.PerfilCambiado(persona.nombreVisible, perfil)) }
    }

    /** "Darle permiso de bloquear" o "Sacarle el permiso de bloquear" a un analista (decisión 51). */
    fun cambiarPermisoBloqueo(persona: Persona, puedeBloquear: Boolean) = conRedActiva { redId ->
        gestionar.cambiarPermisoBloqueo(redId, persona.id, puedeBloquear)
        controles.update { it.copy(aviso = AvisoPersonas.PermisoBloqueoCambiado(persona.nombreVisible, puedeBloquear)) }
    }

    /** "Quitar del equipo", ya confirmado. */
    fun quitar(persona: Persona) = conRedActiva { redId ->
        gestionar.quitar(redId, persona.id)
        controles.update { it.copy(aviso = AvisoPersonas.Quitada(persona.nombreVisible)) }
    }

    fun avisoMostrado() {
        controles.update { it.copy(aviso = null) }
    }

    /** Sin huella ni PIN, sumar, quitar o cambiarle el perfil o el permiso de bloquear a alguien no sale. */
    fun sinBloqueoDePantalla() {
        controles.update { it.copy(aviso = AvisoPersonas.SinBloqueoDePantalla) }
    }

    private fun conRedActiva(bloque: suspend (redId: String) -> Unit) {
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            try {
                bloque(red.id)
            } catch (e: ErrorZonda) {
                controles.update { it.copy(aviso = AvisoPersonas.Error(e)) }
            }
        }
    }
}
