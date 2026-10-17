package ar.edu.uade.zonda.ui.resumen

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.RangoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Elegí las fechas" (cambio 19; Figma 463-2027 y 463-2466): la grilla, Desde y Hasta y los textos de las fechas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h917dp")
class FechasResumenTest {

    @get:Rule
    val compose = createComposeRule()

    private val recursos: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val utc = ZoneOffset.UTC
    private fun dia(d: Int, mes: Int = 9): LocalDate = LocalDate.of(2026, mes, d)
    private fun momento(d: Int, hora: Int, minuto: Int = 0, mes: Int = 9) = LocalDateTime.of(2026, mes, d, hora, minuto)

    // ---- La grilla: primero el primer día y después el último ----

    @Test
    fun `el primer toque elige un dia solo y el segundo el ultimo, con las horas como estaban`() {
        val inicial = BorradorFechas(momento(21, 22), momento(27, 6))

        val unDia = inicial.tocarDia(dia(24))
        assertEquals(RangoResumen(momento(24, 22), momento(24, 6)), unDia.rango)
        assertTrue(unDia.esperandoUltimo)

        val rango = unDia.tocarDia(dia(26))
        assertEquals(RangoResumen(momento(24, 22), momento(26, 6)), rango.rango)
        assertFalse(rango.esperandoUltimo)
    }

    @Test
    fun `si el segundo toque es antes del primero, ese pasa a ser el primer dia`() {
        val borrador = BorradorFechas.de(RangoResumen.dias(dia(21), dia(27))).tocarDia(dia(26)).tocarDia(dia(24))

        assertEquals(RangoResumen.dias(dia(24), dia(26)), borrador.rango)
    }

    @Test
    fun `la hora de Desde y de Hasta cambian sin tocar los dias`() {
        val borrador = BorradorFechas.de(RangoResumen.dias(dia(25), dia(25)))
            .conHoraDesde(LocalTime.of(22, 0))
            .conHoraHasta(LocalTime.of(6, 0))

        assertFalse(borrador.rango.valido)
        assertEquals(momento(25, 22), borrador.desde)
    }

    // ---- Los textos ----

    @Test
    fun `la linea dice el rango exacto, el chip los dias y la frase la hora con mas alertas`() {
        assertEquals("del 21/09 al 27/09", RangoResumen.dias(dia(21), dia(27)).enLinea(recursos))
        assertEquals("viernes 25/09", RangoResumen.dias(dia(25), dia(25)).enLinea(recursos))
        // Figma 463-1836.
        assertEquals("viernes 25/09 · de 00:00 a 05:59", RangoResumen(momento(25, 0), momento(25, 5, 59)).enLinea(recursos))
        assertEquals("del sáb 10/10 22:00 al dom 11/10 06:00", RangoResumen(momento(10, 22, mes = 10), momento(11, 6, mes = 10)).enLinea(recursos))

        assertEquals("vie 25/09", RangoResumen(momento(25, 0), momento(25, 5, 59)).enChip(recursos))
        assertEquals("10/10 al 11/10", RangoResumen.dias(dia(10, 10), dia(11, 10)).enChip(recursos))

        val inicio = momento(25, 2, 10).toInstant(utc)
        val lista = (1..5).map { Fixtures.alerta("a-$it", tipoAtaque = if (it == 5) TipoAtaque.ESCANEO else TipoAtaque.FUERZA_BRUTA, inicio = inicio, creadaEn = inicio.plus(Duration.ofMinutes(5))) }
        val resumen = ResumenActividad.calcular(lista, RangoResumen(momento(25, 0), momento(25, 5, 59)), momento(27, 5).toInstant(utc), utc)
        assertEquals("A las 2 h hubo más alertas: 5, con 4 de fuerza bruta.", fraseDelGrafico(resumen, recursos))
        assertEquals("Oficina central · viernes 25/09 · de 00:00 a 05:59 · actualizado 05:14", lineaDelResumen("Oficina central", resumen, momento(27, 5, 14).toInstant(utc), false, recursos, utc))
    }

    // ---- La hoja ----

    private fun dibujar(rango: RangoResumen, elegidos: MutableList<RangoResumen> = mutableListOf(), cancelado: () -> Unit = {}) {
        compose.setContent {
            ZondaTheme {
                // Como en la hoja inferior, que se desplaza.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ContenidoHojaFechas(
                        rango = rango,
                        hoy = dia(27),
                        cuantas = { 14 },
                        alMostrar = { elegidos += it },
                        alCancelar = cancelado,
                    )
                }
            }
        }
    }

    @Test
    fun `la hoja muestra Desde y Hasta, los dos meses y cuantas alertas quedan`() {
        dibujar(PeriodoResumen.SIETE_DIAS.rango(dia(27)))

        compose.onNodeWithText("Elegí las fechas").assertIsDisplayed()
        compose.onNodeWithText("Desde").assertIsDisplayed()
        compose.onNodeWithText("lun 21/09").assertIsDisplayed()
        compose.onNodeWithText("dom 27/09").assertIsDisplayed()
        compose.onNodeWithText("23:59").assertIsDisplayed()
        compose.onNodeWithText("El teléfono guarda los últimos 30 días: del 28/08 al 27/09.").assertIsDisplayed()
        compose.onNodeWithText("Agosto 2026").assertExists()
        compose.onNodeWithText("Septiembre 2026").assertExists()
        compose.onNodeWithText("Mostrar 14 alertas").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tocar un dia y Mostrar elige ese dia solo, y los dias sin datos no se tocan`() {
        val elegidos = mutableListOf<RangoResumen>()
        dibujar(PeriodoResumen.SIETE_DIAS.rango(dia(27)), elegidos)

        // El 27/08 no está guardado: tocarlo no cambia nada.
        compose.onNodeWithContentDescription("jueves 27/08").performScrollTo().performClick()
        compose.onNodeWithContentDescription("viernes 25/09").performScrollTo().performClick()
        compose.onNodeWithText("Mostrar 14 alertas").performScrollTo().performClick()

        assertEquals(listOf(RangoResumen.dias(dia(25), dia(25))), elegidos)
    }

    @Test
    fun `con Hasta antes que Desde avisa y no deja mostrar`() {
        dibujar(RangoResumen(momento(25, 22), momento(25, 6)))

        compose.onNodeWithText("La hora de \"Hasta\" tiene que ser después de la de \"Desde\".").assertIsDisplayed()
        compose.onNodeWithText("Mostrar 0 alertas").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `tocar la hora abre el reloj de Material 3 y Cancelar de la hoja la cierra`() {
        var cancelado = false
        dibujar(PeriodoResumen.SIETE_DIAS.rango(dia(27)), cancelado = { cancelado = true })

        compose.onNodeWithText("00:00").performClick()
        compose.onNodeWithText("Hora de inicio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Escribir la hora").assertExists()
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithText("Hora de inicio").assertDoesNotExist()

        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(cancelado)
    }
}
