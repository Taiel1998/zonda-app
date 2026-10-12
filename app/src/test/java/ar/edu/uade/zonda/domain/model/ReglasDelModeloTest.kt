package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas chicas del modelo: severidad recomendada, motivos, filtros y datos de pareo. */
class ReglasDelModeloTest {

    @Test
    fun `la severidad recomendada cubre todos los tipos con los valores acordados`() {
        val recomendada = SeveridadPorTipo.RECOMENDADA

        assertEquals(TipoAtaque.entries.toSet(), recomendada.valores.keys)
        assertEquals(Severidad.ALTA, recomendada.severidadDe(TipoAtaque.FUERZA_BRUTA))
        assertEquals(Severidad.ALTA, recomendada.severidadDe(TipoAtaque.SIN_TIPIFICAR))
        assertEquals(Severidad.MEDIA, recomendada.severidadDe(TipoAtaque.ESCANEO))
        assertEquals(Severidad.MEDIA, recomendada.severidadDe(TipoAtaque.DDOS))
        assertEquals(Severidad.MEDIA, recomendada.severidadDe(TipoAtaque.SOLO_REGLAS_O_UMBRALES))
        assertEquals(Severidad.BAJA, recomendada.severidadDe(TipoAtaque.PROBABLE_FALSA_ALARMA))
    }

    @Test
    fun `una red puede cambiar la severidad de un tipo y el resto queda en lo recomendado`() {
        val propia = SeveridadPorTipo(emptyMap()).con(TipoAtaque.ESCANEO, Severidad.ALTA)

        assertEquals(Severidad.ALTA, propia.severidadDe(TipoAtaque.ESCANEO))
        assertEquals(Severidad.BAJA, propia.severidadDe(TipoAtaque.PROBABLE_FALSA_ALARMA))
    }

    @Test
    fun `cada motivo pertenece a un solo resultado y hay motivos para los dos`() {
        val reales = Motivo.para(ResultadoRevision.ATAQUE_REAL)
        val falsos = Motivo.para(ResultadoRevision.FALSO_POSITIVO)

        assertTrue(reales.isNotEmpty() && falsos.isNotEmpty())
        assertTrue(reales.all { it.resultado == ResultadoRevision.ATAQUE_REAL })
        assertTrue(falsos.all { it.resultado == ResultadoRevision.FALSO_POSITIVO })
        assertEquals(Motivo.entries.size, reales.size + falsos.size)
    }

    @Test
    fun `el filtro por defecto no esconde nada y el de un contador si`() {
        assertFalse(FiltroAlertas.TODAS.activo)
        assertTrue(FiltroAlertas.sinRevisarDe(Severidad.MEDIA).activo)
        assertEquals(EstadoRevision.SIN_REVISAR, FiltroAlertas.sinRevisarDe(Severidad.MEDIA).estado)
    }

    @Test
    fun `cada periodo cuenta desde la cantidad de horas que dice`() {
        assertEquals(Fixtures.AHORA.minus(Duration.ofHours(24)), Periodo.ULTIMAS_24_HORAS.desde(Fixtures.AHORA))
        assertEquals(Fixtures.AHORA.minus(Duration.ofDays(7)), Periodo.ULTIMOS_7_DIAS.desde(Fixtures.AHORA))
        assertEquals(Fixtures.AHORA.minus(Duration.ofDays(30)), Periodo.ULTIMOS_30_DIAS.desde(Fixtures.AHORA))
        assertNull(Periodo.TODO.desde(Fixtures.AHORA))
    }

    @Test
    fun `el QR de pareo trae la direccion y el codigo`() {
        val datos = DatosPareo.desdeQr("zonda:https://zonda-equipo.duckdns.org#k7m2-qx9p")

        assertEquals("https://zonda-equipo.duckdns.org", datos?.urlBase)
        assertEquals("K7M2-QX9P", datos?.codigo)
    }

    @Test
    fun `un QR que no es de Zonda o con un codigo mal formado se rechaza`() {
        assertNull(DatosPareo.desdeQr("https://ejemplo.com"))
        assertNull(DatosPareo.desdeQr("zonda:https://zonda-equipo.duckdns.org#K7M2"))
        assertNull(DatosPareo.desdeQr("zonda:#K7M2-QX9P"))
        assertFalse(DatosPareo("https://x", "1234").codigoValido)
    }

    @Test
    fun `una alerta sabe que metodos la detectaron y si esta pendiente de envio`() {
        val alerta = Fixtures.alerta(metodos = setOf(MetodoDeteccion.IA, MetodoDeteccion.REGLAS), envio = EstadoEnvio.PENDIENTE)

        assertEquals(setOf(MetodoDeteccion.IA, MetodoDeteccion.REGLAS), alerta.metodos)
        assertTrue(alerta.pendienteDeEnvio)
        assertFalse(alerta.copy(envio = EstadoEnvio.CONFLICTO).pendienteDeEnvio)
        assertEquals(Duration.ofMinutes(9), alerta.duracion)
    }
}
