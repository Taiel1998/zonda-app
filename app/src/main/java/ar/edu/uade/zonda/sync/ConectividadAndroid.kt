package ar.edu.uade.zonda.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import ar.edu.uade.zonda.domain.repository.Conectividad
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Si el teléfono tiene una red con acceso a internet, según `ConnectivityManager`. Decide si una
 * acción se envía en el momento o queda encolada, y muestra el aviso "sin conexión" (informe, 14.1).
 *
 * Que haya red no garantiza que el servidor responda: eso lo resuelve la llamada misma, que
 * devuelve [ar.edu.uade.zonda.domain.model.ErrorZonda.SinConexion] si falla.
 */
class ConectividadAndroid(context: Context) : Conectividad {

    private val administrador: ConnectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** Emite el estado actual al suscribirse y cada cambio después; no repite valores iguales. */
    override val conectado: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hayConexionAhora())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(hayConexionAhora())
        administrador.registerDefaultNetworkCallback(callback)
        awaitClose { administrador.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override suspend fun hayConexion(): Boolean = hayConexionAhora()

    private fun hayConexionAhora(): Boolean {
        val red = administrador.activeNetwork ?: return false
        val capacidades = administrador.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
