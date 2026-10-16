package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Qué es cada tipo" (cambios 8 y 14; Figma 443-2683 y 443-3488): los dos bloques, una oración por tipo
 * con el punto de la severidad elegida y de dónde se cambia. Se dibuja el contenido sin la ventana de
 * la hoja.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HojaTiposTest {

    @get:Rule
    val compose = createComposeRule()

    private fun dibujar(severidades: SeveridadPorTipo, alCerrar: () -> Unit = {}) {
        compose.setContent {
            ZondaTheme {
                // Como en la hoja: se desplaza si no entra en la pantalla.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { ContenidoHojaTipos(severidades, alCerrar) }
            }
        }
    }

    @Test
    fun `explica los dos bloques y cada tipo con su oracion`() {
        dibujar(SeveridadPorTipo.RECOMENDADA)

        compose.onNodeWithText("Qué es cada tipo").assertIsDisplayed()
        compose.onNodeWithText("LO QUE DETECTÓ LA IA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("La IA vio algo raro y otro modelo le pone el tipo de ataque que parece.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin tipo identificado. No se parece a ningún ataque conocido. Puede ser uno nuevo.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Escaneo de puertos. Prueba puertos para ver qué servicios hay. Suele ser el paso previo a un ataque.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Probable falsa alarma. Raro para la IA, pero parecido a tráfico normal, como un backup.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("LO QUE LA IA NO DETECTÓ").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lo marcaron los otros dos detectores de Zonda.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Servicio no autorizado. Las reglas vieron una conexión a un servicio que no está en la lista de permitidos.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Volumen inusual. Los umbrales vieron demasiadas conexiones a un mismo servicio en un minuto.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(
            "El punto de color es la severidad que elegiste para cada tipo. Se cambia en Ajustes → Severidad por tipo de ataque.",
        ).performScrollTo().assertIsDisplayed()
        // Un renglón por tipo, sin "Otros".
        compose.onAllNodesWithText("Otros", substring = true).assertCountEquals(0)
    }

    @Test
    fun `el punto de cada tipo es la severidad que eligio la red`() {
        dibujar(SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.DDOS, Severidad.BAJA))

        // Fuerza bruta y sin tipo: alta; escaneo, servicio no autorizado y volumen inusual: media;
        // probable falsa alarma y DDoS: baja.
        compose.onAllNodesWithContentDescription("Severidad alta").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Severidad media").assertCountEquals(3)
        compose.onAllNodesWithContentDescription("Severidad baja").assertCountEquals(2)
    }

    @Test
    fun `entendido cierra la hoja`() {
        var cerrada = false
        dibujar(SeveridadPorTipo.RECOMENDADA, alCerrar = { cerrada = true })

        compose.onNodeWithText("Entendido").performScrollTo().performClick()

        assertTrue(cerrada)
    }
}
