package ar.edu.uade.zonda.ui.primerinicio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Severidad por tipo de ataque en los dos bloques del cambio 8 (Figma 66-3212 y 104-2): lo que detectó
 * la IA y lo que no, el ⓘ de "Qué es cada tipo" y una opción elegida por tipo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeveridadesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun dibujar(alGuardar: () -> Unit = {}) {
        compose.setContent {
            ZondaTheme {
                var severidades by remember { mutableStateOf(SeveridadPorTipo.RECOMENDADA) }
                SeveridadesScreen(
                    severidades = severidades,
                    alCambiar = { tipo, severidad -> severidades = severidades.con(tipo, severidad) },
                    textoBoton = "Guardar",
                    alGuardar = alGuardar,
                    titulo = "Severidad por tipo de ataque",
                    intro = "Elegí qué tan urgente es cada caso.",
                )
            }
        }
    }

    /** Dónde empieza un texto, de arriba hacia abajo (la columna se dibuja entera aunque no entre). */
    private fun alturaDe(texto: String): Float = compose.onNodeWithText(texto).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `los tipos van en dos bloques, primero lo que detecto la IA`() {
        dibujar()

        compose.onNodeWithText("Elegí qué tan urgente es cada caso.").assertIsDisplayed()
        val alturas = listOf(
            "LO QUE DETECTÓ LA IA",
            "Fuerza bruta (SSH, FTP)",
            "Tráfico anómalo sin tipo identificado",
            "Escaneo de puertos",
            "Denegación de servicio (DDoS)",
            "Probable falsa alarma",
            "LO QUE LA IA NO DETECTÓ",
            "Servicio no autorizado",
            "Lo detectan las reglas",
            "Volumen inusual de conexiones",
            "Lo detectan los umbrales",
            "Los cambios valen para todos los teléfonos pareados a esta red.",
        ).map(::alturaDe)
        assertEquals(alturas.sorted(), alturas)
        assertEquals(alturas.size, alturas.distinct().size)
    }

    @Test
    fun `cada tipo tiene una opcion elegida y tocar otra la cambia`() {
        dibujar()
        // Escaneo de puertos es el tercer tipo: viene en media y se pasa a alta.
        val altaDeEscaneo = compose.onAllNodesWithText("Alta")[2]

        compose.onAllNodesWithText("Alta").assertCountEquals(7)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(7)
        compose.onAllNodesWithText("Media")[2].assertIsSelected()

        altaDeEscaneo.performScrollTo().performClick()

        altaDeEscaneo.assertIsSelected()
        compose.onAllNodesWithText("Media")[2].assertIsNotSelected()
    }

    @Test
    fun `el i del primer bloque abre que es cada tipo`() {
        dibujar()

        compose.onNodeWithContentDescription("Qué es cada tipo").performClick()

        compose.onNodeWithText("Qué es cada tipo").assertIsDisplayed()
        compose.onNodeWithText("Lo marcaron los otros dos detectores de Zonda.").assertExists()
    }

    @Test
    fun `guardar llama a la accion`() {
        var guardado = false
        dibujar(alGuardar = { guardado = true })

        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        assertTrue(guardado)
    }
}
