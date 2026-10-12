package ar.edu.uade.zonda.ui.conexion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.ui.primerinicio.BienvenidaScreen
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La bienvenida y la pantalla Conexión dibujadas con Compose sobre Robolectric, sin emulador. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrimerInicioYConexionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `para empezar hay que aceptar las condiciones`() {
        var empezo = false
        compose.setContent {
            ZondaTheme { BienvenidaScreen(alVerCondiciones = {}, alEmpezar = { empezo = true }, alOmitirIntroduccion = {}) }
        }

        compose.onNodeWithText("Empezar").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Empezar").assertIsEnabled().performClick()

        assertTrue(empezo)
    }

    @Test
    fun `conectar se habilita con la direccion y un codigo completo`() {
        var conecto = false
        var estado by mutableStateOf<ConexionUiState>(ConexionUiState.Formulario())
        compose.setContent {
            ZondaTheme { ConexionContenido(estado = estado, acciones = AccionesConexion(alConectar = { conecto = true })) }
        }

        compose.onNodeWithText("Conectar").assertIsNotEnabled()
        estado = ConexionUiState.Formulario(direccion = "zonda-empresa.duckdns.org", codigo = "k7m2qx9p")
        compose.onNodeWithText("Conectar").performScrollTo().assertIsEnabled().performClick()

        assertTrue(conecto)
    }

    @Test
    fun `mientras conecta muestra la red y cada paso`() {
        compose.setContent {
            ZondaTheme {
                ConexionContenido(
                    estado = ConexionUiState.Conectando("https://zonda-empresa.duckdns.org", PasoPareo.REGISTRANDO_TELEFONO),
                    acciones = AccionesConexion(),
                )
            }
        }

        compose.onNodeWithText("zonda-empresa.duckdns.org").assertIsDisplayed()
        compose.onNodeWithText("Verificando que sea el verdadero").assertIsDisplayed()
        compose.onNodeWithText("Registrando este teléfono").assertIsDisplayed()
        compose.onNodeWithText("Descargando las alertas").assertIsDisplayed()
    }

    @Test
    fun `con el codigo vencido ofrece escanear otro`() {
        var escanear = false
        compose.setContent {
            ZondaTheme {
                ConexionContenido(
                    estado = ConexionUiState.Fallo("https://zonda-empresa.duckdns.org", ErrorPareo.CODIGO_VENCIDO),
                    acciones = AccionesConexion(alEscanear = { escanear = true }),
                )
            }
        }

        compose.onNodeWithText("El código venció o ya se usó").assertIsDisplayed()
        compose.onNodeWithText("Escanear otro código").performClick()

        assertTrue(escanear)
    }
}
