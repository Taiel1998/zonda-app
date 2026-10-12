package ar.edu.uade.zonda.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Agrega `Authorization: Bearer <token>` a cada pedido. El token se lee en el momento, no al crear
 * el cliente: así un pareo nuevo (token cambiado) se usa en el pedido siguiente sin reconstruir nada.
 * Si no hay token (red todavía no pareada) el pedido sale sin cabecera y la API responde 401.
 */
class InterceptorDeToken(
    private val token: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val valor = token() ?: return chain.proceed(chain.request())
        val pedido = chain.request().newBuilder()
            .header("Authorization", "Bearer $valor")
            .build()
        return chain.proceed(pedido)
    }
}
