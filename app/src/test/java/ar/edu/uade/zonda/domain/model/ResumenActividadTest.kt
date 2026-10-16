package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumenActividadTest {

    // Lunes 12/10/2026 al mediodía; las pruebas usan UTC salvo la de la zona.
    private val ahora = Fixtures.AHORA
    private val utc = ZoneOffset.UTC

    private fun dia(d: Int, mes: Int = 10): LocalDate = LocalDate.of(2026, mes, d)

    /** Una alerta que empezó ese día a las 10 (UTC) y se creó 5 minutos después. */
    private fun alertaDel(
        d: Int,
        id: String,
        tipo: TipoAtaque = TipoAtaque.FUERZA_BRUTA,
        destinoIp: String = "192.168.10.50",
        severidad: Severidad = Severidad.ALTA,
        revision: Revision? = null,
        metodos: Set<MetodoDeteccion> = setOf(MetodoDeteccion.IA),
    ): Alerta {
        val inicio = dia(d).atTime(10, 0).toInstant(utc)
        return Fixtures.alerta(
            id = id, tipoAtaque = tipo, destinoIp = destinoIp, severidad = severidad,
            creadaEn = inicio.plus(Duration.ofMinutes(5)), inicio = inicio, revision = revision, metodos = metodos,
        )
    }

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
            Fixtures.alerta("a-4", severidad = Severidad.MEDIA, tipoAtaque = TipoAtaque.SERVICIO_NO_AUTORIZADO, creadaEn = hace3h, metodos = setOf(MetodoDeteccion.REGLAS)),
            Fixtures.alerta("a-5", severidad = Severidad.BAJA, tipoAtaque = TipoAtaque.PROBABLE_FALSA_ALARMA, creadaEn = hace3h),
            // Fuera del período de 7 días: no cuenta.
            Fixtures.alerta("vieja", severidad = Severidad.ALTA, creadaEn = ahora.minus(Duration.ofDays(10))),
        )
    }

    @Test
    fun `cuenta totales, reales, falsos positivos y sin revisar dentro del periodo`() {
        val resumen = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, ahora, utc)

        assertEquals(5, resumen.totalDelPeriodo)
        assertEquals(5, resumen.total)
        assertEquals(3, resumen.revisadas)
        assertEquals(2, resumen.reales)
        assertEquals(1, resumen.falsosPositivos)
        assertEquals(2, resumen.sinRevisar)
        assertEquals(Proporcion(3, 5), resumen.cobertura)
        assertNull(resumen.grupo)
    }

    @Test
    fun `siete dias son hoy y los seis anteriores, con una barra por dia aunque este en cero`() {
        val lista = listOf(alertaDel(12, "hoy-1"), alertaDel(12, "hoy-2"), alertaDel(9, "viernes"), alertaDel(5, "afuera"))

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc)

        assertEquals(Tramo(dia(6), dia(12)), resumen.tramo)
        assertEquals((6..12).map { dia(it) }, resumen.barras.map { it.tramo.desde })
        assertTrue(resumen.barras.all { it.tramo.unSoloDia })
        assertEquals(listOf(0, 0, 0, 1, 0, 0, 2), resumen.barras.map { it.cantidad })
        assertEquals(3, resumen.total)
    }

    @Test
    fun `el periodo se cuenta por dias del calendario, no por horas`() {
        // El 5/10 a las 23 es de hace menos de 7 x 24 horas, pero cae antes del primer día (6/10).
        val lista = listOf(Fixtures.alerta("domingo", inicio = Instant.parse("2026-10-05T23:00:00Z"), creadaEn = Instant.parse("2026-10-05T23:05:00Z")))

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc)

        assertEquals(0, resumen.totalDelPeriodo)
    }

    @Test
    fun `treinta dias arranca treinta dias atras y junta las barras en cuatro, como el Figma`() {
        // El ejemplo del Figma (100-7024): domingo 27/09, "del 28/08 al 27/09" y las barras 28/8, 7/9, 14/9 y 21/9.
        val domingo = Instant.parse("2026-09-27T05:14:00Z")
        val lista = listOf(
            Fixtures.alerta("primera", inicio = Instant.parse("2026-08-28T10:00:00Z"), creadaEn = Instant.parse("2026-08-28T10:05:00Z")),
            Fixtures.alerta("del-6", inicio = Instant.parse("2026-09-06T10:00:00Z"), creadaEn = Instant.parse("2026-09-06T10:05:00Z")),
            Fixtures.alerta("del-15", inicio = Instant.parse("2026-09-15T10:00:00Z"), creadaEn = Instant.parse("2026-09-15T10:05:00Z")),
            Fixtures.alerta("de-hoy", inicio = Instant.parse("2026-09-27T01:00:00Z"), creadaEn = Instant.parse("2026-09-27T01:05:00Z")),
            Fixtures.alerta("afuera", inicio = Instant.parse("2026-08-27T10:00:00Z"), creadaEn = Instant.parse("2026-08-27T10:05:00Z")),
        )

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.TREINTA_DIAS, domingo, utc)

        assertEquals(Tramo(dia(28, 8), dia(27, 9)), resumen.tramo)
        assertEquals(
            listOf(
                Tramo(dia(28, 8), dia(6, 9)),
                Tramo(dia(7, 9), dia(13, 9)),
                Tramo(dia(14, 9), dia(20, 9)),
                Tramo(dia(21, 9), dia(27, 9)),
            ),
            resumen.barras.map { it.tramo },
        )
        assertEquals(listOf(2, 0, 1, 1), resumen.barras.map { it.cantidad })
        assertEquals(4, resumen.total)
    }

    @Test
    fun `el dia de cada alerta es el de la zona del telefono`() {
        // Las 2 de la mañana del 12/10 en UTC son las 23 del 11/10 en Buenos Aires.
        val lista = listOf(Fixtures.alerta("noche", inicio = Instant.parse("2026-10-12T02:00:00Z"), creadaEn = Instant.parse("2026-10-12T02:05:00Z")))
        val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, buenosAires)

        assertEquals(1, resumen.barras.single { it.tramo.desde == dia(11) }.cantidad)
    }

    @Test
    fun `calcula las dos metricas de exito`() {
        val resumen = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, ahora, utc)

        // Tiempos hasta la revisión: 30 min, 2 h y 1 h; la mediana es 1 h.
        assertEquals(Duration.ofHours(1), resumen.tiempoMedianoHastaRevision)
        // De las dos altas, solo la primera se revisó dentro de la primera hora.
        assertEquals(Proporcion(1, 2), resumen.altasRevisadasEnUnaHora)
    }

    @Test
    fun `las metricas usan la primera revision y la hora en que se hizo en el telefono`() {
        val creada = ahora.minus(Duration.ofHours(6))
        // Se revisó sin conexión a los 20 minutos (llegó al servidor a las 3 horas) y después se cambió.
        val primera = Fixtures.revision(
            ResultadoRevision.FALSO_POSITIVO, Motivo.BACKUP,
            fecha = creada.plus(Duration.ofHours(3)), revisadaEn = creada.plus(Duration.ofMinutes(20)),
        )
        val cambio = Fixtures.revision(ResultadoRevision.ATAQUE_REAL, fecha = creada.plus(Duration.ofHours(5)))
        val alerta = Fixtures.alerta("cambiada", creadaEn = creada, revision = cambio).copy(historial = listOf(primera, cambio))

        val resumen = ResumenActividad.calcular(listOf(alerta), PeriodoResumen.SIETE_DIAS, ahora, utc)

        assertEquals(Duration.ofMinutes(20), resumen.tiempoMedianoHastaRevision)
        assertEquals(Proporcion(1, 1), resumen.altasRevisadasEnUnaHora)
        // El resultado que cuenta es el actual.
        assertEquals(1, resumen.reales)
    }

    @Test
    fun `una alta que todavia no cumplio su primera hora no cuenta hasta que se revisa o pasa la hora`() {
        val reciente = ahora.minus(Duration.ofMinutes(20))
        val sinRevisarReciente = Fixtures.alerta("reciente", creadaEn = reciente)
        val revisadaReciente = Fixtures.alerta("revisada", creadaEn = reciente, revision = Fixtures.revision(fecha = reciente.plus(Duration.ofMinutes(10))))
        val sinRevisarVieja = Fixtures.alerta("vieja", creadaEn = ahora.minus(Duration.ofHours(3)))

        fun altas(vararg lista: Alerta) = ResumenActividad.calcular(lista.toList(), PeriodoResumen.SIETE_DIAS, ahora, utc).altasRevisadasEnUnaHora

        // "ninguna alta con más de 1 h" (Figma 100-8900).
        assertEquals(Proporcion(0, 0), altas(sinRevisarReciente))
        assertEquals(Proporcion(1, 1), altas(sinRevisarReciente, revisadaReciente))
        assertEquals(Proporcion(1, 2), altas(sinRevisarReciente, revisadaReciente, sinRevisarVieja))
    }

    @Test
    fun `la precision de cada metodo es reales sobre revisadas entre las que ese metodo detecto`() {
        val resumen = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, ahora, utc)

        assertEquals(Proporcion(1, 2), resumen.precisionPorMetodo.getValue(MetodoDeteccion.IA))
        assertEquals(Proporcion(1, 1), resumen.precisionPorMetodo.getValue(MetodoDeteccion.UMBRALES))
        assertEquals(Proporcion(1, 1), resumen.precisionPorMetodo.getValue(MetodoDeteccion.REGLAS))
    }

    @Test
    fun `sin alertas revisadas no hay mediana ni precision`() {
        val resumen = ResumenActividad.calcular(listOf(Fixtures.alerta("a-1")), PeriodoResumen.TREINTA_DIAS, ahora, utc)

        assertNull(resumen.tiempoMedianoHastaRevision)
        assertEquals(Proporcion(0, 1), resumen.cobertura)
        assertEquals(Proporcion(0, 0), resumen.precisionPorMetodo.getValue(MetodoDeteccion.IA))
        assertNull(resumen.precisionPorMetodo.getValue(MetodoDeteccion.IA).valor)
    }

    @Test
    fun `cada tipo de ataque tiene su fila, en el orden de la pantalla de severidades y aunque este en cero`() {
        val resumen = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, ahora, utc)

        // Cambio 14: sin "Otros"; primero lo que detectó la IA y después lo que no.
        assertEquals(
            listOf(
                TipoAtaque.FUERZA_BRUTA, TipoAtaque.SIN_TIPIFICAR, TipoAtaque.ESCANEO, TipoAtaque.DDOS,
                TipoAtaque.PROBABLE_FALSA_ALARMA, TipoAtaque.SERVICIO_NO_AUTORIZADO, TipoAtaque.VOLUMEN_INUSUAL,
            ),
            resumen.porTipo.keys.toList(),
        )
        assertEquals(listOf(1, 0, 1, 1, 1, 1, 0), resumen.porTipo.values.toList())
    }

    @Test
    fun `una alerta que vieron las reglas y los umbrales sin la IA cuenta en las dos filas`() {
        // Cambio 9: cada fila dice lo que vio ese detector, así que las filas pueden sumar más que el total.
        val lista = listOf(
            alertaDel(12, "reglas", tipo = TipoAtaque.SERVICIO_NO_AUTORIZADO, metodos = setOf(MetodoDeteccion.REGLAS)),
            alertaDel(12, "umbrales", tipo = TipoAtaque.VOLUMEN_INUSUAL, metodos = setOf(MetodoDeteccion.UMBRALES)),
            alertaDel(
                12, "las-dos", tipo = TipoAtaque.SERVICIO_NO_AUTORIZADO,
                metodos = setOf(MetodoDeteccion.REGLAS, MetodoDeteccion.UMBRALES),
            ),
        )

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc)

        assertEquals(3, resumen.total)
        assertEquals(2, resumen.porTipo.getValue(TipoAtaque.SERVICIO_NO_AUTORIZADO))
        assertEquals(2, resumen.porTipo.getValue(TipoAtaque.VOLUMEN_INUSUAL))
        // Tocar cualquiera de las dos filas la muestra.
        val umbrales = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc, GrupoResumen.Tipo(TipoAtaque.VOLUMEN_INUSUAL))
        assertEquals(2, umbrales.total)
        // El destacado también: los dos tipos empatan y va el primero en pantalla.
        assertEquals(TipoAtaque.SERVICIO_NO_AUTORIZADO, resumen.destacado?.tipo)
        assertEquals(2, resumen.destacado?.delTipo)
    }

    @Test
    fun `revisadas y sin revisar recalculan todo sobre ese grupo`() {
        val revisadas = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, ahora, utc, GrupoResumen.Revisadas)
        val sinRevisar = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, ahora, utc, GrupoResumen.SinRevisar)

        assertEquals(GrupoResumen.Revisadas, revisadas.grupo)
        assertEquals(5, revisadas.totalDelPeriodo)
        assertEquals(3, revisadas.total)
        assertEquals(Proporcion(3, 3), revisadas.cobertura)
        assertEquals(0, revisadas.sinRevisar)

        assertEquals(2, sinRevisar.total)
        assertEquals(0, sinRevisar.revisadas)
        assertNull(sinRevisar.tiempoMedianoHastaRevision)
        assertEquals(1, sinRevisar.porTipo.getValue(TipoAtaque.SERVICIO_NO_AUTORIZADO))
        assertEquals(1, sinRevisar.porTipo.getValue(TipoAtaque.PROBABLE_FALSA_ALARMA))
        assertEquals(0, sinRevisar.porTipo.getValue(TipoAtaque.FUERZA_BRUTA))
    }

    @Test
    fun `altas, reales, falsos positivos, un tipo y un metodo son grupos`() {
        fun total(grupo: GrupoResumen) = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, ahora, utc, grupo).total

        assertEquals(2, total(GrupoResumen.Altas))
        assertEquals(2, total(GrupoResumen.Reales))
        assertEquals(1, total(GrupoResumen.FalsosPositivos))
        assertEquals(1, total(GrupoResumen.Tipo(TipoAtaque.SERVICIO_NO_AUTORIZADO)))
        assertEquals(0, total(GrupoResumen.Tipo(TipoAtaque.VOLUMEN_INUSUAL)))
        assertEquals(1, total(GrupoResumen.Tipo(TipoAtaque.DDOS)))
        assertEquals(0, total(GrupoResumen.Tipo(TipoAtaque.SIN_TIPIFICAR)))
        assertEquals(2, total(GrupoResumen.Metodo(MetodoDeteccion.REGLAS)))
        assertEquals(3, total(GrupoResumen.Metodo(MetodoDeteccion.IA)))
    }

    @Test
    fun `con un dia elegido los demas dias quedan en cero, como el Figma`() {
        val lista = listOf(alertaDel(12, "hoy"), alertaDel(9, "viernes-1"), alertaDel(9, "viernes-2"))

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc, GrupoResumen.Barra(Tramo(dia(9), dia(9))))

        assertEquals(listOf(0, 0, 0, 2, 0, 0, 0), resumen.barras.map { it.cantidad })
        assertEquals(2, resumen.total)
        assertEquals(3, resumen.totalDelPeriodo)
    }

    @Test
    fun `la frase destaca el dia con mas alertas y el tipo que mas se repite en el`() {
        val lista = listOf(
            alertaDel(9, "fb-1"), alertaDel(9, "fb-2"), alertaDel(9, "fb-3"),
            alertaDel(9, "esc", tipo = TipoAtaque.ESCANEO),
            alertaDel(12, "hoy-1"), alertaDel(12, "hoy-2"),
        )

        val destacado = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc).destacado!!

        assertEquals(Tramo(dia(9), dia(9)), destacado.barra.tramo)
        assertEquals(4, destacado.barra.cantidad)
        assertEquals(TipoAtaque.FUERZA_BRUTA, destacado.tipo)
        assertEquals(3, destacado.delTipo)
        // Las tres fueron al mismo equipo: "sobre el mismo servidor".
        assertTrue(destacado.mismoDestino)
    }

    @Test
    fun `si el tipo destacado fue a equipos distintos no se dice que fue al mismo`() {
        val lista = listOf(alertaDel(9, "fb-1", destinoIp = "192.168.10.3"), alertaDel(9, "fb-2", destinoIp = "192.168.10.4"))

        val destacado = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc).destacado!!

        assertFalse(destacado.mismoDestino)
    }

    @Test
    fun `si dos dias empatan se destaca el mas reciente`() {
        val lista = listOf(alertaDel(8, "jueves"), alertaDel(10, "sabado"))

        val destacado = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc).destacado!!

        assertEquals(dia(10), destacado.barra.tramo.desde)
        // Con una sola alerta del tipo no tiene sentido decir "el mismo servidor".
        assertFalse(destacado.mismoDestino)
    }

    @Test
    fun `sin alertas en el periodo no hay nada que destacar`() {
        val resumen = ResumenActividad.calcular(emptyList(), PeriodoResumen.SIETE_DIAS, ahora, utc)

        assertEquals(0, resumen.totalDelPeriodo)
        assertNull(resumen.destacado)
        assertEquals(7, resumen.barras.size)
    }
}
