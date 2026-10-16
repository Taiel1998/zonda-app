package ar.edu.uade.zonda

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.zonda.ui.detalle.RutaDetalle
import ar.edu.uade.zonda.ui.detalle.detalleAlerta
import ar.edu.uade.zonda.ui.navigation.Rutas
import ar.edu.uade.zonda.ui.navigation.volver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * La pantalla puente de los enlaces del aviso (cambio 23). Que Android la cree de cero en cada toque
 * depende de cómo está declarada en el manifiesto, así que eso se prueba leyendo el manifiesto; lo que
 * hace con el enlace, arrancándola. Al final, el intent que arma se le da a una Activity con el grafo
 * real del detalle (pantallas simples en lugar de las de verdad, como en VolverDesdeEnlaceTest) para
 * ver que Zonda queda con Estado debajo del detalle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PuenteDelAvisoTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()

    /** Como lo arma el bot (backend/app/telegram.py, enlace_del_aviso). */
    private val enlace = "https://taiel1998.github.io/a/a-e392b6acc27e?red=r-1&severidad=alta&servicio=ssh&deteccion=2026-10-15T23:07:00Z&aviso=2026-10-15T23:07:05Z"

    /** Toca el botón del aviso: Telegram abre el enlace con una tarea nueva y Android elige la puente. */
    private fun tocarElAviso(uri: String = enlace): Pair<Activity, Intent?> {
        val intento = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setClass(app, PuenteDelAvisoActivity::class.java)
        val puente = Robolectric.buildActivity(PuenteDelAvisoActivity::class.java, intento).create().get()
        return puente to shadowOf(puente).nextStartedActivity
    }

    @Test
    fun `el enlace del aviso lo atiende la puente y no MainActivity`() {
        val intento = Intent(Intent.ACTION_VIEW, Uri.parse(enlace)).addCategory(Intent.CATEGORY_BROWSABLE)

        val quienes = app.packageManager.queryIntentActivities(intento, 0).map { it.activityInfo.name }

        assertEquals(listOf(PuenteDelAvisoActivity::class.java.name), quienes)
    }

    @Test
    fun `la puente arranca de cero en cada toque - sin afinidad, fuera de recientes y sin historial`() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, PuenteDelAvisoActivity::class.java), 0)

        // Sin afinidad, Android no la junta con la tarea de Zonda ni con una suya anterior.
        assertTrue(info.taskAffinity.isNullOrEmpty())
        assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        assertTrue(info.flags and ActivityInfo.FLAG_NO_HISTORY != 0)
        assertEquals(ActivityInfo.LAUNCH_MULTIPLE, info.launchMode)
        assertTrue(info.exported)
    }

    @Test
    fun `le pasa el enlace a MainActivity con una tarea limpia y se cierra`() {
        val (puente, abierta) = tocarElAviso()

        assertNotNull(abierta)
        assertEquals(MainActivity::class.java.name, abierta!!.component?.className)
        assertEquals(Intent.ACTION_VIEW, abierta.action)
        assertEquals(enlace, abierta.dataString)
        val tareaLimpia = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        assertEquals(tareaLimpia, abierta.flags and tareaLimpia)
        // Solo el enlace: nada de lo que traiga el intent de otra app pasa a Zonda.
        assertNull(abierta.extras)
        assertTrue(puente.isFinishing)
    }

    @Test
    fun `no pasa enlaces que no son del aviso`() {
        listOf(
            "zonda://alerta/a-1?red=r-1",
            "zonda://alertas?red=r-1&estado=sin_revisar&severidad=alta",
            "http://taiel1998.github.io/a/a-1",
            "https://otro.github.io/a/a-1",
            "https://taiel1998.github.io/b/a-1",
        ).forEach { uri ->
            val (puente, abierta) = tocarElAviso(uri)

            assertNull(uri, abierta)
            assertTrue(uri, puente.isFinishing)
        }
    }

    @Test
    fun `sin enlace solo se cierra`() {
        val puente = Robolectric.buildActivity(PuenteDelAvisoActivity::class.java, Intent(app, PuenteDelAvisoActivity::class.java)).create().get()

        assertNull(shadowOf(puente).nextStartedActivity)
        assertTrue(puente.isFinishing)
    }

    @Test
    fun `con lo que pasa la puente, Zonda abre el detalle con Estado debajo, cada vez`() {
        repeat(2) { vez ->
            val pasado = tocarElAviso().second!!

            val (actividad, nav) = abrirZonda(pasado)

            assertEquals("toque ${vez + 1}", RutaDetalle.PATRON, nav.currentDestination?.route)
            assertEquals("a-e392b6acc27e", nav.currentBackStackEntry?.arguments?.getString(RutaDetalle.ARG_ID))
            assertEquals(Rutas.ESTADO, nav.previousBackStackEntry?.destination?.route)
            // Arma todo de una: no cierra la Activity para relanzarla.
            assertNull(shadowOf(actividad).nextStartedActivity)
            assertFalse(actividad.isFinishing)
        }
    }

    /** MainActivity con el grafo real del detalle; las pantallas son simples (las de verdad necesitan la base cifrada). */
    private fun abrirZonda(intento: Intent): Pair<ComponentActivity, NavHostController> {
        lateinit var nav: NavHostController
        val actividad = Robolectric.buildActivity(ComponentActivity::class.java, intento).setup().get()
        actividad.setContent {
            val controlador = rememberNavController()
            nav = controlador
            NavHost(navController = controlador, startDestination = Rutas.ESTADO) {
                composable(Rutas.ESTADO) { Text("Estado") }
                detalleAlerta(alVolver = { controlador.volver() }) { alertaId, _, _ -> Text("Detalle de $alertaId") }
            }
        }
        compose.waitForIdle()
        return actividad to nav
    }
}
