package ar.edu.uade.zonda.ui.estado

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La tarjeta "Estado del sistema" dibujada con Compose sobre Robolectric, sola y dentro de Estado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h917dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TarjetaEstadoSistemaTest {

    @get:Rule
    val compose = createComposeRule()

    private val ahora = Fixtures.AHORA

    private fun sistema(
        sensor: EstadoPieza = EstadoPieza.FUNCIONA,
        analisis: EstadoPieza = EstadoPieza.FUNCIONA,
        servidor: EstadoPieza = EstadoPieza.FUNCIONA,
        segundos: Long? = 12,
        ultimoFlujoHace: Long? = 240,
    ) = EstadoSistema(
        sensor = sensor,
        analisis = analisis,
        servidor = servidor,
        ultimaCorrida = segundos?.let { ahora.minusSeconds(it) },
        ultimoFlujo = ultimoFlujoHace?.let { ahora.minusSeconds(it) },
        consultadoEn = ahora,
    )

    private fun dibujar(sistema: EstadoSistema) {
        compose.setContent { ZondaTheme { TarjetaEstadoSistema(sistema = sistema, ahora = { ahora }) } }
    }

    @Test
    fun `con todo funcionando cada pieza dice Funciona y se ve la ultima corrida`() {
        dibujar(sistema())

        compose.onNodeWithText("Estado del sistema").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sensor de red: Funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Servidor de alertas: Funciona").assertIsDisplayed()
        compose.onNodeWithText("Última corrida hace 12 s · último flujo hace 4 min").assertIsDisplayed()
    }

    @Test
    fun `con el analisis detenido lo dice con la palabra y cuenta desde la ultima corrida`() {
        dibujar(sistema(sensor = EstadoPieza.SIN_DATOS, analisis = EstadoPieza.DETENIDO, segundos = 150, ultimoFlujoHace = null))

        compose.onNodeWithContentDescription("Análisis en vivo: Detenido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sensor de red: Sin datos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Servidor de alertas: Funciona").assertIsDisplayed()
        compose.onNodeWithText("Última corrida hace 2 min 30 s").assertIsDisplayed()
    }

    @Test
    fun `si el servidor no responde lo dice`() {
        dibujar(EstadoSistema.sinRespuesta(ahora))

        compose.onNodeWithContentDescription("Servidor de alertas: No responde").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Sin datos").assertIsDisplayed()
        compose.onNodeWithText("Todavía no llegó ninguna corrida del análisis en vivo").assertIsDisplayed()
    }

    @Test
    fun `el sensor que pierde captura va en Viendo parcialmente con el porcentaje`() {
        dibujar(sistema(sensor = EstadoPieza.PARCIAL).copy(perdidaCaptura = 3.2, perdidaMedidaEn = ahora.minusSeconds(120)))

        compose.onNodeWithContentDescription("Sensor de red: Viendo parcialmente").assertIsDisplayed()
        compose.onNodeWithText("Pérdida de captura: 3,2 % · medida hace 2 min").assertIsDisplayed()
    }

    @Test
    fun `la perdida dice si no hubo trafico para medir y si es simulada`() {
        dibujar(sistema().copy(perdidaCaptura = 0.0, perdidaMedidaEn = ahora, sinTraficoParaMedir = true, perdidaSimulada = true))

        compose.onNodeWithText("Pérdida de captura: sin tráfico para medir (simulada)").assertIsDisplayed()
    }

    @Test
    fun `sin medicion de perdida no hay linea de perdida`() {
        dibujar(sistema())

        compose.onAllNodesWithText("Pérdida de captura", substring = true).assertCountEquals(0)
    }

    @Test
    fun `el sensor detenido con el analisis andando se ve`() {
        dibujar(sistema(sensor = EstadoPieza.DETENIDO))

        compose.onNodeWithContentDescription("Sensor de red: Detenido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()
    }

    @Test
    fun `en Estado la tarjeta va debajo de los contadores y sin respuesta no aparece`() {
        val datos = ContenidoEstado.Datos(
            semaforo = Semaforo.VERDE,
            sinRevisar = ContadoresSinRevisar(altas = 0, medias = 0, bajas = 0),
            ultimaDeteccion = null,
            alertas = emptyList(),
        )
        var conTarjeta by mutableStateOf(true)
        compose.setContent {
            ZondaTheme {
                EstadoContenido(
                    estado = EstadoUiState(contenido = datos, datosDe = ahora, sistema = if (conTarjeta) sistema() else null),
                    ahora = ahora,
                    zona = ZoneId.of("UTC"),
                )
            }
        }
        compose.onNodeWithText("Estado del sistema").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()

        conTarjeta = false
        compose.waitForIdle()
        compose.onAllNodesWithText("Estado del sistema").assertCountEquals(0)
    }
}
