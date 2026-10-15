package ar.edu.uade.zonda.ui

import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.Proporcion
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoTest {

    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")
    private val instante = Instant.parse("2026-09-27T05:09:12Z") // 02:09:12 en Buenos Aires, domingo

    @Test
    fun `hora, dia y fecha en la zona del telefono`() {
        assertEquals("02:09", Formato.hora(instante, buenosAires))
        assertEquals("02:09:12", Formato.horaConSegundos(instante, buenosAires))
        assertEquals("dom 27/09", Formato.dia(instante, buenosAires))
        assertEquals("02:09 · dom 27/09", Formato.horaYDia(instante, buenosAires))
        assertEquals("27/09 02:09", Formato.fechaYHora(instante, buenosAires))
        assertEquals("05:09", Formato.hora(instante, ZoneId.of("UTC")))
    }

    @Test
    fun `duraciones en segundos, minutos y horas`() {
        assertEquals("0 s", Formato.duracion(Duration.ZERO))
        assertEquals("42 s", Formato.duracion(Duration.ofSeconds(42)))
        assertEquals("18 min 10 s", Formato.duracion(Duration.ofSeconds(18 * 60 + 10)))
        assertEquals("5 min", Formato.duracion(Duration.ofMinutes(5)))
        assertEquals("1 h 5 min", Formato.duracion(Duration.ofMinutes(65)))
        assertEquals("2 h", Formato.duracion(Duration.ofHours(2)))
        assertEquals("0 s", Formato.duracion(Duration.ofSeconds(-3)))
    }

    @Test
    fun `numeros con coma decimal, equipos y servicios`() {
        assertEquals("0,0412", Formato.numero(0.0412))
        assertEquals("0,0031", Formato.numero(0.00306383))
        assertEquals("0,87", Formato.numero(0.87, decimales = 2))
        assertEquals("192.168.30.50 · pc-rrhh", Formato.ipYNombre(Equipo("192.168.30.50", "pc-rrhh")))
        assertEquals("192.168.30.50", Formato.ipYNombre(Equipo("192.168.30.50")))
        assertEquals("SSH · 22", Formato.servicioYPuerto("ssh", 22))
    }

    @Test
    fun `fechas, porcentajes y tiempos del Resumen`() {
        val viernes = LocalDate.of(2026, 9, 25)
        assertEquals("vie", Formato.diaDeLaSemanaCorto(viernes))
        assertEquals("mié", Formato.diaDeLaSemanaCorto(LocalDate.of(2026, 9, 23)))
        assertEquals("viernes", Formato.diaDeLaSemana(viernes))
        assertEquals("Viernes 25/09", Formato.diaDeLaSemanaYFecha(viernes))
        assertEquals("25/09", Formato.diaYMes(viernes))
        assertEquals("28/8", Formato.diaYMesCorto(LocalDate.of(2026, 8, 28)))
        assertEquals("25-09", Formato.diaYMesParaArchivo(viernes))

        assertEquals("71 %", Formato.porcentaje(Proporcion(37, 52)))
        assertEquals("83 %", Formato.porcentaje(Proporcion(24, 29)))
        assertNull(Formato.porcentaje(Proporcion(0, 0)))

        assertEquals("40 s", Formato.duracionCorta(Duration.ofSeconds(40)))
        assertEquals("14 min", Formato.duracionCorta(Duration.ofSeconds(14 * 60 + 20)))
        assertEquals("2 h", Formato.duracionCorta(Duration.ofHours(2)))
        assertEquals("1 h 20 min", Formato.duracionCorta(Duration.ofMinutes(80)))
        assertEquals("1 d 3 h", Formato.duracionCorta(Duration.ofHours(27)))
    }

    @Test
    fun `hace cuanto del semaforo, en minutos, horas o dias`() {
        val ahora = instante.plus(Duration.ofMinutes(5))
        assertEquals("5 min", Formato.haceCuanto(instante, ahora))
        assertEquals("menos de 1 min", Formato.haceCuanto(instante, instante.plusSeconds(40)))
        assertEquals("menos de 1 min", Formato.haceCuanto(ahora, instante))
        assertEquals("59 min", Formato.haceCuanto(instante, instante.plus(Duration.ofMinutes(59))))
        assertEquals("3 h", Formato.haceCuanto(instante, instante.plus(Duration.ofMinutes(3 * 60 + 40))))
        assertEquals("1 día", Formato.haceCuanto(instante, instante.plus(Duration.ofHours(30))))
        assertEquals("4 días", Formato.haceCuanto(instante, instante.plus(Duration.ofDays(4))))
    }

    @Test
    fun `la hora de los datos lleva el dia cuando no es de hoy`() {
        assertEquals("02:09", Formato.horaDeLosDatos(instante, instante.plus(Duration.ofHours(3)), buenosAires))
        assertEquals("ayer 02:09", Formato.horaDeLosDatos(instante, instante.plus(Duration.ofDays(1)), buenosAires))
        assertEquals("dom 27/09 02:09", Formato.horaDeLosDatos(instante, instante.plus(Duration.ofDays(3)), buenosAires))
    }
}
