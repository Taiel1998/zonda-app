package ar.edu.uade.zonda.ui.detalle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La revisión y sus salidas dibujadas con Compose sobre Robolectric, sin emulador. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetalleContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    @Test
    fun `una alerta sin revisar ofrece Revisar`() {
        var abierta = false

        compose.setContent {
            ZondaTheme {
                DetalleContenido(
                    estado = DetalleUiState.Contenido(Fixtures.alerta("a-1"), accion = null, sinConexion = false, datosDe = Fixtures.AHORA),
                    acciones = AccionesDetalle(alRevisar = { abierta = true }),
                    zona = utc,
                )
            }
        }

        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        assertTrue(abierta)
    }

    @Test
    fun `el paso 1 pregunta si fue un ataque real o un falso positivo`() {
        var elegido: ResultadoRevision? = null

        compose.setContent {
            ZondaTheme { ContenidoHojaRevision(FormularioRevision(), Fixtures.alerta(), AccionesDetalle(alElegirResultado = { elegido = it })) }
        }

        compose.onNodeWithText("¿Qué resultó ser esta alerta?").assertIsDisplayed()
        compose.onNodeWithText("Paso 1 de 2").assertIsDisplayed()
        compose.onNodeWithText("Falso positivo").performClick()
        assertEquals(ResultadoRevision.FALSO_POSITIVO, elegido)
    }

    @Test
    fun `el paso 2 propone el comentario del motivo y solo deja confirmar con motivo y comentario`() {
        var motivoElegido: Pair<Motivo, String>? = null
        var confirmada = false
        var formulario by mutableStateOf(FormularioRevision(paso = PasoRevision.MOTIVO, resultado = ResultadoRevision.FALSO_POSITIVO))

        compose.setContent {
            ZondaTheme {
                ContenidoHojaRevision(
                    formulario,
                    Fixtures.alerta(),
                    AccionesDetalle(alElegirMotivo = { motivo, comentario -> motivoElegido = motivo to comentario }, alConfirmarRevision = { confirmada = true }),
                )
            }
        }

        compose.onNodeWithText("Falso positivo: ¿por qué?").assertIsDisplayed()
        compose.onNodeWithText("Confirmar revisión").assertIsNotEnabled()
        compose.onNodeWithText("Tarea programada").performClick()
        assertEquals(Motivo.TAREA_PROGRAMADA to "Es la copia nocturna que corre todos los domingos.", motivoElegido)

        formulario = formulario.copy(motivo = Motivo.TAREA_PROGRAMADA, comentario = "Respaldo nocturno")
        compose.onNodeWithText("Confirmar revisión").assertIsEnabled().performClick()
        assertTrue(confirmada)
    }

    @Test
    fun `en conflicto muestra la revision de la otra persona y deja aplicar la propia`() {
        var aplicada = false
        val ajena = Fixtures.revision(resultado = ResultadoRevision.FALSO_POSITIVO, motivo = Motivo.TAREA_PROGRAMADA, origen = OrigenRevision.SERVIDOR)
            .copy(autor = "Teléfono de Lucía")
        val alerta = Fixtures.alerta("a-1", revision = Fixtures.revision(), envio = EstadoEnvio.CONFLICTO)
        val accion = Fixtures.accion(alertaId = "a-1").copy(estado = EstadoEnvio.CONFLICTO, revisionAjena = ajena)

        compose.setContent {
            ZondaTheme {
                DetalleContenido(
                    estado = DetalleUiState.Contenido(alerta, accion, sinConexion = false, datosDe = Fixtures.AHORA),
                    acciones = AccionesDetalle(alAplicarLaMia = { aplicada = true }),
                    zona = utc,
                )
            }
        }

        compose.onNodeWithText("Otra persona revisó esta alerta").assertExists()
        compose.onNodeWithText("Teléfono de Lucía", substring = true).assertExists()
        compose.onNodeWithText("Aplicar la mía igual").performScrollTo().performClick()
        assertTrue(aplicada)
    }

    @Test
    fun `una revision que no se pudo enviar ofrece Reintentar`() {
        var reintentada = false
        val alerta = Fixtures.alerta("a-1", revision = Fixtures.revision(), envio = EstadoEnvio.ERROR)
        val accion = Fixtures.accion(alertaId = "a-1").copy(estado = EstadoEnvio.ERROR, intentos = 5, ultimoError = "Error del servidor")

        compose.setContent {
            ZondaTheme {
                DetalleContenido(
                    estado = DetalleUiState.Contenido(alerta, accion, sinConexion = false, datosDe = Fixtures.AHORA),
                    acciones = AccionesDetalle(alReintentarEnvio = { reintentada = true }),
                    zona = utc,
                )
            }
        }

        compose.onNodeWithText("No se pudo enviar la revisión").assertExists()
        compose.onNodeWithText("Intentos: 5. Último error: Error del servidor.").assertExists()
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
        assertTrue(reintentada)
    }
}
