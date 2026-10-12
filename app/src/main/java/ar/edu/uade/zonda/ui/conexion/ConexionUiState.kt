package ar.edu.uade.zonda.ui.conexion

import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.model.Red

/** Los estados de la pantalla Conexión (Figma, figuras 14 y 15). */
sealed interface ConexionUiState {

    /** Pantalla inicial: escanear el QR o escribir la dirección y el código. */
    data class Formulario(
        val direccion: String = "",
        val codigo: String = "",
        val errorDireccion: Boolean = false,
        val errorCodigo: Boolean = false,
    ) : ConexionUiState {
        val puedeConectar: Boolean
            get() = direccion.isNotBlank() && DatosPareo.FORMATO_CODIGO.matches(DatosPareo.normalizarCodigo(codigo))
    }

    /** La cámara está abierta. [qrNoValido]: se leyó un QR que no es de Zonda. */
    data class Escaneando(val qrNoValido: Boolean = false) : ConexionUiState

    data class Conectando(val direccion: String, val paso: PasoPareo) : ConexionUiState

    /** [alertasDescargadas] es null si la primera sincronización no se pudo hacer (se hace después). */
    data class Conectada(val red: Red, val alertasDescargadas: Int?) : ConexionUiState

    data class Fallo(val direccion: String, val error: ErrorPareo) : ConexionUiState
}

/** Los errores del pareo que la pantalla distingue, cada uno con su salida (informe, figura 15). */
enum class ErrorPareo {
    CODIGO_VENCIDO,
    CODIGO_INVALIDO,
    SIN_SERVIDOR,
    SERVIDOR_NO_VERIFICADO,
    OTRO;

    /** El problema es el código: hay que pedir otro, la dirección sirve. */
    val esDelCodigo: Boolean
        get() = this == CODIGO_VENCIDO || this == CODIGO_INVALIDO

    companion object {
        fun de(error: ErrorZonda): ErrorPareo = when (error) {
            is ErrorZonda.CodigoVencido -> CODIGO_VENCIDO
            is ErrorZonda.CodigoInvalido -> CODIGO_INVALIDO
            is ErrorZonda.SinConexion -> SIN_SERVIDOR
            is ErrorZonda.ServidorNoVerificado -> SERVIDOR_NO_VERIFICADO
            else -> OTRO
        }
    }
}
