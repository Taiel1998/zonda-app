package ar.edu.uade.zonda.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los avisos de Telegram por severidad (cambio 6; Figma 51-1688 y 443-3668): una fila con lo elegido y
 * un interruptor, y la hoja de dos opciones que vuelve sola al elegir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvisosDeTelegramTest {

    @get:Rule
    val compose = createComposeRule()

    private val cambios = mutableListOf<Pair<Severidad, ModoAviso>>()

    private val esInterruptor = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private val esOpcion = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /** Como el borrador de Ajustes: lo que se elige se ve enseguida en la fila. */
    private fun mostrar(avisos: PreferenciasAvisos = PreferenciasAvisos()) {
        compose.setContent {
            ZondaTheme {
                var actuales by remember { mutableStateOf(avisos) }
                AvisosDeTelegram(
                    avisos = actuales,
                    alCambiar = { severidad, modo ->
                        cambios += severidad to modo
                        actuales = actuales.con(severidad, modo)
                    },
                )
            }
        }
    }

    private fun interruptor(indice: Int) = compose.onAllNodes(esInterruptor)[indice]

    @Test
    fun `cada severidad dice como avisa y solo sin aviso tiene el interruptor apagado`() {
        mostrar()

        compose.onNodeWithText("Con sonido").assertIsDisplayed()
        compose.onNodeWithText("En silencio").assertIsDisplayed()
        compose.onNodeWithText("Sin aviso").assertIsDisplayed()
        compose.onAllNodes(esInterruptor).assertCountEquals(3)
        interruptor(0).assertIsOn()
        interruptor(1).assertIsOn()
        interruptor(2).assertIsOff()
    }

    @Test
    fun `apagar el interruptor deja esa severidad sin aviso`() {
        mostrar()

        interruptor(0).performClick()

        assertEquals(listOf(Severidad.ALTA to ModoAviso.SIN_AVISO), cambios)
        compose.onAllNodesWithText("Sin aviso").assertCountEquals(2)
    }

    @Test
    fun `tocar la fila abre la hoja y al elegir vuelve sola`() {
        mostrar()

        compose.onNodeWithText("En silencio").performClick()
        compose.onNodeWithText("¿Cómo te avisamos las medias?").assertIsDisplayed()
        compose.onAllNodes(esOpcion).assertCountEquals(2)
        compose.onNode(hasText("Con sonido") and esOpcion).performClick()

        assertEquals(listOf(Severidad.MEDIA to ModoAviso.SONIDO), cambios)
        compose.onNodeWithText("¿Cómo te avisamos las medias?").assertDoesNotExist()
        compose.onAllNodesWithText("Con sonido").assertCountEquals(2)
    }

    @Test
    fun `prender una severidad sin aviso pregunta como avisar antes de cambiarla`() {
        mostrar()

        interruptor(2).performClick()

        assertTrue(cambios.isEmpty())
        compose.onNodeWithText("¿Cómo te avisamos las bajas?").assertIsDisplayed()
        compose.onNode(hasText("En silencio") and esOpcion).performClick()
        assertEquals(listOf(Severidad.BAJA to ModoAviso.SILENCIO), cambios)
        interruptor(2).assertIsOn()
    }
}
