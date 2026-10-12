package ar.edu.uade.zonda.data.mapper

import ar.edu.uade.zonda.data.remote.AlertaDto
import ar.edu.uade.zonda.data.remote.DtoFixtures
import ar.edu.uade.zonda.data.remote.ProveedorDeApiRetrofit
import ar.edu.uade.zonda.data.remote.RevisionDto
import ar.edu.uade.zonda.data.remote.RevisionEntradaDto
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MapeosRemotosTest {

    private val gson = ProveedorDeApiRetrofit.gson

    @Test
    fun `una alerta del contrato se lee con Gson y pasa al dominio`() {
        val dto = gson.fromJson(ALERTA_DEL_CONTRATO, AlertaDto::class.java)

        val alerta = dto.aDominio("red-1")

        assertEquals("a-5f2c9e1b7a44", alerta.id)
        assertEquals("red-1", alerta.redId)
        assertEquals(Instant.parse("2026-10-11T14:25:10Z"), alerta.creadaEn)
        assertEquals(Instant.parse("2026-10-11T14:02:00Z"), alerta.inicio)
        assertEquals(Instant.parse("2026-10-11T14:20:10Z"), alerta.fin)
        assertEquals(1198, alerta.cantidadFlujos)
        assertEquals(Equipo("172.16.0.1", "gw-borde"), alerta.origen)
        assertEquals(Equipo("192.168.10.50", "srv-web"), alerta.destino)
        assertEquals("ftp", alerta.servicio)
        assertEquals(21, alerta.puerto)
        assertEquals(TipoAtaque.FUERZA_BRUTA, alerta.tipoAtaque)
        assertEquals(1.0, alerta.confianzaTipo!!, 0.0)
        assertEquals(Severidad.ALTA, alerta.severidad)
        assertTrue(alerta.veredictos.ia.anomalo)
        assertEquals(0.0412, alerta.veredictos.ia.errorReconstruccion, 1e-9)
        assertEquals(0.00306383, alerta.veredictos.ia.umbral, 1e-12)
        assertTrue(alerta.veredictos.umbrales.anomalo)
        assertEquals(65, alerta.veredictos.umbrales.contexto60s)
        assertEquals(1, alerta.veredictos.umbrales.umbralServicio)
        assertFalse(alerta.veredictos.umbrales.servicioSinLineaBase)
        assertFalse(alerta.veredictos.reglas.anomalo)
        assertEquals("192.168.10.50:21", alerta.veredictos.reglas.parDestino)
        assertTrue(alerta.veredictos.reglas.enCatalogo)
        assertEquals(setOf(MetodoDeteccion.IA, MetodoDeteccion.UMBRALES), alerta.metodos)
        assertEquals(EstadoRevision.SIN_REVISAR, alerta.estadoRevision)
        assertTrue(alerta.historial.isEmpty())
        assertNull(alerta.revisionActual)
        assertNull(alerta.envio)
        assertEquals(1, alerta.version)
        assertEquals(Instant.parse("2026-10-11T14:25:10Z"), alerta.actualizadaEn)
    }

    @Test
    fun `el tipo vacio es SOLO_REGLAS_O_UMBRALES y el historial llega como revisiones del servidor`() {
        val dto = DtoFixtures.alerta(
            tipoAtaque = null,
            severidad = "media",
            estadoRevision = "falso_positivo",
            version = 2,
            historial = listOf(RevisionDto("falso_positivo", "prueba_del_equipo_de_it", "Prueba de IT", "Pixel de Martín", "2026-10-12T05:00:00+00:00")),
        )

        val alerta = dto.aDominio("red-1")

        assertEquals(TipoAtaque.SOLO_REGLAS_O_UMBRALES, alerta.tipoAtaque)
        assertNull(alerta.confianzaTipo)
        assertEquals(Severidad.MEDIA, alerta.severidad)
        assertEquals(EstadoRevision.FALSO_POSITIVO, alerta.estadoRevision)
        val revision = alerta.revisionActual!!
        assertEquals(ResultadoRevision.FALSO_POSITIVO, revision.resultado)
        assertEquals(Motivo.PRUEBA_DEL_EQUIPO_DE_IT, revision.motivo)
        assertEquals("Prueba de IT", revision.comentario)
        assertEquals("Pixel de Martín", revision.autor)
        assertEquals(Instant.parse("2026-10-12T05:00:00Z"), revision.fecha)
        assertEquals(OrigenRevision.SERVIDOR, revision.origen)
        assertEquals(listOf(revision), alerta.historial)
    }

    @Test
    fun `un valor que el dominio no conoce es un error, no un dato inventado`() {
        assertThrows(IllegalArgumentException::class.java) { DtoFixtures.alerta(tipoAtaque = "ransomware").aDominio("red-1") }
        assertThrows(IllegalArgumentException::class.java) { DtoFixtures.alerta(severidad = "urgente").aDominio("red-1") }
        assertThrows(IllegalArgumentException::class.java) { mapOf("escaneo" to "critica").aSeveridadPorTipo() }
    }

    @Test
    fun `las severidades van y vuelven en minusculas, con la clave del tipo vacio`() {
        val dto = SeveridadPorTipo.RECOMENDADA.aDto()

        assertEquals(6, dto.size)
        assertEquals("alta", dto["fuerza_bruta"])
        assertEquals("alta", dto["sin_tipificar"])
        assertEquals("media", dto["solo_reglas_o_umbrales"])
        assertEquals("baja", dto["probable_falsa_alarma"])
        assertEquals(SeveridadPorTipo.RECOMENDADA, dto.aSeveridadPorTipo())
    }

    @Test
    fun `la accion de revisar se manda con el id de la accion, el motivo en minusculas y la version vista`() {
        val accion = Fixtures.accion(
            id = "accion-7",
            revision = Fixtures.revision(resultado = ResultadoRevision.FALSO_POSITIVO, motivo = Motivo.TAREA_PROGRAMADA),
        )

        val entrada = accion.aRevisionEntrada()!!

        assertEquals("accion-7", entrada.accionId)
        assertEquals("falso_positivo", entrada.resultado)
        assertEquals("tarea_programada", entrada.motivo)
        assertEquals("Comentario de prueba", entrada.comentario)
        assertEquals(1, entrada.versionVista)
        assertFalse(entrada.forzar)
        val json = gson.toJson(entrada)
        assertTrue(json, json.contains("\"accionId\":\"accion-7\"") && json.contains("\"versionVista\":1") && json.contains("\"forzar\":false"))
        assertEquals(entrada, gson.fromJson(json, RevisionEntradaDto::class.java))
        assertNull(Fixtures.accion(alertaId = null, revision = null).aRevisionEntrada())
    }

    companion object {
        /** El ejemplo de backend/README.md, tal cual. */
        private val ALERTA_DEL_CONTRATO = """
            {
              "id": "a-5f2c9e1b7a44", "creadaEn": "2026-10-11T14:25:10+00:00",
              "inicio": "2026-10-11T14:02:00+00:00", "fin": "2026-10-11T14:20:10+00:00",
              "cantidadFlujos": 1198, "uidReferencia": "CxYz1...",
              "origenIp": "172.16.0.1", "origenNombre": "gw-borde", "destinoIp": "192.168.10.50", "destinoNombre": "srv-web",
              "servicio": "ftp", "puerto": 21, "puertoReferencia": 21, "puertosDistintos": 1,
              "tipoAtaque": "fuerza_bruta", "confianzaTipo": 1.0, "severidad": "alta",
              "veredictos": {
                "ia": true, "errorReconstruccion": 0.0412, "umbralIa": 0.00306383,
                "umbrales": true, "contexto60s": 65, "umbralServicio": 1, "servicioSinLineaBase": false,
                "reglas": false, "parDestino": "192.168.10.50:21", "parEnCatalogo": true
              },
              "estadoRevision": "sin_revisar", "historial": [], "version": 1, "actualizadaEn": "2026-10-11T14:25:10+00:00"
            }
        """.trimIndent()
    }
}
