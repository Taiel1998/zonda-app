package ar.edu.uade.zonda.data.remote

import ar.edu.uade.zonda.domain.model.Red
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Entrega el cliente de la API de cada red. Cada red tiene su propio servidor (su `urlBase`) y su
 * propio token, por eso no hay un único `ZondaApi` sino uno por red. Es una interfaz para que las
 * pruebas de los repositorios usen una API en memoria en lugar de HTTP.
 */
interface ProveedorDeApi {

    /** Cliente autenticado de una red pareada. */
    fun para(red: Red): ZondaApi = para(red.id, red.urlBase)

    fun para(redId: String, urlBase: String): ZondaApi

    /** Cliente sin token, para `GET /salud` y `POST /pareo` antes de tener la red. */
    fun sinToken(urlBase: String): ZondaApi

    /** Descarta el cliente de una red que se quitó. */
    fun olvidar(redId: String)
}

/**
 * Implementación con Retrofit sobre un único [OkHttpClient] (comparte conexiones e hilos); por red se
 * agrega solo el [InterceptorDeToken]. Los clientes se guardan en memoria: construir un Retrofit no es
 * gratis y una red se usa muchas veces.
 */
class ProveedorDeApiRetrofit(
    private val tokens: (redId: String) -> String?,
    private val clienteBase: OkHttpClient = clientePorDefecto(),
) : ProveedorDeApi {

    private val porRed = ConcurrentHashMap<String, ZondaApi>()

    override fun para(redId: String, urlBase: String): ZondaApi =
        porRed.getOrPut(clave(redId, urlBase)) { crear(urlBase, InterceptorDeToken { tokens(redId) }) }

    override fun sinToken(urlBase: String): ZondaApi = crear(urlBase, interceptor = null)

    override fun olvidar(redId: String) {
        porRed.keys.removeAll { it.startsWith("$redId|") }
    }

    private fun clave(redId: String, urlBase: String) = "$redId|${normalizar(urlBase)}"

    /** Lanza IllegalArgumentException si la dirección no es una URL http(s) válida. */
    private fun crear(urlBase: String, interceptor: Interceptor?): ZondaApi {
        val cliente = if (interceptor == null) clienteBase else clienteBase.newBuilder().addInterceptor(interceptor).build()
        return Retrofit.Builder()
            .baseUrl(normalizar(urlBase))
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ZondaApi::class.java)
    }

    companion object {
        /** Un solo Gson para toda la app: el contrato no necesita adaptadores. */
        val gson: Gson = GsonBuilder().create()

        /** Retrofit exige que la dirección base termine en barra. */
        fun normalizar(urlBase: String): String = urlBase.trim().trimEnd('/') + "/"

        /**
         * Tiempos de espera cortos: la API está en la misma red local o detrás de Caddy; si no contesta
         * en 10 segundos, la app lo informa como sin conexión y sigue con lo que tiene guardado.
         */
        fun clientePorDefecto(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
