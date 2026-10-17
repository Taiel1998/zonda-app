package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los filtros del Resumen que se suman (cambio 16), con una tabla de seis alertas como la de la demo
 * que vio Taiel: días y tipos de a varios, revisión y modelo de a uno.
 */
class FiltroResumenTest {

    private val utc = ZoneOffset.UTC
    private val sabado = Franja.dia(LocalDate.of(2026, 10, 10))
    private val domingo = Franja.dia(LocalDate.of(2026, 10, 11))
    private val lunes = Franja.dia(LocalDate.of(2026, 10, 12))
    private val altas = SeveridadPorTipo.RECOMENDADA.tiposAltos

    private fun alerta(
        id: String,
        dia: Franja,
        tipo: TipoAtaque,
        revision: ResultadoRevision? = null,
        metodos: Set<MetodoDeteccion> = setOf(MetodoDeteccion.IA),
        hora: Int = 10,
    ): Alerta {
        val inicio = dia.primerDia.atTime(hora, 0).toInstant(utc)
        return Fixtures.alerta(
            id = id, tipoAtaque = tipo, inicio = inicio, creadaEn = inicio.plus(Duration.ofMinutes(5)), metodos = metodos,
            revision = revision?.let { Fixtures.revision(it, fecha = inicio.plus(Duration.ofMinutes(30))) },
        )
    }

    /**
     * | id  | día     | tipo               | revisión        | modelos          |
     * | a-1 | sábado  | fuerza bruta       | real            | IA               |
     * | a-2 | sábado  | escaneo de puertos | falso positivo  | IA y umbrales    |
     * | a-3 | domingo | fuerza bruta       | sin revisar     | IA               |
     * | a-4 | domingo | fuerza bruta       | real            | umbrales         |
     * | a-5 | domingo | denegación         | sin revisar     | IA               |
     * | a-6 | lunes   | reglas y umbrales  | sin revisar     | reglas y umbrales|
     */
    private val tabla = listOf(
        alerta("a-1", sabado, TipoAtaque.FUERZA_BRUTA, ResultadoRevision.ATAQUE_REAL),
        alerta("a-2", sabado, TipoAtaque.ESCANEO, ResultadoRevision.FALSO_POSITIVO, setOf(MetodoDeteccion.IA, MetodoDeteccion.UMBRALES)),
        alerta("a-3", domingo, TipoAtaque.FUERZA_BRUTA),
        alerta("a-4", domingo, TipoAtaque.FUERZA_BRUTA, ResultadoRevision.ATAQUE_REAL, setOf(MetodoDeteccion.UMBRALES)),
        alerta("a-5", domingo, TipoAtaque.DDOS),
        alerta("a-6", lunes, TipoAtaque.SERVICIO_NO_AUTORIZADO, metodos = setOf(MetodoDeteccion.REGLAS, MetodoDeteccion.UMBRALES)),
    )

    private fun ids(filtro: FiltroResumen): List<String> = tabla.filter { filtro.incluye(it, utc) }.map { it.id }

    @Test
    fun `los filtros de distinta clase se suman`() {
        assertEquals(listOf("a-3", "a-4", "a-5"), ids(FiltroResumen(barras = setOf(domingo))))
        // El domingo y fuerza bruta: la fuerza bruta del domingo.
        assertEquals(listOf("a-3", "a-4"), ids(FiltroResumen(barras = setOf(domingo), tipos = setOf(TipoAtaque.FUERZA_BRUTA))))
        assertEquals(listOf("a-4"), ids(FiltroResumen(barras = setOf(domingo), tipos = setOf(TipoAtaque.FUERZA_BRUTA), revision = RevisionResumen.REALES)))
        assertEquals(listOf("a-4"), ids(FiltroResumen(tipos = setOf(TipoAtaque.FUERZA_BRUTA), metodo = MetodoDeteccion.UMBRALES)))
    }

    @Test
    fun `dos dias o dos tipos cuentan las de cualquiera de los dos`() {
        assertEquals(listOf("a-1", "a-2", "a-3", "a-4", "a-5"), ids(FiltroResumen(barras = setOf(sabado, domingo))))
        assertEquals(listOf("a-1", "a-2", "a-3", "a-4"), ids(FiltroResumen(tipos = setOf(TipoAtaque.FUERZA_BRUTA, TipoAtaque.ESCANEO))))
        // Sábado y domingo · fuerza bruta y escaneo de puertos.
        val cuatro = FiltroResumen(barras = setOf(sabado, domingo), tipos = setOf(TipoAtaque.FUERZA_BRUTA, TipoAtaque.ESCANEO))
        assertEquals(listOf("a-1", "a-2", "a-3", "a-4"), ids(cuatro))
        assertEquals(4, cuatro.cantidad)
    }

    @Test
    fun `la alerta de las reglas y los umbrales entra una sola vez con sus dos tipos elegidos`() {
        val losDos = FiltroResumen(tipos = setOf(TipoAtaque.SERVICIO_NO_AUTORIZADO, TipoAtaque.VOLUMEN_INUSUAL))

        assertEquals(listOf("a-6"), ids(losDos))
        assertEquals(listOf("a-6"), ids(FiltroResumen(tipos = setOf(TipoAtaque.VOLUMEN_INUSUAL))))
    }

    @Test
    fun `tocar un dia o un tipo lo suma, y tocarlo de nuevo lo saca`() {
        val conDomingo = FiltroResumen().tocar(ToqueResumen.Barra(domingo), altas)
        val conLosDos = conDomingo.tocar(ToqueResumen.Barra(sabado), altas)
        assertEquals(setOf(sabado, domingo), conLosDos.barras)
        assertEquals(setOf(sabado), conLosDos.tocar(ToqueResumen.Barra(domingo), altas).barras)

        val tipos = FiltroResumen()
            .tocar(ToqueResumen.Tipo(TipoAtaque.FUERZA_BRUTA), altas)
            .tocar(ToqueResumen.Tipo(TipoAtaque.ESCANEO), altas)
        assertEquals(setOf(TipoAtaque.FUERZA_BRUTA, TipoAtaque.ESCANEO), tipos.tipos)
        assertEquals(setOf(TipoAtaque.ESCANEO), tipos.tocar(ToqueResumen.Tipo(TipoAtaque.FUERZA_BRUTA), altas).tipos)
    }

    @Test
    fun `la revision y el modelo van de a uno - tocar otro cambia el que estaba`() {
        val reales = FiltroResumen().tocar(ToqueResumen.Revision(RevisionResumen.REALES), altas)
        val falsos = reales.tocar(ToqueResumen.Revision(RevisionResumen.FALSOS_POSITIVOS), altas)
        assertEquals(RevisionResumen.FALSOS_POSITIVOS, falsos.revision)
        assertEquals(null, falsos.tocar(ToqueResumen.Revision(RevisionResumen.FALSOS_POSITIVOS), altas).revision)

        val ia = FiltroResumen().tocar(ToqueResumen.Metodo(MetodoDeteccion.IA), altas)
        assertEquals(MetodoDeteccion.UMBRALES, ia.tocar(ToqueResumen.Metodo(MetodoDeteccion.UMBRALES), altas).metodo)
        assertEquals(null, ia.tocar(ToqueResumen.Metodo(MetodoDeteccion.IA), altas).metodo)
    }

    @Test
    fun `tocar las altas suma los tipos que la red tiene en alta, y tocarlas de nuevo los saca`() {
        val conEscaneo = FiltroResumen(tipos = setOf(TipoAtaque.ESCANEO))

        val conAltas = conEscaneo.tocar(ToqueResumen.Altas, altas)
        assertEquals(setOf(TipoAtaque.ESCANEO, TipoAtaque.FUERZA_BRUTA, TipoAtaque.SIN_TIPIFICAR), conAltas.tipos)
        assertEquals(setOf(TipoAtaque.ESCANEO), conAltas.tocar(ToqueResumen.Altas, altas).tipos)

        val soloAltas = FiltroResumen().tocar(ToqueResumen.Altas, altas)
        assertTrue(soloAltas.tiposSonLasAltas(altas))
        assertFalse(conAltas.tiposSonLasAltas(altas))
        // Si la red pone el escaneo en alta, las altas son otras.
        val conEscaneoAlto = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA).tiposAltos
        assertEquals(setOf(TipoAtaque.FUERZA_BRUTA, TipoAtaque.SIN_TIPIFICAR, TipoAtaque.ESCANEO), conEscaneoAlto)
    }

    @Test
    fun `tocar el total saca todo y cambiar de periodo saca solo los dias`() {
        val filtro = FiltroResumen(barras = setOf(domingo), tipos = setOf(TipoAtaque.FUERZA_BRUTA), revision = RevisionResumen.REALES, metodo = MetodoDeteccion.IA)

        assertTrue(filtro.tocar(ToqueResumen.Total, altas).vacio)
        assertEquals(FiltroResumen(tipos = setOf(TipoAtaque.FUERZA_BRUTA), revision = RevisionResumen.REALES, metodo = MetodoDeteccion.IA), filtro.sinBarras())
    }

    @Test
    fun `el resumen con filtros guarda esas alertas de la mas nueva a la mas vieja y todas las del periodo`() {
        val ahora = LocalDate.of(2026, 10, 12).atTime(12, 0).toInstant(utc)
        val filtro = FiltroResumen(tipos = setOf(TipoAtaque.FUERZA_BRUTA))

        val resumen = ResumenActividad.calcular(tabla, PeriodoResumen.SIETE_DIAS, ahora, utc, filtro)

        assertEquals(3, resumen.total)
        assertEquals(listOf("a-4", "a-3", "a-1").sorted(), resumen.alertas.map { it.id }.sorted())
        assertTrue(resumen.alertas.zipWithNext().all { (a, b) -> !a.inicio.isBefore(b.inicio) })
        assertEquals(6, resumen.delPeriodo.size)
        assertEquals(filtro, resumen.filtro)
    }
}
