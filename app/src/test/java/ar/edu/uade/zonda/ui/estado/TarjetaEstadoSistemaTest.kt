package ar.edu.uade.zonda.ui.estado

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
    fun `con todo funcionando cada pieza dice Funciona y debajo su dato`() {
        dibujar(sistema())

        compose.onNodeWithText("Estado del sistema").assertIsDisplayed()
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
    fun `cada pieza tiene su signo de pregunta que explica que es`() {
        dibujar(sistema())
        val ayudaAnalisis = "Cada 20 segundos revisa lo que vio el sensor con los tres métodos y manda las alertas. " +
            "Si pasan 2 minutos sin señales, dice Detenido y avisa por Telegram."

        listOf("Sensor de red", "Análisis en vivo", "Servidor de alertas")
            .forEach { pieza -> compose.onNodeWithContentDescription("Qué es $pieza").assertHasClickAction() }
        compose.onAllNodesWithText(ayudaAnalisis).assertCountEquals(0)

        compose.onNodeWithContentDescription("Qué es Análisis en vivo").performClick()
        compose.onNodeWithText(ayudaAnalisis).assertIsDisplayed()
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

        compose.onNodeWithText("Sin tráfico para saber si ve todo (prueba simulada) · último tráfico hace 4 min").assertIsDisplayed()
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
        compose.onNodeWithText("Estado del sistema").assertIsDisplayed()
        compose.onNodeWithContentDescription("Análisis en vivo: Funciona").assertIsDisplayed()

        conTarjeta = false
        compose.waitForIdle()
        compose.onAllNodesWithText("Estado del sistema").assertCountEquals(0)
    }
}
