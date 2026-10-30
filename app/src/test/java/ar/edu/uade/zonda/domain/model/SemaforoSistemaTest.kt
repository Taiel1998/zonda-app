package ar.edu.uade.zonda.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** El semáforo de todo el sistema (decisión 48): el color de la pieza que está peor, nunca verde sin noticias. */
class SemaforoSistemaTest {

    private val ahora = Instant.parse("2026-10-30T21:00:00Z")

    private fun sistema(sensor: EstadoPieza, analisis: EstadoPieza, servidor: EstadoPieza) = EstadoSistema(
        sensor = sensor,
        analisis = analisis,
        servidor = servidor,
        ultimaCorrida = ahora,
        ultimoFlujo = ahora,
        consultadoEn = ahora,
    )

    @Test
    fun `cada pieza tiene su color`() {
        assertEquals(Semaforo.VERDE, EstadoPieza.FUNCIONA.semaforo)
        assertEquals(Semaforo.AMARILLO, EstadoPieza.PARCIAL.semaforo)
        assertEquals(Semaforo.ROJO, EstadoPieza.DETENIDO.semaforo)
        assertEquals(Semaforo.ROJO, EstadoPieza.SIN_DATOS.semaforo)
        assertEquals(Semaforo.ROJO, EstadoPieza.NO_RESPONDE.semaforo)
    }

    @Test
    fun `verde solo si las tres funcionan`() {
        assertEquals(Semaforo.VERDE, sistema(EstadoPieza.FUNCIONA, EstadoPieza.FUNCIONA, EstadoPieza.FUNCIONA).semaforo)
    }

    @Test
    fun `el sensor que ve parcialmente pone el sistema en amarillo`() {
        assertEquals(Semaforo.AMARILLO, sistema(EstadoPieza.PARCIAL, EstadoPieza.FUNCIONA, EstadoPieza.FUNCIONA).semaforo)
    }

    @Test
    fun `una pieza en rojo manda aunque otra este en amarillo`() {
        assertEquals(Semaforo.ROJO, sistema(EstadoPieza.PARCIAL, EstadoPieza.DETENIDO, EstadoPieza.FUNCIONA).semaforo)
        assertEquals(Semaforo.ROJO, sistema(EstadoPieza.FUNCIONA, EstadoPieza.FUNCIONA, EstadoPieza.NO_RESPONDE).semaforo)
    }

    @Test
    fun `sin respuesta del servidor el sistema queda en rojo`() {
        assertEquals(Semaforo.ROJO, EstadoSistema.sinRespuesta(ahora).semaforo)
    }
}
