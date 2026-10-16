package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.Fixtures.AHORA
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

/** Qué avisa Zonda después de sincronizar en segundo plano (cambio 13). */
class AvisosDeZondaTest {

    private val resultado = ResultadoSincronizacion(
        nuevas = 3, modificadas = 0, altasNuevas = 2, accionesEnviadas = 0, accionesConError = 0, hora = AHORA,
        idsAltasNuevas = listOf("a-1", "a-2"),
        idsAltasNuevasConAvisoTelegram = setOf("a-1"),
    )

    private fun haceMinutos(minutos: Long) = AHORA.minus(Duration.ofMinutes(minutos))

    // ---- Sin aviso doble ----

    @Test
    fun `sin Telegram vinculado Zonda avisa todas las altas nuevas`() {
        assertEquals(listOf("a-1", "a-2"), AvisosDeZonda.altasParaNotificar(resultado, telegramVinculado = false, avisos = PreferenciasAvisos()))
    }

    @Test
    fun `con Telegram vinculado no repite las que Telegram ya aviso`() {
        assertEquals(listOf("a-2"), AvisosDeZonda.altasParaNotificar(resultado, telegramVinculado = true, avisos = PreferenciasAvisos()))
        // En silencio Telegram también avisa: el mensaje llega, sin sonar.
        val enSilencio = PreferenciasAvisos(alta = ModoAviso.SILENCIO)
        assertEquals(listOf("a-2"), AvisosDeZonda.altasParaNotificar(resultado, telegramVinculado = true, avisos = enSilencio))
    }

    @Test
    fun `con las altas en Sin aviso en Telegram, Zonda avisa todas`() {
        val sinAviso = PreferenciasAvisos(alta = ModoAviso.SIN_AVISO)

        assertEquals(listOf("a-1", "a-2"), AvisosDeZonda.altasParaNotificar(resultado, telegramVinculado = true, avisos = sinAviso))
    }

    // ---- Recordatorio ----

    @Test
    fun `recuerda las altas sin revisar que llegaron hace 15 minutos o mas, de la mas vieja a la mas nueva`() {
        val altas = listOf(
            Fixtures.alerta("a-20", creadaEn = haceMinutos(20)),
            Fixtures.alerta("a-15", creadaEn = haceMinutos(15)),
            Fixtures.alerta("a-40", creadaEn = haceMinutos(40)),
            Fixtures.alerta("a-10", creadaEn = haceMinutos(10)),
        )

        val recordar = AvisosDeZonda.altasParaRecordar(altas, recordadasHasta = null, recienLlegadas = emptyList(), ahora = AHORA)

        assertEquals(listOf("a-40", "a-20", "a-15"), recordar.map { it.id })
    }

    @Test
    fun `cada alta se recuerda una sola vez`() {
        val altas = listOf(Fixtures.alerta("a-40", creadaEn = haceMinutos(40)), Fixtures.alerta("a-20", creadaEn = haceMinutos(20)))

        val recordar = AvisosDeZonda.altasParaRecordar(altas, recordadasHasta = haceMinutos(40), recienLlegadas = emptyList(), ahora = AHORA)

        assertEquals(listOf("a-20"), recordar.map { it.id })
    }

    @Test
    fun `no recuerda las revisadas, las medias, las de mas de un dia ni las recien llegadas`() {
        val altas = listOf(
            Fixtures.alerta("revisada", creadaEn = haceMinutos(30), revision = Fixtures.revision()),
            Fixtures.alerta("media", creadaEn = haceMinutos(30), severidad = Severidad.MEDIA),
            Fixtures.alerta("vieja", creadaEn = AHORA.minus(Duration.ofHours(25))),
            Fixtures.alerta("recien", creadaEn = haceMinutos(30)),
        )

        val recordar = AvisosDeZonda.altasParaRecordar(altas, recordadasHasta = null, recienLlegadas = listOf("recien"), ahora = AHORA)

        assertEquals(emptyList<Alerta>(), recordar)
    }
}
