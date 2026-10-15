package ar.edu.uade.zonda.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.zonda.ui.detalle.RutaDetalle
import ar.edu.uade.zonda.ui.detalle.detalleAlerta
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
 * La flecha de volver del detalle (cambios 20 y 21). Telegram abre el enlace del aviso sin pedir una
 * tarea nueva, así que Zonda queda dentro de la tarea de Telegram con el detalle como única pantalla.
 * El grafo usa la ruta y los enlaces reales del detalle, con pantallas simples en lugar de las de
 * verdad (que necesitan la base cifrada), y la flecha usa [volver], como en ZondaNavHost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolverDesdeEnlaceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController

    /** Arranca la Activity con ese intent y dibuja Estado y el detalle. */
    private fun abrir(intento: Intent): ComponentActivity {
        val actividad = Robolectric.buildActivity(ComponentActivity::class.java, intento).setup().get()
        actividad.setContent {
            val controlador = rememberNavController()
            nav = controlador
            NavHost(navController = controlador, startDestination = Rutas.ESTADO) {
                composable(Rutas.ESTADO) { Text("Estado") }
                detalleAlerta(alVolver = { controlador.volver() }) { alertaId, _, alVolver ->
                    Column {
                        Button(onClick = alVolver) { Text("Volver") }
                        Text("Detalle de $alertaId")
                    }
                }
            }
        }
        compose.waitForIdle()
        return actividad
    }

    /** Como lo abre Telegram: el enlace del aviso, sin FLAG_ACTIVITY_NEW_TASK. */
    private fun desdeTelegram() = Intent(Intent.ACTION_VIEW, Uri.parse("https://taiel1998.github.io/a/a-1?red=r-1&severidad=alta&servicio=ssh"))

    /** Toca la flecha del detalle abierto desde Telegram y devuelve lo que se abrió. */
    private fun tocarVolverDesdeTelegram(): Pair<ComponentActivity, Intent?> {
        val actividad = abrir(desdeTelegram())
        compose.onNodeWithText("Volver").performClick()
        compose.waitForIdle()
        return actividad to shadowOf(actividad).nextStartedActivity
    }

    @Test
    fun `abierto desde Telegram, el detalle es la unica pantalla`() {
        abrir(desdeTelegram())

        assertEquals(RutaDetalle.PATRON, nav.currentDestination?.route)
        assertNull(nav.previousBackStackEntry)
        compose.onNodeWithText("Detalle de a-1").assertExists()
    }

    @Test
    fun `ahi la flecha abre Zonda en su propia tarea y cierra la copia que quedo en Telegram`() {
        val (actividad, nueva) = tocarVolverDesdeTelegram()

        assertNotNull(nueva)
        assertEquals(ComponentActivity::class.java.name, nueva!!.component?.className)
        assertTrue(nueva.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(nueva.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(actividad.isFinishing)
    }

    @Test
    fun `la tarea nueva arranca en Estado`() {
        val (_, nueva) = tocarVolverDesdeTelegram()

        val relanzada = abrir(nueva!!)

        assertEquals(Rutas.ESTADO, nav.currentDestination?.route)
        assertNull(nav.previousBackStackEntry)
        assertNull(shadowOf(relanzada).nextStartedActivity)
        assertFalse(relanzada.isFinishing)
    }

    @Test
    fun `desde la app, la flecha vuelve a la pantalla anterior sin abrir otra tarea`() {
        val actividad = abrir(Intent(Intent.ACTION_MAIN))
        compose.runOnIdle { nav.navigate(RutaDetalle.crear("a-1")) }
        compose.waitForIdle()

        compose.onNodeWithText("Volver").performClick()
        compose.waitForIdle()

        assertEquals(Rutas.ESTADO, nav.currentDestination?.route)
        assertNull(shadowOf(actividad).nextStartedActivity)
        assertFalse(actividad.isFinishing)
    }

    @Test
    fun `la notificacion pide una tarea nueva y Navigation pone Estado debajo del detalle`() {
        // Como Notificador.abrir: el enlace interno, a la Activity de la app, con NEW_TASK y CLEAR_TOP.
        val notificacion = Intent(Intent.ACTION_VIEW, Uri.parse(EnlacesInternos.detalle("r-1", "a-1")), RuntimeEnvironment.getApplication(), ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val actividad = abrir(notificacion)
        // Navigation rearma la tarea desde la pantalla principal: cierra esta copia y abre otra.
        val rearmada = shadowOf(actividad).nextStartedActivity
        assertTrue(actividad.isFinishing)

        abrir(rearmada)

        assertEquals(RutaDetalle.PATRON, nav.currentDestination?.route)
        assertEquals(Rutas.ESTADO, nav.previousBackStackEntry?.destination?.route)
    }
}
