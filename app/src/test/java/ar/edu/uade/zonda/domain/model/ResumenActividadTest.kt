package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumenActividadTest {

    private val ahora = Fixtures.AHORA

    private fun alertas(): List<Alerta> {
        val hace5h = ahora.minus(Duration.ofHours(5))
        val hace4h = ahora.minus(Duration.ofHours(4))
        val hace3h = ahora.minus(Duration.ofHours(3))
        return listOf(
            // Alta, real, revisada a los 30 minutos, detectada por IA y umbrales.
            Fixtures.alerta(
                "a-1", severidad = Severidad.ALTA, tipoAtaque = TipoAtaque.FUERZA_BRUTA, creadaEn = hace5h,
                revision = Fixtures.revision(ResultadoRevision.ATAQUE_REAL, fecha = hace5h.plus(Duration.ofMinutes(30))),
                metodos = setOf(MetodoDeteccion.IA, MetodoDeteccion.UMBRALES),
            ),
            // Alta, falso positivo, revisada a las 2 horas, detectada por IA.
            Fixtures.alerta(
                "a-2", severidad = Severidad.ALTA, tipoAtaque = TipoAtaque.ESCANEO, creadaEn = hace4h,
                revision = Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, Motivo.TAREA_PROGRAMADA, fecha = hace4h.plus(Duration.ofHours(2))),
                metodos = setOf(MetodoDeteccion.IA),
            ),
            // Media, real, revisada a la hora, detectada por reglas.
            Fixtures.alerta(
                "a-3", severidad = Severidad.MEDIA, tipoAtaque = TipoAtaque.DDOS, creadaEn = hace3h,
                revision = Fixtures.revision(ResultadoRevision.ATAQUE_REAL, fecha = hace3h.plus(Duration.ofHours(1))),
                metodos = setOf(MetodoDeteccion.REGLAS),
            ),
            // Dos sin revisar.
            Fixtures.alerta("a-4", severidad = Severidad.MEDIA, tipoAtaque = TipoAtaque.SOLO_REGLAS_O_UMBRALES, creadaEn = hace3h, metodos = setOf(MetodoDeteccion.REGLAS)),
            Fixtures.alerta("a-5", severidad = Severidad.BAJA, tipoAtaque = TipoAtaque.PROBABLE_FALSA_ALARMA, creadaEn = hace3h),
            // Fuera del período de 7 días: no cuenta.
            Fixtures.alerta("vieja", severidad = Severidad.ALTA, creadaEn = ahora.minus(Duration.ofDays(10))),
        )
    }

    @Test
    fun `cuenta totales, reales, falsos positivos y sin revisar dentro del periodo`() {
        val resumen = ResumenActividad.calcular(alertas(), Periodo.ULTIMOS_7_DIAS, ahora, ZoneOffset.UTC)

        assertEquals(5, resumen.total)
        assertEquals(2, resumen.reales)
        assertEquals(1, resumen.falsosPositivos)
        assertEquals(2, resumen.sinRevisar)
        assertEquals(Proporcion(3, 5), resumen.cobertura)
        assertEquals(listOf(AlertasPorDia(LocalDate.of(2026, 10, 12), 5)), resumen.porDia)
        assertEquals(1, resumen.porTipo.getValue(TipoAtaque.FUERZA_BRUTA))
        assertEquals(2, resumen.porSeveridad.getValue(Severidad.ALTA))
    }

    @Test
    fun `calcula las dos metricas de exito`() {
        val resumen = ResumenActividad.calcular(alertas(), Periodo.ULTIMOS_7_DIAS, ahora, ZoneOffset.UTC)

        // Tiempos hasta la revisión: 30 min, 2 h y 1 h; la mediana es 1 h.
        assertEquals(Duration.ofHours(1), resumen.tiempoMedianoHastaRevision)
        // De las dos altas, solo la primera se revisó dentro de la primera hora.
        assertEquals(Proporcion(1, 2), resumen.altasRevisadasEnUnaHora)
    }

    @Test
    fun `la precision de cada metodo es reales sobre revisadas entre las que ese metodo detecto`() {
        val resumen = ResumenActividad.calcular(alertas(), Periodo.ULTIMOS_7_DIAS, ahora, ZoneOffset.UTC)

        assertEquals(Proporcion(1, 2), resumen.precisionPorMetodo.getValue(MetodoDeteccion.IA))
        assertEquals(Proporcion(1, 1), resumen.precisionPorMetodo.getValue(MetodoDeteccion.UMBRALES))
        assertEquals(Proporcion(1, 1), resumen.precisionPorMetodo.getValue(MetodoDeteccion.REGLAS))
    }

    @Test
    fun `sin alertas revisadas no hay mediana ni precision`() {
        val resumen = ResumenActividad.calcular(listOf(Fixtures.alerta("a-1")), Periodo.ULTIMOS_30_DIAS, ahora, ZoneOffset.UTC)

        assertNull(resumen.tiempoMedianoHastaRevision)
        assertNull(resumen.cobertura.valor?.takeIf { it > 0 })
        assertEquals(Proporcion(0, 0), resumen.precisionPorMetodo.getValue(MetodoDeteccion.IA))
        assertNull(resumen.precisionPorMetodo.getValue(MetodoDeteccion.IA).valor)
    }

    @Test
    fun `el periodo de 24 horas deja afuera lo de ayer`() {
        val lista = listOf(
            Fixtures.alerta("hoy", creadaEn = ahora.minus(Duration.ofHours(2))),
            Fixtures.alerta("ayer", creadaEn = ahora.minus(Duration.ofHours(30))),
        )

        val resumen = ResumenActividad.calcular(lista, Periodo.ULTIMAS_24_HORAS, ahora, ZoneOffset.UTC)

        assertEquals(1, resumen.total)
    }
}
