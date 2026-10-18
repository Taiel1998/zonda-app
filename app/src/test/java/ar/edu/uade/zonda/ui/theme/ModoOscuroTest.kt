package ar.edu.uade.zonda.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.Tema
import ar.edu.uade.zonda.ui.ajustes.AccionesAjustes
import ar.edu.uade.zonda.ui.ajustes.AjustesContenido
import ar.edu.uade.zonda.ui.ajustes.AjustesUiState
import ar.edu.uade.zonda.ui.ajustes.ContenidoHojaTema
import ar.edu.uade.zonda.ui.ajustes.RedConEstado
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El modo oscuro (corrección 8, decisión 43): lo elegido manda sobre el teléfono y los colores del plan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModoOscuroTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `el tema elegido manda sobre el del telefono y de fabrica lo sigue`() {
        assertTrue(Tema.COMO_EL_TELEFONO.esOscuro(telefonoOscuro = true))
        assertFalse(Tema.COMO_EL_TELEFONO.esOscuro(telefonoOscuro = false))
        assertFalse(Tema.CLARO.esOscuro(telefonoOscuro = true))
        assertTrue(Tema.OSCURO.esOscuro(telefonoOscuro = false))
    }

    @Test
    fun `en oscuro van los colores del plan y los puntos del semaforo no cambian`() {
        val leidos = mutableMapOf<String, Color>()
        compose.setContent {
            ZondaTheme(oscuro = true) {
                leidos["fondo"] = MaterialTheme.colorScheme.surface
                leidos["barra"] = MaterialTheme.colorScheme.surfaceContainer
                leidos["borde"] = MaterialTheme.colorScheme.outlineVariant
                leidos["texto"] = MaterialTheme.colorScheme.onSurface
                leidos["secundario"] = MaterialTheme.colorScheme.onSurfaceVariant
                leidos["boton"] = MaterialTheme.colorScheme.primary
                leidos["letraBoton"] = MaterialTheme.colorScheme.onPrimary
                leidos["elegido"] = MaterialTheme.colorScheme.secondaryContainer
                leidos["altaFondo"] = Tonos.Alta.fondo
                leidos["altaTexto"] = Tonos.Alta.texto
                leidos["altaPunto"] = Tonos.Alta.punto
                leidos["sinRevisar"] = Tonos.Info.fondo
                leidos["aviso"] = MaterialTheme.colorScheme.inverseSurface
                leidos["barraResumen"] = MaterialTheme.zonda.barra
            }
        }
        compose.waitForIdle()

        assertEquals(Color(0xFF1B1B19), leidos["fondo"])
        assertEquals(Color(0xFF262522), leidos["barra"])
        assertEquals(Color(0xFF3D3C37), leidos["borde"])
        assertEquals(Color(0xFFE8E6DF), leidos["texto"])
        assertEquals(Color(0xFFA9A79F), leidos["secundario"])
        assertEquals(Color(0xFF5DCAA5), leidos["boton"])
        assertEquals(Color(0xFF04342C), leidos["letraBoton"])
        assertEquals(Color(0xFF085041), leidos["elegido"])
        assertEquals(Color(0xFF501313), leidos["altaFondo"])
        assertEquals(Color(0xFFF7C1C1), leidos["altaTexto"])
        assertEquals(Color(0xFFE24B4A), leidos["altaPunto"])
        assertEquals(Color(0xFF042C53), leidos["sinRevisar"])
        // El aviso inferior se invierte: fondo claro.
        assertEquals(Color(0xFFE8E6DF), leidos["aviso"])
        assertEquals(Color(0xFF2F8F72), leidos["barraResumen"])
    }

    @Test
    fun `en claro todo queda como antes`() {
        val leidos = mutableMapOf<String, Color>()
        compose.setContent {
            ZondaTheme {
                leidos["fondo"] = MaterialTheme.colorScheme.surface
                leidos["boton"] = MaterialTheme.colorScheme.primary
                leidos["altaFondo"] = Tonos.Alta.fondo
                leidos["aviso"] = MaterialTheme.colorScheme.inverseSurface
            }
        }
        compose.waitForIdle()

        assertEquals(Color.White, leidos["fondo"])
        assertEquals(VerdeZonda, leidos["boton"])
        assertEquals(Color(0xFFFCEBEB), leidos["altaFondo"])
        assertEquals(GrisAviso, leidos["aviso"])
    }

    @Test
    fun `ajustes tiene Apariencia con el tema elegido y la hoja lo cambia`() {
        var elegido: Tema? = null
        val red = Fixtures.red("red-1")
        compose.setContent {
            ZondaTheme(oscuro = true) {
                AjustesContenido(
                    estado = AjustesUiState(
                        redes = listOf(RedConEstado(red, ContadoresSinRevisar(0, 0, 0), 0)),
                        redActiva = red,
                        tema = Tema.OSCURO,
                    ),
                    acciones = AccionesAjustes(alCambiarTema = { elegido = it }),
                    zona = ZoneId.of("UTC"),
                    ahora = Fixtures.AHORA,
                )
            }
        }

        compose.onNodeWithText("Apariencia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Oscuro").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tema").performScrollTo().performClick()
        compose.onNodeWithText("Cambia solo cuando el teléfono pasa a oscuro.").assertIsDisplayed()
        compose.onNodeWithText("Como el teléfono").performClick()
        compose.waitForIdle()

        assertEquals(Tema.COMO_EL_TELEFONO, elegido)
    }

    @Test
    fun `la hoja del tema marca el elegido`() {
        var elegido: Tema? = null
        compose.setContent { ZondaTheme { Column { ContenidoHojaTema(Tema.CLARO) { elegido = it } } } }

        compose.onNodeWithText("Claro").assertIsDisplayed()
        compose.onNodeWithText("Oscuro").performClick()
        assertEquals(Tema.OSCURO, elegido)
    }
}
