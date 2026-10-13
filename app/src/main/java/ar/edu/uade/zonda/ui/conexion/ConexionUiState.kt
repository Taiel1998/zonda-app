package ar.edu.uade.zonda.ui.conexion

import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.ui.components.LARGO_CODIGO
import java.time.Instant

/** Los estados de la pantalla Conexión (Figma 35-432 a 50-729). */
sealed interface ConexionUiState {

    /**
     * Pantalla inicial: escanear el QR o escribir la dirección y el código. [codigo] queda como se
     * escribió (minúsculas o guion incluidos): las casillas del Figma 49-131 muestran sus letras y
     * números en mayúsculas.
     */
    data class Formulario(
        val direccion: String = "",
        val codigo: String = "",
        val errorDireccion: Boolean = false,
        val errorCodigo: Boolean = false,
    ) : ConexionUiState {
        val puedeConectar: Boolean
            get() = direccion.isNotBlank() && DatosPareo.caracteresDeCodigo(codigo).length == LARGO_CODIGO
    }

    /** La cámara está abierta. [qrNoValido]: se leyó un QR que no es de Zonda. */
    data class Escaneando(val qrNoValido: Boolean = false) : ConexionUiState

    /** Los tres pasos de "Conectando…" (Figma 49-521). */
    data class Conectando(val direccion: String, val paso: PasoPareo) : ConexionUiState

    /**
     * Red conectada (Figma 35-692): la red, cuántas alertas de los últimos 30 días hay para bajar y con
     * qué nombre quedó registrado el teléfono. La descarga pasa después, en Estado.
     */
    data class Conectada(
        val red: Red,
        val alertasParaDescargar: Int,
        val nombreDelTelefono: String,
    ) : ConexionUiState

    /**
     * No se pudo parear. [codigoValidoHasta] es la hora en que vence el código, si vino en el QR y
     * todavía no pasó: "El código sigue siendo válido hasta las 02:19" (Figma 35-720).
     */
    data class Fallo(
        val direccion: String,
        val error: ErrorPareo,
        val codigoValidoHasta: Instant? = null,
    ) : ConexionUiState
}

/** Los errores del pareo que la pantalla distingue, cada uno con su salida (Figma 35-720, 50-679 y 50-729). */
enum class ErrorPareo {
    CODIGO_VENCIDO,
    CODIGO_INVALIDO,
    SIN_SERVIDOR,
    SERVIDOR_NO_VERIFICADO,
    OTRO;

    /** El problema es el código: hay que pedir otro, la dirección sirve. Se muestra en ámbar; el resto, en rojo. */
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
