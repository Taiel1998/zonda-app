package ar.edu.uade.zonda.domain.model

/**
 * Errores que las capas de datos le devuelven al dominio y que la interfaz traduce a un mensaje
 * con una salida (informe, sección 14.3). Son excepciones para que atraviesen las corrutinas sin
 * envolver cada resultado.
 */
sealed class ErrorZonda(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa) {

    /** No hay red o el servidor no responde. Lo que se hizo queda guardado y pendiente. */
    class SinConexion(causa: Throwable? = null) : ErrorZonda("Sin conexión", causa)

    /**
     * Android no pudo verificar el certificado, o la dirección es http y Android no deja usarla
     * (RNF04): no se envía ni el código ni el token.
     */
    class ServidorNoVerificado(causa: Throwable? = null) : ErrorZonda("No se pudo verificar el servidor", causa)

    /** Respuesta 401: el token venció o fue revocado. Se pide un pareo nuevo sin borrar la caché. */
    class NoAutorizado : ErrorZonda("El token venció o fue revocado")

    /** Respuesta 404. */
    class NoEncontrado : ErrorZonda("No se encontró")

    /** Respuesta 409 al revisar: otra persona revisó la alerta antes. Trae esa revisión. */
    class Conflicto(val revisionAjena: Revision) : ErrorZonda("Otra persona revisó esta alerta")

    /** Respuesta 410 al sincronizar: el servidor no reconoce el cursor y hay que sincronizar desde cero. */
    class CursorInvalido : ErrorZonda("El cursor de sincronización ya no es válido")

    /** Respuesta 400 al parear: el código no existe. */
    class CodigoInvalido : ErrorZonda("El código no es válido")

    /** Respuesta 410 al parear: el código venció o ya se usó. La app ofrece escanear otro. */
    class CodigoVencido : ErrorZonda("El código venció o ya se usó")

    /** Respuesta 5xx o tiempo de espera agotado. Se reintenta con espera exponencial. */
    class ErrorDelServidor(val codigo: Int?, causa: Throwable? = null) : ErrorZonda("Error del servidor", causa)

    /** Datos que no cumplen una regla del dominio, por ejemplo una revisión sin comentario. */
    class Validacion(val campo: String, mensaje: String) : ErrorZonda(mensaje)

    /** Respuesta 503 en las rutas de Telegram: el servidor de la red no tiene el bot configurado. */
    class TelegramNoDisponible : ErrorZonda("El servidor no tiene el bot de Telegram")

    /** Respuesta 409 al mandar el aviso de prueba: el chat ya no está vinculado (por ejemplo, bloquearon el bot). */
    class TelegramSinVincular : ErrorZonda("Telegram no está vinculado")
}
