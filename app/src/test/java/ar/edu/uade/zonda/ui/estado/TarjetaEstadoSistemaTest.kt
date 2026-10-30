package ar.edu.uade.zonda.ui.estado

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
    fun `con todo funcionando el semaforo dice Todo funciona y cada pieza Funciona con su dato`() {
        dibujar(sistema())

        // La franja se lee entera, como cada fila: los dos textos quedan adentro de la descripción.
        compose.onNodeWithContentDescription("Estado del sistema: Todo funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sensor de red: Funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Servidor de alertas: Funciona").assertIsDisplayed()
        compose.onNodeWithText("Último tráfico hace 4 min").assertIsDisplayed()
        compose.onNodeWithText("Revisó el tráfico hace 12 s").assertIsDisplayed()
        compose.onNodeWithText("Respondió recién").assertIsDisplayed()
    }

    @Test
    fun `el servidor dice hace cuanto respondio`() {
        dibujar(sistema().copy(consultadoEn = ahora.minusSeconds(7)))

        compose.onNodeWithText("Respondió hace 7 s").assertIsDisplayed()
    }

    @Test
    fun `la tarjeta tiene un solo signo de pregunta que explica las piezas y los colores`() {
        dibujar(sistema())
        val ayuda = "Sensor de red: Zeek, que recibe una copia del tráfico.\n" +
            "Análisis en vivo: lo revisa cada 20 s con los tres métodos.\n" +
            "Servidor de alertas: guarda las alertas y avisa por Telegram.\n" +
            "Verde: todo anda. Amarillo: el sensor pierde tráfico. Rojo: algo se detuvo o no da señales."

        compose.onAllNodes(hasContentDescription("Qué es", substring = true)).assertCountEquals(1)
        compose.onAllNodesWithText(ayuda).assertCountEquals(0)

        compose.onNodeWithContentDescription("Qué es Estado del sistema").assertHasClickAction().performClick()
        compose.onNodeWithText(ayuda).assertIsDisplayed()
    }

    @Test
    fun `con el sensor viendo parcialmente el semaforo dice Funciona en parte`() {
        dibujar(sistema(sensor = EstadoPieza.PARCIAL))

        compose.onNodeWithContentDescription("Estado del sistema: Funciona en parte").assertIsDisplayed()
    }

    @Test
    fun `con una pieza detenida o sin noticias el semaforo dice Algo no funciona`() {
        dibujar(sistema(sensor = EstadoPieza.PARCIAL, analisis = EstadoPieza.DETENIDO))

        compose.onNodeWithContentDescription("Estado del sistema: Algo no funciona").assertIsDisplayed()
    }

    @Test
    fun `con el analisis detenido lo dice con la palabra y cuenta desde la ultima senal`() {
        dibujar(sistema(sensor = EstadoPieza.SIN_DATOS, analisis = EstadoPieza.DETENIDO, segundos = 150, ultimoFlujoHace = null))

        compose.onNodeWithContentDescription("Análisis en vivo: Detenido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sensor de red: Sin datos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Servidor de alertas: Funciona").assertIsDisplayed()
        compose.onNodeWithText("Sin señales desde hace 2 min 30 s").assertIsDisplayed()
        compose.onNodeWithText("Lo informa el análisis en vivo, que no da señales").assertIsDisplayed()
    }

    @Test
    fun `si el servidor no responde lo dice`() {
        dibujar(EstadoSistema.sinRespuesta(ahora))

        compose.onNodeWithContentDescription("Servidor de alertas: No responde").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Sin datos").assertIsDisplayed()
        compose.onNodeWithText("No contesta; se vuelve a probar cada 20 s").assertIsDisplayed()
        compose.onNodeWithText("Todavía no dio señales").assertIsDisplayed()
    }

    @Test
    fun `el sensor que ve todo el trafico lo dice con el ultimo trafico`() {
        dibujar(sistema().copy(perdidaCaptura = 0.0, perdidaMedidaEn = ahora.minusSeconds(120)))

        compose.onNodeWithText("Ve todo el tráfico · último tráfico hace 4 min").assertIsDisplayed()
    }

    @Test
    fun `el sensor que no llega a ver todo va en Viendo parcialmente con el porcentaje`() {
        dibujar(sistema(sensor = EstadoPieza.PARCIAL).copy(perdidaCaptura = 3.2, perdidaMedidaEn = ahora.minusSeconds(120)))

        compose.onNodeWithContentDescription("Sensor de red: Viendo parcialmente").assertIsDisplayed()
        compose.onNodeWithText("No llegó a ver el 3,2 % del tráfico · último tráfico hace 4 min").assertIsDisplayed()
    }

    @Test
    fun `sin trafico para medir lo dice y la prueba simulada se aclara`() {
        dibujar(sistema().copy(perdidaCaptura = 0.0, perdidaMedidaEn = ahora, sinTraficoParaMedir = true, perdidaSimulada = true))

        compose.onNodeWithText("Sin tráfico para medir pérdidas (prueba simulada) · último tráfico hace 4 min").assertIsDisplayed()
    }

    @Test
    fun `el sensor detenido con el analisis andando dice que Zeek no corre`() {
        dibujar(sistema(sensor = EstadoPieza.DETENIDO))

        compose.onNodeWithContentDescription("Sensor de red: Detenido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()
        compose.onNodeWithText("El programa que escucha la red (Zeek) no está corriendo").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Estado del sistema: Todo funciona").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()

        conTarjeta = false
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Estado del sistema: Todo funciona").assertCountEquals(0)
    }
}
