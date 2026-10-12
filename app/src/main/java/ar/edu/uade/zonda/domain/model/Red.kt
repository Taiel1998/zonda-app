package ar.edu.uade.zonda.domain.model

import java.time.Instant

/**
 * Una red pareada con este teléfono (RF05 y RF06). Cada red tiene su servidor, su cursor de
 * sincronización y sus datos separados. El token no está acá: vive en el almacenamiento seguro.
 */
data class Red(
    val id: String,
    val nombre: String,
    val urlBase: String,
    val cursor: String?,
    val ultimaSincronizacion: Instant?,
    val telegramVinculado: Boolean,
    val activa: Boolean,
) {
    /** Nunca se sincronizó: la pantalla Estado muestra "sin datos" y nunca un semáforo verde (RF01, criterio 3). */
    val sinDatos: Boolean
        get() = ultimaSincronizacion == null
}

/** Lo que trae el código QR o se escribe a mano para parear (informe, sección 13.4). */
data class DatosPareo(
    val urlBase: String,
    val codigo: String,
) {
    val codigoValido: Boolean
        get() = FORMATO_CODIGO.matches(codigo.trim().uppercase())

    companion object {
        /** Ocho caracteres en dos grupos, por ejemplo K7M2-QX9P. */
        val FORMATO_CODIGO = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}$")
        private const val PREFIJO_QR = "zonda:"

        /**
         * El QR contiene `zonda:<urlBase>#<codigo>`, por ejemplo `zonda:https://red.duckdns.org#K7M2-QX9P`.
         * Devuelve null si el texto no tiene ese formato.
         */
        fun desdeQr(contenido: String): DatosPareo? {
            val texto = contenido.trim()
            if (!texto.startsWith(PREFIJO_QR)) return null
            val cuerpo = texto.removePrefix(PREFIJO_QR)
            val separador = cuerpo.lastIndexOf('#')
            if (separador <= 0 || separador == cuerpo.lastIndex) return null
            val datos = DatosPareo(
                urlBase = cuerpo.substring(0, separador).trimEnd('/'),
                codigo = cuerpo.substring(separador + 1).uppercase(),
            )
            return if (datos.codigoValido && datos.urlBase.startsWith("http")) datos else null
        }
    }
}

/** Respuesta del servidor al pedir el vínculo con Telegram: un código de un uso y el enlace al bot. */
data class VinculoTelegram(
    val codigo: String,
    val enlaceBot: String,
)
