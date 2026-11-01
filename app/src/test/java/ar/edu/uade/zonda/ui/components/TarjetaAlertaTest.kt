package ar.edu.uade.zonda.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.ContextoTarjetas
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El primer renglón de la tarjeta de una alerta (Figma 35-363), en un teléfono de 412 de ancho: la hora
 * siempre entera a la derecha, "pendiente" pegado a ella y un aviso que no entra, en el renglón de abajo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h917dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TarjetaAlertaTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    private fun dibujar(alerta: Alerta) {
        compose.setContent {
            ZondaTheme { TarjetaAlerta(alerta = alerta, alTocar = {}, zona = utc) }
        }
    }

    private fun limites(texto: String): Rect = compose.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun renglones(texto: String): Int {
        val resultados = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.first().lineCount
    }

    @Test
    fun `revisada por otra persona baja de renglon y la hora queda entera`() {
        val alerta = Fixtures.alerta(
            severidad = Severidad.MEDIA,
            revision = Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, Motivo.BACKUP),
            envio = EstadoEnvio.CONFLICTO,
        )
        dibujar(alerta)
        val hora = Formato.horaYDia(alerta.inicio, utc)

        assertEquals(1, renglones(hora))
        val aviso = limites("revisada por otra persona")
        val etiqueta = limites("FALSO POSITIVO")
        assertTrue("el aviso va debajo de las etiquetas", aviso.top >= etiqueta.bottom)
        assertTrue("la hora queda en el primer renglón", limites(hora).bottom <= aviso.top)
    }

    @Test
    fun `pendiente va al lado de la hora, en el mismo renglon, como el Figma`() {
        val alerta = Fixtures.alerta(envio = EstadoEnvio.PENDIENTE)
        dibujar(alerta)
        val hora = limites(Formato.horaYDia(alerta.inicio, utc))
        val pendiente = limites("pendiente")

        assertTrue(abs(pendiente.center.y - hora.center.y) < 2f)
        assertTrue(pendiente.right <= hora.left)
        assertTrue(abs(limites("ALTA").center.y - hora.center.y) < 2f)
    }

    @Test
    fun `TalkBack lee la ruta, los metodos y el responsable como frases, y a la vista no cambia nada`() {
        val alerta = Fixtures.alerta(metodos = setOf(MetodoDeteccion.UMBRALES, MetodoDeteccion.IA))
            .copy(asignacion = Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA))
        compose.setContent {
            ZondaTheme { TarjetaAlerta(alerta = alerta, alTocar = {}, zona = utc, contexto = ContextoTarjetas(miId = "d-2")) }
        }

        compose.onNodeWithContentDescription("De pc-09 a srv-web", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("detectada por IA y umbrales", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Responsable: Vos", useUnmergedTree = true).assertExists()
        listOf("pc-09 → srv-web", "IA", "Umbrales", "Vos").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }
}
