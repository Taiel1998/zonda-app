package ar.edu.uade.zonda.data.remote

import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Revision
import com.google.gson.JsonParseException
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import retrofit2.HttpException

/**
 * Para qué se está llamando a la API. Cambia el significado de algunos códigos: en el pareo un 400 es
 * "código inválido" y un 410 "código vencido o usado"; en la sincronización un 410 es "cursor
 * inválido"; en Telegram un 409 es "no está vinculado" y un 503 "el servidor no tiene el bot".
 */
enum class OperacionHttp { GENERAL, PAREO, SINCRONIZACION, TELEGRAM }

/**
 * Ejecuta una llamada a la API y traduce lo que puede salir mal a [ErrorZonda], que es lo único que
 * conocen el dominio y la interfaz (informe, sección 14.3). Es el único lugar donde la app interpreta
 * códigos HTTP (docs/decisiones.md, decisión 12).
 */
suspend fun <T> conErroresTraducidos(operacion: OperacionHttp = OperacionHttp.GENERAL, bloque: suspend () -> T): T =
    try {
        bloque()
    } catch (e: ErrorZonda) {
        throw e
    } catch (e: HttpException) {
        throw traducirHttp(e, operacion)
    } catch (e: SSLException) {
        // Android no pudo verificar el certificado: no se mandó ni el código ni el token.
        throw ErrorZonda.ServidorNoVerificado(e)
    } catch (e: CertificateException) {
        throw ErrorZonda.ServidorNoVerificado(e)
    } catch (e: IOException) {
        // Sin red, servidor apagado, nombre que no resuelve o tiempo de espera agotado.
        throw ErrorZonda.SinConexion(e)
    } catch (e: JsonParseException) {
        throw ErrorZonda.ErrorDelServidor(null, e)
    }

fun traducirHttp(error: HttpException, operacion: OperacionHttp): ErrorZonda = when (error.code()) {
    400 -> if (operacion == OperacionHttp.PAREO) ErrorZonda.CodigoInvalido() else ErrorZonda.ErrorDelServidor(400, error)
    401 -> ErrorZonda.NoAutorizado()
    404 -> ErrorZonda.NoEncontrado()
    409 -> if (operacion == OperacionHttp.TELEGRAM) {
        ErrorZonda.TelegramSinVincular()
    } else {
        revisionAjenaDe(error)?.let { ErrorZonda.Conflicto(it) } ?: ErrorZonda.ErrorDelServidor(409, error)
    }
    410 -> if (operacion == OperacionHttp.PAREO) ErrorZonda.CodigoVencido() else ErrorZonda.CursorInvalido()
    503 -> if (operacion == OperacionHttp.TELEGRAM) ErrorZonda.TelegramNoDisponible() else ErrorZonda.ErrorDelServidor(503, error)
    else -> ErrorZonda.ErrorDelServidor(error.code(), error)
}

/** El 409 trae `{"detail": {"revisionAjena": {...}}}`; si no se puede leer, se trata como error del servidor. */
private fun revisionAjenaDe(error: HttpException): Revision? = try {
    val cuerpo = error.response()?.errorBody()?.string() ?: return null
    ProveedorDeApiRetrofit.gson.fromJson(cuerpo, DetalleConflictoDto::class.java)?.detail?.revisionAjena?.aDominio()
} catch (e: Exception) {
    null
}
