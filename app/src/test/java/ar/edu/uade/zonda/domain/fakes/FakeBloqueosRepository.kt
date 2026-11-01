package ar.edu.uade.zonda.domain.fakes

import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.BloqueosDeLaRed
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.EstadoFirewall
import ar.edu.uade.zonda.domain.repository.BloqueosRepository

/**
 * Bloqueos en memoria, como los cuenta el servidor: el teléfono de un administrador deja el bloqueo aprobado
 * en el acto y el de un analista, pedido. Todos los bloqueos son del origen de las alertas de prueba
 * (pc-09, 192.168.10.9). [hecho] guarda lo que se mandó, en orden; con [error], todo lanza ese error.
 */
class FakeBloqueosRepository(
    var firewall: EstadoFirewall = EstadoFirewall(habilitado = true, conectado = true, ultimaConsulta = Fixtures.AHORA, nuncaBloquear = emptyList()),
    var administrador: Boolean = true,
) : BloqueosRepository {

    val bloqueos = mutableListOf<Bloqueo>()
    val hecho = mutableListOf<String>()
    var error: ErrorZonda? = null
    var consultas = 0

    override suspend fun consultar(redId: String): BloqueosDeLaRed {
        consultas++
        error?.let { throw it }
        return BloqueosDeLaRed(bloqueos.reversed(), firewall)
    }

    override suspend fun bloquear(redId: String, accionId: String, alertaId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo {
        error?.let { throw it }
        hecho += "bloquear $alertaId ${duracion.name} $motivo"
        val nuevo = bloqueo(
            id = "b-${bloqueos.size + 1}",
            alertaId = alertaId,
            estado = if (administrador) EstadoBloqueo.APROBADO else EstadoBloqueo.PEDIDO,
            segundos = duracion.segundos,
        ).copy(motivo = motivo)
        bloqueos += nuevo
        return nuevo
    }

    override suspend fun aprobar(redId: String, bloqueoId: String): Bloqueo = cambiar(bloqueoId, "aprobar") {
        it.copy(estado = EstadoBloqueo.APROBADO, aprobadoPor = "Este teléfono", aprobadoEn = Fixtures.AHORA, venceEn = it.segundos?.let { s -> Fixtures.AHORA.plusSeconds(s.toLong()) })
    }

    override suspend fun rechazar(redId: String, bloqueoId: String, motivo: String): Bloqueo = cambiar(bloqueoId, "rechazar $motivo") {
        it.copy(estado = EstadoBloqueo.RECHAZADO, terminadoPor = "Este teléfono", terminadoEn = Fixtures.AHORA, detalle = motivo)
    }

    override suspend fun desbloquear(redId: String, bloqueoId: String, motivo: String): Bloqueo = cambiar(bloqueoId, "desbloquear $motivo") {
        it.copy(estado = EstadoBloqueo.DESBLOQUEADO, terminadoPor = "Este teléfono", terminadoEn = Fixtures.AHORA, detalle = motivo)
    }

    override suspend fun cambiarDuracion(redId: String, bloqueoId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo =
        cambiar(bloqueoId, "duracion ${duracion.name} $motivo") {
            it.copy(estado = EstadoBloqueo.APROBADO, segundos = duracion.segundos, venceEn = duracion.segundos?.let { s -> Fixtures.AHORA.plusSeconds(s.toLong()) })
        }

    private fun cambiar(bloqueoId: String, que: String, cambio: (Bloqueo) -> Bloqueo): Bloqueo {
        error?.let { throw it }
        hecho += "$que $bloqueoId"
        val indice = bloqueos.indexOfFirst { it.id == bloqueoId }
        if (indice < 0) throw ErrorZonda.NoEncontrado()
        bloqueos[indice] = cambio(bloqueos[indice])
        return bloqueos[indice]
    }

    companion object {
        /** Un bloqueo del origen de las alertas de prueba; aprobado por una hora, salvo que se diga otra cosa. */
        fun bloqueo(
            id: String = "b-1",
            alertaId: String = "a-1",
            estado: EstadoBloqueo = EstadoBloqueo.APROBADO,
            segundos: Int? = 3600,
            pedidoPor: String = "Este teléfono",
        ) = Bloqueo(
            id = id,
            alertaId = alertaId,
            ip = "192.168.10.9",
            nombre = "pc-09",
            estado = estado,
            segundos = segundos,
            motivo = "Fuerza bruta de pc-09 a srv-web.",
            pedidoPor = pedidoPor,
            pedidoEn = Fixtures.AHORA.minusSeconds(120),
            aprobadoPor = "Este teléfono".takeIf { estado.enElFirewall },
            aprobadoEn = Fixtures.AHORA.minusSeconds(120).takeIf { estado.enElFirewall },
            venceEn = segundos?.takeIf { estado.enElFirewall }?.let { Fixtures.AHORA.minusSeconds(120).plusSeconds(it.toLong()) },
            aplicadoEn = Fixtures.AHORA.minusSeconds(110).takeIf { estado == EstadoBloqueo.APLICADO },
            terminadoPor = null,
            terminadoEn = null,
            detalle = null,
        )
    }
}
