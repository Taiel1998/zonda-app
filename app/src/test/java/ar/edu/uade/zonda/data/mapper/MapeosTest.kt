package ar.edu.uade.zonda.data.mapper

import ar.edu.uade.zonda.data.local.entidades.AlertaConDetalles
import ar.edu.uade.zonda.data.local.entidades.SeveridadEntity
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import org.junit.Assert.assertEquals
import org.junit.Test

/** Los conversores entre entidades y dominio tienen que ser de ida y vuelta sin perder nada. */
class MapeosTest {

    @Test
    fun `una alerta va a la entidad y vuelve igual`() {
        val revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)
        val original = Fixtures.alerta(
            metodos = setOf(MetodoDeteccion.IA, MetodoDeteccion.REGLAS),
            revision = revision,
            envio = EstadoEnvio.PENDIENTE,
        )

        val vuelta = AlertaConDetalles(
            alerta = original.aEntidad(),
            revisiones = listOf(revision.aEntidad(original.id)),
            accion = Fixtures.accion(alertaId = original.id, estado = EstadoEnvio.PENDIENTE).aEntidad(),
        ).aDominio()

        assertEquals(original, vuelta)
    }

    @Test
    fun `una accion pendiente con revision ajena va y vuelve igual`() {
        val original = Fixtures.accion(estado = EstadoEnvio.CONFLICTO).copy(
            revisionAjena = Fixtures.revision(origen = OrigenRevision.SERVIDOR).copy(autor = "melinda"),
            ultimoError = "409",
            intentos = 2,
        )

        assertEquals(original, original.aEntidad().aDominio())
    }

    @Test
    fun `una accion de severidades va y vuelve igual`() {
        val original = Fixtures.accion(alertaId = null, revision = null).copy(
            tipo = TipoAccion.CAMBIAR_SEVERIDADES,
            severidades = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA),
            versionVista = null,
        )

        assertEquals(original, original.aEntidad().aDominio())
    }

    @Test
    fun `el codec de severidades es de ida y vuelta y ordena por tipo`() {
        val severidades = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.DDOS, Severidad.BAJA)

        val texto = SeveridadesCodec.codificar(severidades)

        assertEquals("DDOS=BAJA;ESCANEO=MEDIA;FUERZA_BRUTA=ALTA;PROBABLE_FALSA_ALARMA=BAJA;SIN_TIPIFICAR=ALTA;SOLO_REGLAS_O_UMBRALES=MEDIA", texto)
        assertEquals(severidades, SeveridadesCodec.decodificar(texto))
    }

    @Test
    fun `sin filas de severidad rige la recomendada`() {
        assertEquals(SeveridadPorTipo.RECOMENDADA, emptyList<SeveridadEntity>().aDominio())
        assertEquals(
            SeveridadPorTipo(mapOf(TipoAtaque.DDOS to Severidad.ALTA)),
            listOf(SeveridadEntity("red-1", "DDOS", "ALTA")).aDominio(),
        )
    }
}
