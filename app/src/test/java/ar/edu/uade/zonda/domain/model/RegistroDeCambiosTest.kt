package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El registro de cambios (decisión 40) y el historial de color de cada alerta que se arma con él. */
class RegistroDeCambiosTest {

    private val creada: Instant = Fixtures.AHORA.minus(Duration.ofHours(5))
    private val alerta = Fixtures.alerta(tipoAtaque = TipoAtaque.FUERZA_BRUTA, creadaEn = creada, destinoIp = "192.168.10.50")

    private fun cambio(
        id: Long,
        horas: Long,
        tipo: TipoCambio,
        objeto: String,
        antes: String,
        despues: String,
    ) = CambioRegistro(
        id = id,
        fecha = creada.plus(Duration.ofHours(horas)),
        tipo = tipo,
        autorId = "d-1",
        autor = "Taiel",
        objeto = objeto,
        nombre = null,
        antes = antes,
        despues = despues,
        motivo = "Motivo $id",
    )

    @Test
    fun `cada linea sabe leer su tipo de ataque y sus valores`() {
        val severidad = cambio(1, 1, TipoCambio.SEVERIDAD, "fuerza_bruta", "alta", "media")
        val importancia = cambio(2, 1, TipoCambio.IMPORTANCIA, "192.168.10.50", "normal", "critico")
        val perfil = cambio(3, 1, TipoCambio.PERFIL, "d-2", "analista", "administrador")

        assertEquals(TipoAtaque.FUERZA_BRUTA, severidad.tipoDeAtaque)
        assertEquals(Severidad.ALTA, severidad.severidadAntes)
        assertEquals(Severidad.MEDIA, severidad.severidadDespues)
        assertNull(importancia.tipoDeAtaque)
        assertEquals(Importancia.NORMAL, importancia.importanciaAntes)
        assertEquals(Importancia.CRITICO, importancia.importanciaDespues)
        assertEquals(Perfil.ANALISTA, perfil.perfilAntes)
        assertEquals(Perfil.ADMINISTRADOR, perfil.perfilDespues)
    }

    @Test
    fun `el historial muestra solo los cambios que cambiaron el color de la alerta`() {
        val registro = listOf(
            // Antes de que llegara la alerta: no cuenta.
            cambio(1, -2, TipoCambio.SEVERIDAD, "fuerza_bruta", "media", "alta"),
            // Bajó fuerza bruta: la alerta pasó de alta a media.
            cambio(2, 1, TipoCambio.SEVERIDAD, "fuerza_bruta", "alta", "media"),
            // Otro tipo y otro equipo: no la tocan.
            cambio(3, 2, TipoCambio.SEVERIDAD, "escaneo", "media", "alta"),
            cambio(4, 2, TipoCambio.IMPORTANCIA, "192.168.10.77", "normal", "critico"),
            // Su equipo pasó a crítico: de media a alta.
            cambio(5, 3, TipoCambio.IMPORTANCIA, "192.168.10.50", "normal", "critico"),
            // Fuerza bruta volvió a alta con el equipo crítico: sigue alta, no aparece.
            cambio(6, 4, TipoCambio.SEVERIDAD, "fuerza_bruta", "media", "alta"),
        )

        val eventos = cambiosDeSeveridad(
            alerta,
            registro,
            SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA),
            mapOf("192.168.10.50" to Importancia.CRITICO, "192.168.10.77" to Importancia.CRITICO),
        )

        assertEquals(listOf(2L, 5L), eventos.map { it.cambio.id })
        assertEquals(Severidad.ALTA to Severidad.MEDIA, eventos[0].antes to eventos[0].despues)
        assertEquals(Severidad.MEDIA to Severidad.ALTA, eventos[1].antes to eventos[1].despues)
        assertEquals(creada.plus(Duration.ofHours(1)), eventos[0].fecha)
    }

    @Test
    fun `sin cambios despues de que llego la alerta el historial no suma nada`() {
        val registro = listOf(cambio(1, -1, TipoCambio.SEVERIDAD, "fuerza_bruta", "media", "alta"))

        assertTrue(cambiosDeSeveridad(alerta, registro, SeveridadPorTipo.RECOMENDADA, emptyMap()).isEmpty())
    }
}
