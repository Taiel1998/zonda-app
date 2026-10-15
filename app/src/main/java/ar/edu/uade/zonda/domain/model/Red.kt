package ar.edu.uade.zonda.domain.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

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
    /** Un chat de Telegram recibe los avisos de esta red en este teléfono (lo confirma el servidor). */
    val telegramVinculado: Boolean,
    val activa: Boolean,
    /** El servidor respondió 401: el token venció o fue revocado. La caché sigue; hay que parear de nuevo (RNF12). */
    val accesoVencido: Boolean = false,
    /** Cuándo se emitió y cuándo vence el acceso de este teléfono (vienen en la respuesta del pareo; 90 días). */
    val accesoEmitido: Instant? = null,
    val accesoVence: Instant? = null,
    /** Usuario del bot de Telegram de la red, sin la arroba; null hasta que el servidor lo diga. */
    val telegramBot: String? = null,
) {
    /** Nunca se sincronizó: la pantalla Estado muestra "sin datos" y nunca un semáforo verde (RF01, criterio 3). */
    val sinDatos: Boolean
        get() = ultimaSincronizacion == null
}

/**
 * Lo que trae el código QR o se escribe a mano para parear (informe, sección 13.4). [codigoVence]
 * solo viene en el QR: con el ingreso a mano no se sabe hasta cuándo vale el código.
 */
data class DatosPareo(
    val urlBase: String,
    val codigo: String,
    val codigoVence: Instant? = null,
) {
    val codigoValido: Boolean
        get() = FORMATO_CODIGO.matches(codigo.trim().uppercase())

    companion object {
        /** Ocho caracteres en dos grupos, por ejemplo K7M2-QX9P. */
        val FORMATO_CODIGO = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}$")
        private const val PREFIJO_QR = "zonda:"

        /**
         * La dirección como la escribió la persona, lista para usar: sin espacios ni barra final y con
         * `https://` si no puso esquema (`zonda-empresa.duckdns.org` → `https://zonda-empresa.duckdns.org`).
         * Un `http://` escrito a mano se respeta: es la API local en el emulador (`http://10.0.2.2:8000`).
         */
        fun normalizarDireccion(texto: String): String {
            val limpio = texto.trim().trimEnd('/')
            if (limpio.isEmpty()) return limpio
            val conEsquema = limpio.startsWith("https://", ignoreCase = true) || limpio.startsWith("http://", ignoreCase = true)
            return if (conEsquema) limpio else "https://$limpio"
        }

        /** Las letras y los números del código, en mayúsculas y sin el guion: `k7m2-qx9p` → `K7M2QX9P`. */
        fun caracteresDeCodigo(texto: String): String = texto.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

        /** El código como lo escribió la persona, en mayúsculas y con el guion: `k7m2qx9p` → `K7M2-QX9P`. */
        fun normalizarCodigo(texto: String): String {
            val caracteres = texto.uppercase().filter { it.isLetterOrDigit() }
            return if (caracteres.length == 8) caracteres.substring(0, 4) + "-" + caracteres.substring(4) else texto.trim().uppercase()
        }

        /**
         * El QR contiene `zonda:<urlBase>#<codigo>;vence=<fecha ISO>`, por ejemplo
         * `zonda:https://red.duckdns.org#K7M2-QX9P;vence=2026-10-12T02:19:00+00:00`. Un QR sin `vence`
         * (de un servidor anterior) también vale. Devuelve null si el texto no tiene ese formato.
         */
        fun desdeQr(contenido: String): DatosPareo? {
            val texto = contenido.trim()
            if (!texto.startsWith(PREFIJO_QR)) return null
            val cuerpo = texto.removePrefix(PREFIJO_QR)
            val separador = cuerpo.lastIndexOf('#')
            if (separador <= 0 || separador == cuerpo.lastIndex) return null
            val partes = cuerpo.substring(separador + 1).split(';')
            val datos = DatosPareo(
                urlBase = cuerpo.substring(0, separador).trimEnd('/'),
                codigo = partes.first().uppercase(),
                codigoVence = partes.drop(1).firstOrNull { it.startsWith(CLAVE_VENCE) }?.let { leerFecha(it.removePrefix(CLAVE_VENCE)) },
            )
            return if (datos.codigoValido && datos.urlBase.startsWith("http")) datos else null
        }

        private const val CLAVE_VENCE = "vence="

        /** Una fecha mal escrita no invalida el QR: solo se pierde la línea "válido hasta las HH:MM". */
        private fun leerFecha(texto: String): Instant? =
            try {
                OffsetDateTime.parse(texto).toInstant()
            } catch (e: DateTimeParseException) {
                null
            }
    }
}

/**
 * Los pasos del pareo que muestra la pantalla Conexión (Figma 49-521). Buscar el servidor y
 * verificar que sea el verdadero son el mismo pedido (`GET /salud` por HTTPS): terminan juntos.
 * La descarga de las alertas ya no es un paso: pasa en Estado, con su barra (Figma 50-616).
 */
enum class PasoPareo { BUSCANDO_SERVIDOR, REGISTRANDO_TELEFONO }

/**
 * El resultado del pareo: la red ya pareada y activa, y cuántas alertas de los últimos 30 días
 * tiene para descargar ("213 alertas de los últimos 30 días listas para descargar", Figma 35-692).
 */
data class RedPareada(
    val red: Red,
    val alertasParaDescargar: Int,
)

/**
 * Cómo va la primera descarga de una red (Figma 50-616): "120 de 213". [total] es null hasta que
 * llega la primera página, o si el servidor no lo manda.
 */
data class ProgresoDescarga(
    val redId: String,
    val descargadas: Int,
    val total: Int?,
)

/**
 * Respuesta del servidor al pedir el vínculo con Telegram: un código de un uso (10 minutos) y el
 * enlace que abre el chat con el bot (`https://t.me/<bot>?start=<código>`).
 */
data class VinculoTelegram(
    val codigo: String,
    val enlaceBot: String,
)

/** Lo que dice el servidor de Telegram en este teléfono: si hay un chat vinculado, con qué bot y cómo avisa. */
data class EstadoTelegram(
    val vinculado: Boolean,
    /** Usuario del bot, sin la arroba. */
    val bot: String,
    val avisos: PreferenciasAvisos,
)

/**
 * Lo que muestra Detalles técnicos sobre la conexión con el servidor de la red (Figma 66-3316).
 * Se lee de una llamada real a `GET /salud`, así que necesita conexión.
 */
data class DetallesConexion(
    /** Versión de TLS de la conexión ("TLSv1.3"), o null si es http sin cifrar (solo la API local de pruebas). */
    val versionTls: String?,
    /** Quién firmó el certificado del servidor, por ejemplo "Let's Encrypt". */
    val emisorCertificado: String?,
    val certificadoVence: Instant?,
    /** Vencimiento del acceso de este teléfono según el servidor. */
    val accesoVence: Instant?,
)
