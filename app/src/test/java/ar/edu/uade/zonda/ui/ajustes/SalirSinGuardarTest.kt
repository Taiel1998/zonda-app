package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La flecha de las pantallas con "Guardar" (Avisos por severidad y Severidad por tipo de ataque). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SalirSinGuardarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `con cambios sin guardar la flecha pregunta antes de salir, y sin cambios sale directo`() {
        var hayCambios by mutableStateOf(true)
        var salidas = 0
        compose.setContent {
            ZondaTheme {
                val volver = salidaConCambios(hayCambios) { salidas++ }
                BotonPrincipal(texto = "Volver", alTocar = volver)
            }
        }

        // Antes la flecha descartaba lo cambiado sin avisar.
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithText("¿Salir sin guardar?").assertIsDisplayed()
        compose.onNodeWithText("Hay cambios sin guardar.").assertIsDisplayed()
        assertEquals(0, salidas)
        compose.onNodeWithText("Cancelar").performClick()
        compose.onAllNodesWithText("¿Salir sin guardar?").assertCountEquals(0)
        assertEquals(0, salidas)

        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithText("Salir igual").performClick()
        assertEquals(1, salidas)

        // Sin cambios no hay nada que perder: sale sin preguntar.
        hayCambios = false
        compose.waitForIdle()
        compose.onNodeWithText("Volver").performClick()
        compose.onAllNodesWithText("¿Salir sin guardar?").assertCountEquals(0)
        assertEquals(2, salidas)
    }
}
