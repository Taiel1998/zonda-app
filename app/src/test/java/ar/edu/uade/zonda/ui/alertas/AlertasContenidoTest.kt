package ar.edu.uade.zonda.ui.alertas

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.Severidad
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

/** La pantalla Alertas dibujada con Compose sobre Robolectric, sin emulador. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlertasContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    @Test
    fun `muestra las tarjetas con severidad, estado, equipos y hora, y abre el detalle al tocar`() {
        var abierta: String? = null
        val estado = AlertasUiState(
            contenido = ContenidoAlertas.Lista(
                alertas = listOf(
                    Fixtures.alerta("a-1", severidad = Severidad.ALTA, envio = EstadoEnvio.PENDIENTE),
                    Fixtures.alerta("a-2", severidad = Severidad.MEDIA, revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
                ),
                sinRevisar = 1,
            ),
            filtro = FiltroAlertas(periodo = Periodo.ULTIMAS_24_HORAS),
            datosDe = Fixtures.AHORA,
        )

        compose.setContent {
            ZondaTheme {
                AlertasContenido(estado = estado, alAbrirDetalle = { abierta = it }, alCambiarFiltro = {}, alSincronizar = {}, alQuitarFiltros = {}, zona = utc)
            }
        }

        compose.onAllNodesWithText("pc-09 → srv-web").assertCountEquals(2)
        compose.onNodeWithText("ALTA").assertIsDisplayed()
        compose.onNodeWithText("MEDIA").assertIsDisplayed()
        compose.onNodeWithText("SIN REVISAR").assertIsDisplayed()
        compose.onNodeWithText("REVISADA").assertIsDisplayed()
        compose.onNodeWithText("pendiente").assertIsDisplayed()
        compose.onAllNodesWithText("10:50 · lun 12/10").assertCountEquals(2)
        compose.onNodeWithText("2 alertas · 1 sin revisar · últimas 24 h · actualizado 12:00").assertIsDisplayed()
        compose.onNodeWithText("24 h").assertIsDisplayed()

        compose.onAllNodesWithText("pc-09 → srv-web").onFirst().performClick()

        assertEquals("a-1", abierta)
    }

    @Test
    fun `sin resultados con un filtro activo ofrece quitar los filtros`() {
        var quitados = false
        val estado = AlertasUiState(contenido = ContenidoAlertas.Vacio(conFiltro = true), filtro = FiltroAlertas(severidades = setOf(Severidad.BAJA)))

        compose.setContent {
            ZondaTheme {
                AlertasContenido(estado = estado, alAbrirDetalle = {}, alCambiarFiltro = {}, alSincronizar = {}, alQuitarFiltros = { quitados = true }, zona = utc)
            }
        }

        compose.onNodeWithText("Ninguna alerta coincide con los filtros").assertIsDisplayed()
        compose.onNodeWithText("Quitar filtros").performClick()
        assertTrue(quitados)
    }

    @Test
    fun `sin red pareada y sin conexion lo dice con el banner`() {
        val estado = AlertasUiState(contenido = ContenidoAlertas.SinRed, sinConexion = true)

        compose.setContent {
            ZondaTheme {
                AlertasContenido(estado = estado, alAbrirDetalle = {}, alCambiarFiltro = {}, alSincronizar = {}, alQuitarFiltros = {}, zona = utc)
            }
        }

        compose.onNodeWithText("Todavía no hay una red conectada").assertIsDisplayed()
        compose.onNodeWithText("Sin conexión · todavía no hay datos guardados").assertIsDisplayed()
    }
}
