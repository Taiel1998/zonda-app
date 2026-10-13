package ar.edu.uade.zonda.ui.recorrido

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La capa del recorrido (Figma 107-3321 a 107-4731): la tarjeta al lado de lo que resalta. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapaRecorridoTest {

    @get:Rule
    val compose = createComposeRule()

    /** Una pantalla con los chips arriba y la pestaña Alertas abajo, marcados como en la app. */
    private fun dibujar(paso: PasoRecorrido, registro: RegistroRecorrido = RegistroRecorrido(), alSiguiente: () -> Unit = {}, alOmitir: () -> Unit = {}) {
        compose.setContent {
            ZondaTheme {
                CompositionLocalProvider(LocalRegistroRecorrido provides registro) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "chips",
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(30.dp)
                                .objetivoDelRecorrido(ObjetivoRecorrido.CHIPS),
                        )
                        Text(
                            text = "pestaña",
                            modifier = Modifier
                                .offset(y = 560.dp)
                                .fillMaxWidth()
                                .height(56.dp)
                                .objetivoDelRecorrido(ObjetivoRecorrido.PESTANIA_ALERTAS),
                        )
                        CapaRecorrido(paso = paso, registro = registro, alSiguiente = alSiguiente, alOmitir = alOmitir)
                    }
                }
            }
        }
    }

    @Test
    fun `con lugar abajo la tarjeta va debajo de lo que resalta, con su paso y sus botones`() {
        var siguiente = 0
        var omitido = false
        dibujar(PasoRecorrido.FILTROS, alSiguiente = { siguiente++ }, alOmitir = { omitido = true })

        compose.onNodeWithText("PASO 6 DE 11").assertIsDisplayed()
        compose.onNodeWithText("Filtros").assertIsDisplayed()
        val chips = compose.onNodeWithText("chips").getBoundsInRoot()
        val paso = compose.onNodeWithText("PASO 6 DE 11").getBoundsInRoot()
        assertTrue("la tarjeta va debajo de los chips", paso.top > chips.bottom)

        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("Omitir").performClick()
        assertEquals(1, siguiente)
        assertTrue(omitido)
    }

    @Test
    fun `sin lugar abajo la tarjeta va arriba de lo que resalta`() {
        dibujar(PasoRecorrido.ALERTAS)

        val pestania = compose.onNodeWithText("pestaña").getBoundsInRoot()
        val boton = compose.onNodeWithText("Siguiente").getBoundsInRoot()
        assertTrue("la tarjeta termina arriba de la pestaña", boton.bottom < pestania.top)
    }

    @Test
    fun `sin el elemento en la pantalla queda la tarjeta sola, y el ultimo paso dice Listo`() {
        val registro = RegistroRecorrido()
        dibujar(PasoRecorrido.AJUSTES, registro)

        assertNull(registro.posicionDe(ObjetivoRecorrido.TARJETA_AVISOS))
        compose.onNodeWithText("PASO 11 DE 11").assertIsDisplayed()
        compose.onNodeWithText("Listo").assertIsDisplayed()
        assertEquals(ObjetivoRecorrido.TARJETA_AVISOS, registro.actual)
    }

    @Test
    fun `el primer paso explica los tres colores del semaforo`() {
        dibujar(PasoRecorrido.SEMAFORO)

        compose.onNodeWithText("El estado de la red, de un vistazo").assertIsDisplayed()
        compose.onNodeWithText("Rojo: hay alertas altas sin revisar").assertIsDisplayed()
        compose.onNodeWithText("Verde: ninguna alta ni media; las bajas esperan en la lista").assertIsDisplayed()
    }
}
