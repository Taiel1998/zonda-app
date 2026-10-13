package ar.edu.uade.zonda.ui.conexion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.usecase.ParearRed
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.components.LARGO_CODIGO
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Conectada
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Conectando
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Escaneando
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Fallo
import ar.edu.uade.zonda.ui.conexion.ConexionUiState.Formulario
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla Conexión (RF05; informe, sección 13.4): pareo con el QR o a mano, los
 * pasos mientras conecta y los errores con su salida. [nombreDelTelefono] es el que va a aparecer
 * como autor de las revisiones de este teléfono.
 *
 * La primera descarga de alertas ya no pasa acá: "Continuar" la arranca en [alcanceDeLaApp] (no se
 * corta al cerrar la pantalla) y Estado muestra la barra (Figma 50-616).
 */
class ConexionViewModel(
    private val parearRed: ParearRed,
    private val sincronizarAlertas: SincronizarAlertas,
    private val nombreDelTelefono: String,
    private val alcanceDeLaApp: CoroutineScope,
    private val reloj: () -> Instant = Instant::now,
) : ViewModel() {

    private val _estado = MutableStateFlow<ConexionUiState>(Formulario())
    val estado: StateFlow<ConexionUiState> = _estado.asStateFlow()

    /** Lo último que se intentó, para Reintentar y para volver al formulario sin escribir todo de nuevo. */
    private var ultimosDatos: DatosPareo? = null

    fun cambiarDireccion(texto: String) {
        _estado.update { if (it is Formulario) it.copy(direccion = texto, errorDireccion = false) else it }
    }

    /**
     * El código se guarda tal como se escribe. Pasarlo a mayúsculas mientras el teclado compone la
     * palabra lo desincroniza y se pierden letras; las casillas ya lo muestran en mayúsculas. Lo que
     * pasa de ocho letras y números no entra.
     */
    fun cambiarCodigo(texto: String) {
        if (DatosPareo.caracteresDeCodigo(texto).length > LARGO_CODIGO) return
        _estado.update { if (it is Formulario) it.copy(codigo = texto, errorCodigo = false) else it }
    }

    /**
     * "Pegar" (Figma 49-131). Si lo copiado es el contenido del QR, completa también la dirección;
     * si no, toma las letras y los números del código.
     */
    fun pegar(texto: String?) {
        if (texto.isNullOrBlank() || _estado.value !is Formulario) return
        val delQr = DatosPareo.desdeQr(texto)
        // Del QR se recuerda también cuándo vence el código, para el mensaje si el servidor no responde.
        if (delQr != null) ultimosDatos = delQr
        _estado.update { actual ->
            when {
                actual !is Formulario -> actual
                delQr != null -> actual.copy(
                    direccion = delQr.urlBase,
                    codigo = soloCaracteresDeCodigo(delQr.codigo),
                    errorDireccion = false,
                    errorCodigo = false,
                )
                else -> actual.copy(codigo = soloCaracteresDeCodigo(texto), errorCodigo = false)
            }
        }
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
        // Si el código es el mismo que trajo un QR pegado, se conserva cuándo vence.
        val vence = ultimosDatos
            ?.takeIf { DatosPareo.caracteresDeCodigo(it.codigo) == DatosPareo.caracteresDeCodigo(formulario.codigo) }
            ?.codigoVence
        conectar(DatosPareo(formulario.direccion, formulario.codigo, vence))
    }

    fun reintentar() {
        ultimosDatos?.let(::conectar)
    }

    /**
     * "Continuar" en Red conectada: arranca la primera descarga (Estado muestra cómo va) y devuelve la
     * red para seguir. Si falla, la sincronización siguiente la retoma desde el último cursor guardado.
     */
    fun continuar(): Red? {
        val conectada = _estado.value as? Conectada ?: return null
        alcanceDeLaApp.launch {
            try {
                sincronizarAlertas(conectada.red.id)
            } catch (e: ErrorZonda) {
                // Se reintenta en la sincronización siguiente.
            }
        }
        return conectada.red
    }

    private fun conectar(datos: DatosPareo) {
        ultimosDatos = datos
        val direccion = DatosPareo.normalizarDireccion(datos.urlBase)
        _estado.value = Conectando(direccion, PasoPareo.BUSCANDO_SERVIDOR)
        viewModelScope.launch {
            try {
                val pareada = parearRed(datos, nombreDelTelefono) { paso -> _estado.value = Conectando(direccion, paso) }
                _estado.value = Conectada(pareada.red, pareada.alertasParaDescargar, nombreDelTelefono)
            } catch (e: ErrorZonda.Validacion) {
                _estado.value = Formulario(
                    direccion = datos.urlBase,
                    codigo = datos.codigo,
                    errorDireccion = e.campo == "urlBase",
                    errorCodigo = e.campo == "codigo",
                )
            } catch (e: ErrorZonda) {
                // Si el servidor no respondió, el código no llegó a mandarse: sigue sirviendo hasta que venza.
                val error = ErrorPareo.de(e)
                val vigente = datos.codigoVence?.takeIf { error == ErrorPareo.SIN_SERVIDOR && it.isAfter(reloj()) }
                _estado.value = Fallo(direccion, error, codigoValidoHasta = vigente)
            }
        }
    }

    private fun soloCaracteresDeCodigo(texto: String): String = DatosPareo.caracteresDeCodigo(texto).take(LARGO_CODIGO)
}
