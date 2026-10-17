package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las fechas y las horas del Resumen (cambio 19): qué abarca un rango y cómo se agrupa el gráfico. */
class RangoResumenTest {

    private val utc = ZoneOffset.UTC
    private fun dia(d: Int, mes: Int = 9): LocalDate = LocalDate.of(2026, mes, d)
    private fun momento(d: Int, hora: Int, minuto: Int = 0, mes: Int = 9) = LocalDateTime.of(2026, mes, d, hora, minuto)

    @Test
    fun `Hasta incluye ese minuto entero y el minuto siguiente ya no cuenta`() {
        val viernes = RangoResumen.dias(dia(25), dia(25))

        assertTrue(momento(25, 0) in viernes)
        assertTrue(LocalDateTime.of(2026, 9, 25, 23, 59, 59) in viernes)
        assertFalse(momento(26, 0) in viernes)
        assertFalse(LocalDateTime.of(2026, 9, 24, 23, 59, 59) in viernes)
        assertTrue(viernes.diasEnteros)
    }

    @Test
    fun `un dia o menos va por hora, con una barra por cada hora que toca`() {
        // Figma 463-1836: viernes de 00:00 a 05:59, seis barras de 0 a 5.
        val madrugada = RangoResumen(momento(25, 0), momento(25, 5, 59))
        assertEquals(EscalaBarra.HORA, madrugada.escala)
        assertEquals((0..5).toList(), madrugada.barras().map { it.desde.hour })

        // Del sábado 22:00 al domingo 06:00: nueve barras, de las 22 a las 6.
        val noche = RangoResumen(momento(26, 22), momento(27, 6))
        assertEquals(EscalaBarra.HORA, noche.escala)
        assertEquals(listOf(22, 23, 0, 1, 2, 3, 4, 5, 6), noche.barras().map { it.desde.hour })

        // Un día entero son 24 barras.
        assertEquals(24, RangoResumen.dias(dia(25), dia(25)).barras().size)
        assertFalse(madrugada.diasEnteros)
    }

    @Test
    fun `hasta 14 dias va por dia y con mas por semana, la primera con lo que sobra`() {
        val dos = RangoResumen.dias(dia(24), dia(25))
        assertEquals(EscalaBarra.DIA, dos.escala)
        assertEquals(listOf(dia(24), dia(25)), dos.barras().map { it.primerDia })

        assertEquals(EscalaBarra.DIA, RangoResumen.dias(dia(1), dia(14)).escala)

        val quince = RangoResumen.dias(dia(1), dia(15))
        assertEquals(EscalaBarra.SEMANA, quince.escala)
        assertEquals(listOf(Franja.semana(dia(1), dia(8)), Franja.semana(dia(9), dia(15))), quince.barras())
    }

    @Test
    fun `los atajos son rangos de dias enteros que terminan hoy y dan las mismas barras de siempre`() {
        val hoy = dia(27)

        assertEquals(RangoResumen.dias(dia(21), dia(27)), PeriodoResumen.SIETE_DIAS.rango(hoy))
        assertEquals((21..27).map { Franja.dia(dia(it)) }, PeriodoResumen.SIETE_DIAS.barras(hoy))
        // 30 días: 28/8, 7/9, 14/9 y 21/9 (Figma 100-7024).
        assertEquals(
            listOf(dia(28, 8), dia(7), dia(14), dia(21)),
            PeriodoResumen.TREINTA_DIAS.barras(hoy).map { it.primerDia },
        )
        assertEquals(Tramo(dia(28, 8), dia(27)), PeriodoResumen.guardados(hoy))
    }

    @Test
    fun `Hasta antes que Desde en el mismo dia no vale`() {
        assertFalse(RangoResumen(momento(25, 22), momento(25, 6)).valido)
        assertTrue(RangoResumen(momento(25, 22), momento(26, 6)).valido)
        assertTrue(RangoResumen(momento(25, 6), momento(25, 6)).valido)
    }

    @Test
    fun `el resumen de unas horas cuenta por hora y solo lo que empezo dentro`() {
        fun alertaA(hora: Int, minuto: Int = 10, id: String) = LocalDateTime.of(2026, 9, 25, hora, minuto).toInstant(utc).let { inicio ->
            Fixtures.alerta(id, inicio = inicio, creadaEn = inicio.plus(Duration.ofMinutes(5)))
        }
        val lista = listOf(alertaA(2, id = "a"), alertaA(2, 40, id = "b"), alertaA(5, 59, id = "c"), alertaA(6, 0, id = "afuera"))
        val ahora = LocalDateTime.of(2026, 9, 27, 5, 14).toInstant(utc)

        val resumen = ResumenActividad.calcular(lista, RangoResumen(momento(25, 0), momento(25, 5, 59)), ahora, utc)

        assertEquals(null, resumen.periodo)
        assertEquals(3, resumen.total)
        assertEquals(listOf(0, 0, 2, 0, 0, 1), resumen.barras.map { it.cantidad })
        assertEquals(EscalaBarra.HORA, resumen.escala)
        assertEquals(2, resumen.destacado?.barra?.franja?.desde?.hour)
        // Con otras fechas quedarían las cuatro, y los días elegidos no cuentan: eran de las fechas de antes.
        val conHora = ResumenActividad.calcular(
            lista, RangoResumen(momento(25, 0), momento(25, 5, 59)), ahora, utc,
            FiltroResumen(barras = setOf(Franja.hora(momento(25, 2)))),
        )
        assertEquals(2, conHora.total)
        assertEquals(4, conHora.cuantasEn(RangoResumen.dias(dia(25), dia(25)), utc))
    }
}
