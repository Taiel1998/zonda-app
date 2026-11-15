package ar.edu.uade.zonda.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La pestaña Administración, solo del administrador de la red activa (como el tablero, 15/11): la barra
 * de abajo la suma o la saca según el perfil, y a quien deja de ser administrador mientras la mira la app
 * lo lleva a su Ajustes. El grafo usa las rutas de verdad con pantallas simples, como VolverDesdeEnlaceTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdministracionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `la pestania Administracion es solo del administrador y va al final`() {
        assertEquals(listOf(Destino.Estado, Destino.Alertas, Destino.Ajustes, Destino.Administracion), Destino.barraInferior(conAdministracion = true))
        assertEquals(listOf(Destino.Estado, Destino.Alertas, Destino.Ajustes), Destino.barraInferior(conAdministracion = false))
    }

    @Test
    fun `en la barra dice Admin y TalkBack lee Administracion, y aparece o se va con el perfil`() {
        var conAdministracion by mutableStateOf(true)
        val elegidas = mutableListOf<Destino>()
        compose.setContent {
            ZondaTheme {
                BarraDePestanias(
                    pestanias = Destino.barraInferior(conAdministracion),
                    rutaActual = Rutas.AJUSTES,
                    alElegir = { elegidas += it },
                    alRetocar = {},
                )
            }
        }

        // "Administración" entero no entra en la barra: se ve "Admin." y TalkBack lee el nombre entero.
        compose.onNodeWithContentDescription("Administración").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("Admin.").assertCountEquals(0)
        compose.onNodeWithText("Ajustes").assertIsSelected()
        assertEquals(listOf<Destino>(Destino.Administracion), elegidas)

        conAdministracion = false
        compose.onAllNodesWithContentDescription("Administración").assertCountEquals(0)
        listOf("Estado", "Alertas", "Ajustes").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    private lateinit var nav: NavHostController

    /** Estado, Alertas, Ajustes, Administración y una pantalla que se abre desde ahí, como en la app. */
    private fun dibujarGrafo() {
        compose.setContent {
            val controlador = rememberNavController()
            nav = controlador
            NavHost(navController = controlador, startDestination = Rutas.ESTADO) {
                composable(Rutas.ESTADO) { Text("Pantalla Estado") }
                composable(Rutas.ALERTAS) { Text("Pantalla Alertas") }
                composable(Rutas.AJUSTES) { Text("Pantalla Ajustes") }
                composable(Rutas.ADMINISTRACION) { Text("Pantalla Administración") }
                composable(Rutas.PERSONAS) { Text("Pantalla Personas") }
            }
        }
        compose.waitForIdle()
    }

    private fun rutaActual(): String? = compose.runOnIdle { nav.currentDestination?.route }

    @Test
    fun `quien deja de ser administrador mientras mira Personas pasa a su Ajustes, y Personas no vuelve`() {
        dibujarGrafo()
        compose.runOnIdle {
            nav.irAPestania(Rutas.ADMINISTRACION)
            nav.navigate(Rutas.PERSONAS)
        }
        assertEquals(Rutas.PERSONAS, rutaActual())

        compose.runOnIdle { nav.salirDeAdministracion() }

        assertEquals(Rutas.AJUSTES, rutaActual())
        compose.onNodeWithText("Pantalla Ajustes").assertIsDisplayed()
        // Si después lo vuelven a hacer administrador, Administración arranca de cero: no reaparece Personas.
        compose.runOnIdle { nav.irAPestania(Rutas.ADMINISTRACION) }
        assertEquals(Rutas.ADMINISTRACION, rutaActual())
        val enLaPila = compose.runOnIdle { runCatching { nav.getBackStackEntry(Rutas.PERSONAS) }.isSuccess }
        assertFalse("Personas no tiene que volver", enLaPila)
    }

    @Test
    fun `fuera de Administracion, dejar de ser administrador no mueve a nadie`() {
        dibujarGrafo()
        compose.runOnIdle {
            // Administración quedó guardada abajo de Alertas.
            nav.irAPestania(Rutas.ADMINISTRACION)
            nav.navigate(Rutas.PERSONAS)
            nav.irAPestania(Rutas.ALERTAS)
        }

        compose.runOnIdle { nav.salirDeAdministracion() }

        assertEquals(Rutas.ALERTAS, rutaActual())
        // Lo guardado de Administración se descartó.
        compose.runOnIdle { nav.irAPestania(Rutas.ADMINISTRACION) }
        assertEquals(Rutas.ADMINISTRACION, rutaActual())
        assertTrue(compose.runOnIdle { runCatching { nav.getBackStackEntry(Rutas.PERSONAS) }.isFailure })
    }
}
