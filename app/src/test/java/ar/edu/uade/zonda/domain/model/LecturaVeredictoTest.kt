package ar.edu.uade.zonda.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** El veredicto de cada método con palabras (correcciones 5 y 6, decisión 37). */
class LecturaVeredictoTest {

    /** Un caso de la tabla: cuánto marcó el método contra su límite y qué dice el detalle. */
    private data class Caso(val anomalo: Boolean, val valor: Double, val limite: Double, val esperado: LecturaVeredicto)

    @Test
    fun `la tabla del cociente dice cuantas veces se paso, desde el doble`() {
        val casos = listOf(
            // Lo marcó pero no llega al doble: "un poco más".
            Caso(anomalo = true, valor = 2.0, limite = 2.0, esperado = LecturaVeredicto.UnPocoMas),
            Caso(anomalo = true, valor = 1.9, limite = 1.0, esperado = LecturaVeredicto.UnPocoMas),
            // Desde el doble, el número redondeado al entero más cercano.
            Caso(anomalo = true, valor = 2.0, limite = 1.0, esperado = LecturaVeredicto.VecesMas(2)),
            Caso(anomalo = true, valor = 2.5, limite = 1.0, esperado = LecturaVeredicto.VecesMas(3)),
            Caso(anomalo = true, valor = 33.0, limite = 1.0, esperado = LecturaVeredicto.VecesMas(33)),
            // Los del plan: la IA de la demo (0,0377 / 0,0031) y los umbrales (58 / 2).
            Caso(anomalo = true, valor = 0.0377, limite = 0.0031, esperado = LecturaVeredicto.VecesMas(12)),
            Caso(anomalo = true, valor = 58.0, limite = 2.0, esperado = LecturaVeredicto.VecesMas(29)),
            // Sin marcarlo dice "dentro de lo normal", aunque el cociente diga otra cosa: manda la etiqueta.
            Caso(anomalo = false, valor = 1.0, limite = 2.0, esperado = LecturaVeredicto.DentroDeLoNormal),
            Caso(anomalo = false, valor = 33.0, limite = 1.0, esperado = LecturaVeredicto.DentroDeLoNormal),
            // Un límite que no sirve para dividir no inventa un número.
            Caso(anomalo = true, valor = 5.0, limite = 0.0, esperado = LecturaVeredicto.UnPocoMas),
        )

        casos.forEach { caso ->
            assertEquals(caso.toString(), caso.esperado, LecturaVeredicto.de(caso.anomalo, caso.valor, caso.limite))
        }
    }

    @Test
    fun `la IA compara el error de reconstruccion con su umbral`() {
        assertEquals(LecturaVeredicto.VecesMas(13), VeredictoIa(anomalo = true, errorReconstruccion = 0.0412, umbral = 0.00306383).lectura)
        assertEquals(LecturaVeredicto.DentroDeLoNormal, VeredictoIa(anomalo = false, errorReconstruccion = 0.001, umbral = 0.0031).lectura)
    }

    @Test
    fun `los umbrales comparan las conexiones en 60 s con la linea base, o dicen que el servicio es nuevo`() {
        assertEquals(LecturaVeredicto.VecesMas(33), VeredictoUmbrales(anomalo = true, contexto60s = 65, umbralServicio = 2, servicioSinLineaBase = false).lectura)
        assertEquals(LecturaVeredicto.UnPocoMas, VeredictoUmbrales(anomalo = true, contexto60s = 3, umbralServicio = 2, servicioSinLineaBase = false).lectura)
        assertEquals(LecturaVeredicto.ServicioNuevo, VeredictoUmbrales(anomalo = true, contexto60s = 40, umbralServicio = 1, servicioSinLineaBase = true).lectura)
    }

    @Test
    fun `las reglas dicen si el servicio esta autorizado`() {
        assertEquals(LecturaVeredicto.ServicioAutorizado, VeredictoReglas(anomalo = false, parDestino = "192.168.20.20:22", enCatalogo = true).lectura)
        assertEquals(LecturaVeredicto.ServicioNoAutorizado, VeredictoReglas(anomalo = true, parDestino = "192.168.10.50:22", enCatalogo = false).lectura)
    }
}
