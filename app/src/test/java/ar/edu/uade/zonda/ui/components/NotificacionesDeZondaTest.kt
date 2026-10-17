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
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.model.ModoNotificacion
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.ajustes.AvisosPorSeveridadContenido
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La tarjeta "Notificaciones de Zonda" (1.0.4; Figma 51-1688 y 463-70): una fila por severidad con lo
 * elegido y un interruptor, la hoja de los cuatro modos que vuelve sola al elegir y el recordatorio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificacionesDeZondaTest {

    @get:Rule
    val compose = createComposeRule()

    private val cambios = mutableListOf<Pair<Severidad, ModoNotificacion>>()
    private val recordatorio = mutableListOf<Boolean>()
    private var activaciones = 0

    private val esInterruptor = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private val esOpcion = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /** Como el borrador de Ajustes: lo que se elige se ve enseguida en la fila. */
    private fun mostrar(notificaciones: PreferenciasNotificaciones = PreferenciasNotificaciones()) {
        compose.setContent {
            ZondaTheme {
                var actuales by remember { mutableStateOf(notificaciones) }
                NotificacionesDeZonda(
                    notificaciones = actuales,
                    alCambiar = { severidad, modo ->
                        cambios += severidad to modo
                        actuales = actuales.con(severidad, modo)
                    },
                    alCambiarRecordatorio = {
                        recordatorio += it
                        actuales = actuales.copy(recordatorio = it)
                    },
                    alActivar = { activaciones++ },
                )
            }
        }
    }

    private fun interruptor(indice: Int) = compose.onAllNodes(esInterruptor)[indice]

    @Test
    fun `de fabrica alta con sonido y vibracion, media solo vibracion, baja sin aviso y el recordatorio prendido`() {
        mostrar()

        compose.onNodeWithText("Sonido y vibración").assertIsDisplayed()
        compose.onNodeWithText("Solo vibración").assertIsDisplayed()
        compose.onNodeWithText("Sin aviso").assertIsDisplayed()
        compose.onNodeWithText("Recordatorio de altas").assertIsDisplayed()
        compose.onNodeWithText("Si una alta sigue sin revisar a los 15 minutos, te avisamos de nuevo.").assertIsDisplayed()
        compose.onAllNodes(esInterruptor).assertCountEquals(4)
        interruptor(0).assertIsOn()
        interruptor(1).assertIsOn()
        interruptor(2).assertIsOff()
        interruptor(3).assertIsOn()
    }

    @Test
    fun `tocar la fila abre la hoja de los cuatro modos y al elegir vuelve sola`() {
        mostrar()

        compose.onNodeWithText("Solo vibración").performClick()
        compose.onNodeWithText("¿Cómo te avisa Zonda las medias?").assertIsDisplayed()
        compose.onAllNodes(esOpcion).assertCountEquals(4)
        compose.onNodeWithText("Queda en la barra de notificaciones, sin sonar ni vibrar.").assertIsDisplayed()
        compose.onNode(hasText("En silencio") and esOpcion).performClick()

        assertEquals(listOf(Severidad.MEDIA to ModoNotificacion.EN_SILENCIO), cambios)
        compose.onNodeWithText("¿Cómo te avisa Zonda las medias?").assertDoesNotExist()
        compose.onNodeWithText("En silencio").assertIsDisplayed()
        // Elegir un modo que avisa pide el permiso si falta.
        assertEquals(1, activaciones)
    }

    @Test
    fun `apagar el interruptor deja esa severidad sin aviso`() {
        mostrar()

        interruptor(0).performClick()

        assertEquals(listOf(Severidad.ALTA to ModoNotificacion.SIN_AVISO), cambios)
        compose.onAllNodesWithText("Sin aviso").assertCountEquals(2)
        assertEquals(0, activaciones)
    }

    @Test
    fun `prender una severidad sin aviso pregunta como avisar antes de cambiarla`() {
        mostrar()

        interruptor(2).performClick()

        assertTrue(cambios.isEmpty())
        compose.onNodeWithText("¿Cómo te avisa Zonda las bajas?").assertIsDisplayed()
        compose.onNode(hasText("Solo sonido") and esOpcion).performClick()
        assertEquals(listOf(Severidad.BAJA to ModoNotificacion.SOLO_SONIDO), cambios)
        interruptor(2).assertIsOn()
    }

    @Test
    fun `el recordatorio se apaga y se prende con su interruptor`() {
        mostrar()

        interruptor(3).performClick()
        interruptor(3).assertIsOff()
        interruptor(3).performClick()

        assertEquals(listOf(false, true), recordatorio)
        assertEquals(1, activaciones)
    }

    @Test
    fun `Avisos por severidad muestra Telegram y Zonda en dos tarjetas, sin el recuadro del sonido del sistema`() {
        compose.setContent {
            ZondaTheme {
                AvisosPorSeveridadContenido(
                    avisos = PreferenciasAvisos(),
                    alCambiar = { _, _ -> },
                    notificaciones = PreferenciasNotificaciones(),
                    alCambiarSeveridades = {},
                    alGuardar = {},
                    alVolver = {},
                )
            }
        }

        compose.onNodeWithText("Avisos por Telegram").assertIsDisplayed()
        compose.onNodeWithText("Notificaciones de Zonda").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Te avisan de las alertas que no te llegaron por Telegram. Pueden tardar hasta 15 minutos.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sonido de los avisos").assertDoesNotExist()
        // Tres interruptores de Telegram, tres de Zonda y el del recordatorio.
        compose.onAllNodes(esInterruptor).assertCountEquals(7)
    }
}
