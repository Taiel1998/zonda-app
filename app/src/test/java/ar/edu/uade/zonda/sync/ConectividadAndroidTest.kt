package ar.edu.uade.zonda.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConectividadAndroidTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val administrador: ConnectivityManager = contexto.getSystemService(ConnectivityManager::class.java)
    private val sombra = shadowOf(administrador)

    private fun redActivaConInternet(tiene: Boolean) {
        val capacidades = ShadowNetworkCapabilities.newInstance()
        if (tiene) shadowOf(capacidades).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        sombra.setNetworkCapabilities(administrador.activeNetwork, capacidades)
    }

    @Test
    fun `hay conexion solo cuando la red activa tiene acceso a internet`() = runTest {
        val conectividad = ConectividadAndroid(contexto)

        redActivaConInternet(true)
        assertTrue(conectividad.hayConexion())

        redActivaConInternet(false)
        assertFalse(conectividad.hayConexion())
    }

    @Test
    fun `sin red activa no hay conexion`() = runTest {
        sombra.setActiveNetworkInfo(null)

        assertFalse(ConectividadAndroid(contexto).hayConexion())
    }

    @Test
    fun `el flujo emite el estado inicial y despues cada cambio, sin repetir`() = runTest {
        redActivaConInternet(true)

        ConectividadAndroid(contexto).conectado.test {
            assertTrue(awaitItem())
            val callback = sombra.networkCallbacks.single()

            sombra.setActiveNetworkInfo(null)
            callback.onLost(ShadowNetwork.newInstance(1))
            assertFalse(awaitItem())

            callback.onAvailable(ShadowNetwork.newInstance(1))
            assertTrue(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, sombra.networkCallbacks.size)
    }

    @Test
    fun `perder la red por defecto es quedarse sin conexion aunque Android todavia la muestre activa`() = runTest {
        redActivaConInternet(true)

        ConectividadAndroid(contexto).conectado.test {
            assertTrue(awaitItem())

            // Así llega el modo avión en el emulador: primero el aviso y después se actualiza la red activa.
            sombra.networkCallbacks.single().onLost(ShadowNetwork.newInstance(1))
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
