package ar.edu.uade.zonda.ui.conexion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.usecase.ParearRed
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Conectada
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Conectando
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Escaneando
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Fallo
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Formulario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla Conexión (RF05; informe, sección 13.4): pareo con el QR o a mano, los
 * pasos mientras conecta, la primera descarga de alertas y los errores con su salida.
 * [nombreDelTelefono] es el que va a aparecer como autor de las revisiones de este teléfono.
 */
class ConexionViewModel(
    private val parearRed: ParearRed,
    private val sincronizarAlertas: SincronizarAlertas,
    private val nombreDelTelefono: String,
) : ViewModel() {

    private val _estado = MutableStateFlow<ConexionUiState>(Formulario())
    val estado: StateFlow<ConexionUiState> = _estado.asStateFlow()

    /** Lo último que se intentó, para Reintentar y para volver al formulario sin escribir todo de nuevo. */
    private var ultimosDatos: DatosPareo? = null

    fun cambiarDireccion(texto: String) {
        _estado.update { if (it is Formulario) it.copy(direccion = texto, errorDireccion = false) else it }
    }

    fun cambiarCodigo(texto: String) {
        _estado.update { if (it is Formulario) it.copy(codigo = texto.take(LARGO_MAXIMO_CODIGO), errorCodigo = false) else it }
    }

    fun abrirEscaner() {
        _estado.value = Escaneando()
    }

    fun cancelarEscaneo() {
        if (_estado.value is Escaneando) ingresarAMano()
    }

    /** Vuelve al formulario con la dirección que ya se usó; el código, solo si el problema no era el código. */
    fun ingresarAMano() {
        val anterior = _estado.value
        val datos = ultimosDatos
        val conservarCodigo = !(anterior is Fallo && anterior.error.esDelCodigo)
        _estado.value = Formulario(
            direccion = datos?.urlBase.orEmpty(),
            codigo = if (conservarCodigo) datos?.codigo.orEmpty() else "",
        )
    }

    /** La cámara leyó un QR. Se ignora si ya no se está escaneando (la cámara lee varias veces por segundo). */
    fun leyoQr(contenido: String) {
        if (_estado.value !is Escaneando) return
        val datos = DatosPareo.desdeQr(contenido)
        if (datos == null) {
            _estado.value = Escaneando(qrNoValido = true)
            return
        }
        conectar(datos)
    }

    fun conectarManual() {
        val formulario = _estado.value as? Formulario ?: return
        if (!formulario.puedeConectar) return
        conectar(DatosPareo(formulario.direccion, formulario.codigo))
    }

    fun reintentar() {
        ultimosDatos?.let(::conectar)
    }

    private fun conectar(datos: DatosPareo) {
        ultimosDatos = datos
        val direccion = DatosPareo.normalizarDireccion(datos.urlBase)
        _estado.value = Conectando(direccion, PasoPareo.BUSCANDO_SERVIDOR)
        viewModelScope.launch {
            try {
                val red = parearRed(datos, nombreDelTelefono) { paso -> _estado.value = Conectando(direccion, paso) }
                _estado.value = Conectando(direccion, PasoPareo.DESCARGANDO_ALERTAS)
                // El pareo ya quedó hecho: si la primera descarga falla, se hace en la sincronización siguiente.
                val descargadas = try {
                    sincronizarAlertas(red.id)?.nuevas
                } catch (e: ErrorZonda) {
                    null
                }
                _estado.value = Conectada(red, descargadas)
            } catch (e: ErrorZonda.Validacion) {
                _estado.value = Formulario(
                    direccion = datos.urlBase,
                    codigo = datos.codigo,
                    errorDireccion = e.campo == "urlBase",
                    errorCodigo = e.campo == "codigo",
                )
            } catch (e: ErrorZonda) {
                _estado.value = Fallo(direccion, ErrorPareo.de(e))
            }
        }
    }

    private companion object {
        /** Ocho caracteres y el guion. */
        const val LARGO_MAXIMO_CODIGO = 9
    }
}
