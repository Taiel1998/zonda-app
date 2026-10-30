package ar.edu.uade.zonda.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** El color del semáforo de cada pieza de la tarjeta "Estado del sistema" (decisión 48): nunca verde sin noticias. */
class SemaforoSistemaTest {

    @Test
    fun `cada pieza tiene su color`() {
        assertEquals(Semaforo.VERDE, EstadoPieza.FUNCIONA.semaforo)
        assertEquals(Semaforo.AMARILLO, EstadoPieza.PARCIAL.semaforo)
        assertEquals(Semaforo.ROJO, EstadoPieza.DETENIDO.semaforo)
        assertEquals(Semaforo.ROJO, EstadoPieza.SIN_DATOS.semaforo)
        assertEquals(Semaforo.ROJO, EstadoPieza.NO_RESPONDE.semaforo)
    }
}
