package ar.edu.uade.zonda.ui.estado

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun `con todo funcionando cada pieza tiene su semaforo en verde y debajo su dato`() {
        dibujar(sistema())

        compose.onNodeWithText("Estado del sistema").assertIsDisplayed()
        // El estado lo dice el semáforo, sin palabras en la pantalla (decisión 48); TalkBack lee la fila entera.
        compose.onAllNodesWithText("Funciona", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Sensor de red: Funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Servidor de alertas: Funciona").assertIsDisplayed()
        // Debajo, un dato corto y con la misma forma en las tres piezas.
        compose.onNodeWithText("Último tráfico hace 4 min").assertIsDisplayed()
        compose.onNodeWithText("Última revisión hace 12 s").assertIsDisplayed()
        compose.onNodeWithText("Respondió recién").assertIsDisplayed()
    }

    @Test
    fun `el servidor dice hace cuanto respondio`() {
        dibujar(sistema().copy(consultadoEn = ahora.minusSeconds(7)))

        compose.onNodeWithText("Última respuesta hace 7 s").assertIsDisplayed()
    }

    @Test
    fun `un solo signo de pregunta, junto al titulo, explica las piezas y los colores`() {
        dibujar(sistema())
        val ayuda = "Sensor de red: recibe una copia del tráfico de la red.\n" +
            "Análisis en vivo: revisa ese tráfico cada 20 s.\n" +
            "Servidor de alertas: guarda las alertas y avisa por Telegram.\n" +
            "Verde: funciona. Ámbar: el sensor pierde tráfico. Rojo: no funciona o no hay datos."

        compose.onAllNodes(hasContentDescription("Qué es", substring = true)).assertCountEquals(1)
        compose.onAllNodesWithText(ayuda).assertCountEquals(0)

        compose.onNodeWithContentDescription("Qué es Estado del sistema").assertHasClickAction().performClick()
        compose.onNodeWithText(ayuda).assertIsDisplayed()
    }

    @Test
    fun `con el analisis detenido la luz va en rojo y el dato dice cuando reviso por ultima vez`() {
        dibujar(sistema(sensor = EstadoPieza.SIN_DATOS, analisis = EstadoPieza.DETENIDO, segundos = 1_440, ultimoFlujoHace = null))

        compose.onNodeWithContentDescription("Análisis en vivo: Detenido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sensor de red: Sin datos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Servidor de alertas: Funciona").assertIsDisplayed()
        // Una sola unidad: "24 min", no "24 min 0 s".
        compose.onNodeWithText("Última revisión hace 24 min").assertIsDisplayed()
        compose.onNodeWithText("Sin datos").assertIsDisplayed()
    }

    @Test
    fun `si el servidor no responde lo dice`() {
        dibujar(EstadoSistema.sinRespuesta(ahora))

        compose.onNodeWithContentDescription("Servidor de alertas: No responde").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Sin datos").assertIsDisplayed()
        compose.onNodeWithText("No responde").assertIsDisplayed()
        compose.onAllNodesWithText("Sin datos").assertCountEquals(2)
    }

    @Test
    fun `el sensor que ve todo dice cuando vio el ultimo trafico`() {
        dibujar(sistema().copy(perdidaCaptura = 0.0, perdidaMedidaEn = ahora.minusSeconds(120)))

        compose.onNodeWithText("Último tráfico hace 4 min").assertIsDisplayed()
    }

    @Test
    fun `el sensor que no llega a ver todo dice cuanto pierde`() {
        dibujar(sistema(sensor = EstadoPieza.PARCIAL).copy(perdidaCaptura = 3.2, perdidaMedidaEn = ahora.minusSeconds(120)))

        compose.onNodeWithContentDescription("Sensor de red: Viendo parcialmente").assertIsDisplayed()
        compose.onNodeWithText("Pierde el 3,2 % del tráfico").assertIsDisplayed()
    }

    @Test
    fun `la perdida de prueba del laboratorio se aclara`() {
        dibujar(sistema(sensor = EstadoPieza.PARCIAL).copy(perdidaCaptura = 5.0, perdidaMedidaEn = ahora, perdidaSimulada = true))

        compose.onNodeWithText("Pierde el 5,0 % del tráfico (simulado)").assertIsDisplayed()
    }

    @Test
    fun `el sensor detenido con el analisis andando dice que no escucha la red`() {
        dibujar(sistema(sensor = EstadoPieza.DETENIDO))

        compose.onNodeWithContentDescription("Sensor de red: Detenido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()
        compose.onNodeWithText("No escucha la red").assertIsDisplayed()
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
