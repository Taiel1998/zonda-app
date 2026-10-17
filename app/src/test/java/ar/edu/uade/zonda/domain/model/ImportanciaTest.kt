package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La importancia de cada equipo sube o baja un nivel la severidad (corrección 4, decisión 41). */
class ImportanciaTest {

    @Test
    fun `la tabla de 3 por 3 es la misma que la del servidor`() {
        // La misma tabla que prueba el servidor (test_la_tabla_de_3_por_3): un escalón, con tope en alta y piso en baja.
        val tabla = listOf(
            Triple(Severidad.ALTA, Importancia.CRITICO, Severidad.ALTA),
            Triple(Severidad.ALTA, Importancia.NORMAL, Severidad.ALTA),
            Triple(Severidad.ALTA, Importancia.POCO_IMPORTANTE, Severidad.MEDIA),
            Triple(Severidad.MEDIA, Importancia.CRITICO, Severidad.ALTA),
            Triple(Severidad.MEDIA, Importancia.NORMAL, Severidad.MEDIA),
            Triple(Severidad.MEDIA, Importancia.POCO_IMPORTANTE, Severidad.BAJA),
            Triple(Severidad.BAJA, Importancia.CRITICO, Severidad.MEDIA),
            Triple(Severidad.BAJA, Importancia.NORMAL, Severidad.BAJA),
            Triple(Severidad.BAJA, Importancia.POCO_IMPORTANTE, Severidad.BAJA),
        )

        for ((delTipo, importancia, final) in tabla) {
            assertEquals("$delTipo con un equipo $importancia", final, delTipo.ajustada(importancia))
        }
    }

    @Test
    fun `el calculo usa la severidad del tipo y la importancia del equipo destino`() {
        val alerta = Fixtures.alerta(tipoAtaque = TipoAtaque.ESCANEO, destinoIp = "192.168.10.50")

        val calculo = CalculoSeveridad.de(alerta, SeveridadPorTipo.RECOMENDADA, mapOf("192.168.10.50" to Importancia.CRITICO))

        assertEquals(Severidad.MEDIA, calculo.delTipo)
        assertEquals(Importancia.CRITICO, calculo.importancia)
        assertEquals("srv-web", calculo.equipo)
        assertEquals(Severidad.ALTA, calculo.final)
        assertEquals(1, calculo.cambio)
        assertTrue(calculo.conImportancia)
    }

    @Test
    fun `un equipo sin marcar es normal y la alerta queda como antes`() {
        val alerta = Fixtures.alerta(tipoAtaque = TipoAtaque.ESCANEO, destinoIp = "192.168.10.77")

        val calculo = CalculoSeveridad.de(alerta, SeveridadPorTipo.RECOMENDADA, mapOf("192.168.10.50" to Importancia.CRITICO))

        assertEquals(Importancia.NORMAL, calculo.importancia)
        assertEquals(Severidad.MEDIA, calculo.final)
        assertEquals(0, calculo.cambio)
        assertFalse(calculo.conImportancia)
    }

    @Test
    fun `en los topes la importancia no cambia el color pero la tarjeta igual explica el calculo`() {
        val alerta = Fixtures.alerta(tipoAtaque = TipoAtaque.FUERZA_BRUTA)

        val calculo = CalculoSeveridad.de(alerta, SeveridadPorTipo.RECOMENDADA, mapOf("192.168.10.50" to Importancia.CRITICO))

        assertEquals(Severidad.ALTA, calculo.final)
        assertEquals(0, calculo.cambio)
        assertTrue(calculo.conImportancia)
    }

    @Test
    fun `una alerta de reglas y umbrales sin la IA toma la mas alta de sus dos tipos antes de ajustar`() {
        val alerta = Fixtures.alerta(
            tipoAtaque = TipoAtaque.SERVICIO_NO_AUTORIZADO,
            metodos = setOf(MetodoDeteccion.REGLAS, MetodoDeteccion.UMBRALES),
        )
        val severidades = SeveridadPorTipo.RECOMENDADA
            .con(TipoAtaque.SERVICIO_NO_AUTORIZADO, Severidad.BAJA)
            .con(TipoAtaque.VOLUMEN_INUSUAL, Severidad.MEDIA)

        val calculo = CalculoSeveridad.de(alerta, severidades, mapOf("192.168.10.50" to Importancia.POCO_IMPORTANTE))

        assertEquals(Severidad.MEDIA, calculo.delTipo)
        assertEquals(Severidad.BAJA, calculo.final)
        assertEquals(-1, calculo.cambio)
    }
}
