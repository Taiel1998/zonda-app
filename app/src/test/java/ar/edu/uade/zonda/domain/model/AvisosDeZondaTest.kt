package ar.edu.uade.zonda.domain.model

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.Fixtures.AHORA
import ar.edu.uade.zonda.domain.repository.AlertaNueva
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

/** Qué avisa Zonda después de sincronizar en segundo plano (cambio 13 y 1.0.4). */
class AvisosDeZondaTest {

    /** Dos altas (a-1 ya avisada por Telegram), dos medias (m-1 avisada) y una baja. */
    private val resultado = ResultadoSincronizacion(
        nuevas = 5, modificadas = 0, altasNuevas = 2, accionesEnviadas = 0, accionesConError = 0, hora = AHORA,
        alertasNuevas = listOf(
            AlertaNueva("a-1", Severidad.ALTA, conAvisoTelegram = true),
            AlertaNueva("a-2", Severidad.ALTA),
            AlertaNueva("m-1", Severidad.MEDIA, conAvisoTelegram = true),
            AlertaNueva("m-2", Severidad.MEDIA),
            AlertaNueva("b-1", Severidad.BAJA),
        ),
    )

    private val todasPrendidas = PreferenciasNotificaciones(baja = ModoNotificacion.EN_SILENCIO)

    private fun haceMinutos(minutos: Long) = AHORA.minus(Duration.ofMinutes(minutos))

    // ---- Sin aviso doble, por severidad ----

    @Test
    fun `sin Telegram vinculado Zonda avisa todas las nuevas de las severidades que no estan en Sin aviso`() {
        val avisar = AvisosDeZonda.paraNotificar(resultado, telegramVinculado = false, avisos = PreferenciasAvisos(), notificaciones = todasPrendidas)

        assertEquals(mapOf(Severidad.ALTA to listOf("a-1", "a-2"), Severidad.MEDIA to listOf("m-1", "m-2"), Severidad.BAJA to listOf("b-1")), avisar)
    }

    @Test
    fun `de fabrica la baja no se avisa`() {
        val avisar = AvisosDeZonda.paraNotificar(resultado, telegramVinculado = false, avisos = PreferenciasAvisos(), notificaciones = PreferenciasNotificaciones())

        assertEquals(setOf(Severidad.ALTA, Severidad.MEDIA), avisar.keys)
    }

    @Test
    fun `con Telegram vinculado no repite las que Telegram ya aviso, en cada severidad`() {
        val avisar = AvisosDeZonda.paraNotificar(resultado, telegramVinculado = true, avisos = PreferenciasAvisos(), notificaciones = todasPrendidas)

        assertEquals(mapOf(Severidad.ALTA to listOf("a-2"), Severidad.MEDIA to listOf("m-2"), Severidad.BAJA to listOf("b-1")), avisar)
    }

    @Test
    fun `si en Telegram esa severidad esta en Sin aviso, Zonda la avisa entera`() {
        val avisos = PreferenciasAvisos(media = ModoAviso.SIN_AVISO)

        val avisar = AvisosDeZonda.paraNotificar(resultado, telegramVinculado = true, avisos = avisos, notificaciones = todasPrendidas)

        assertEquals(listOf("a-2"), avisar[Severidad.ALTA])
        assertEquals(listOf("m-1", "m-2"), avisar[Severidad.MEDIA])
    }

    @Test
    fun `una severidad en Sin aviso en Zonda no aparece aunque Telegram no la haya avisado`() {
        val notificaciones = PreferenciasNotificaciones(alta = ModoNotificacion.SIN_AVISO)

        val avisar = AvisosDeZonda.paraNotificar(resultado, telegramVinculado = false, avisos = PreferenciasAvisos(), notificaciones = notificaciones)

        assertEquals(setOf(Severidad.MEDIA), avisar.keys)
    }

    @Test
    fun `si Telegram aviso todas las de una severidad, esa severidad no aparece`() {
        val soloAvisadas = resultado.copy(alertasNuevas = listOf(AlertaNueva("a-1", Severidad.ALTA, conAvisoTelegram = true)))

        val avisar = AvisosDeZonda.paraNotificar(soloAvisadas, telegramVinculado = true, avisos = PreferenciasAvisos(), notificaciones = todasPrendidas)

        assertEquals(emptyMap<Severidad, List<String>>(), avisar)
    }

    // ---- Cómo suena el recordatorio ----

    @Test
    fun `el recordatorio suena como las altas de Zonda, y con las altas en Sin aviso con sonido y vibracion`() {
        assertEquals(ModoNotificacion.SOLO_VIBRACION, PreferenciasNotificaciones(alta = ModoNotificacion.SOLO_VIBRACION).modoDelRecordatorio)
        assertEquals(ModoNotificacion.EN_SILENCIO, PreferenciasNotificaciones(alta = ModoNotificacion.EN_SILENCIO).modoDelRecordatorio)
        assertEquals(ModoNotificacion.SONIDO_Y_VIBRACION, PreferenciasNotificaciones(alta = ModoNotificacion.SIN_AVISO).modoDelRecordatorio)
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
