package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.local.dao.RedesDao
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.remote.BloqueoEntradaDto
import ar.edu.uade.zonda.data.remote.DuracionEntradaDto
import ar.edu.uade.zonda.data.remote.MotivoEntradaDto
import ar.edu.uade.zonda.data.remote.OperacionHttp
import ar.edu.uade.zonda.data.remote.ProveedorDeApi
import ar.edu.uade.zonda.data.remote.ZondaApi
import ar.edu.uade.zonda.data.remote.conErroresTraducidos
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.BloqueosDeLaRed
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.repository.BloqueosRepository

/**
 * Los bloqueos del firewall contra la API de cada red (decisión 51). No guarda nada en el teléfono: un
 * bloqueo se ve como lo dice el servidor en ese momento.
 */
class BloqueosRepositoryImpl(
    private val dao: RedesDao,
    private val apis: ProveedorDeApi,
) : BloqueosRepository {

    private suspend fun api(redId: String): ZondaApi = apis.para(dao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado())

    private suspend fun <T> conErrores(bloque: suspend () -> T): T = conErroresTraducidos(OperacionHttp.BLOQUEO, bloque)

    override suspend fun consultar(redId: String): BloqueosDeLaRed {
        val api = api(redId)
        return conErrores { api.bloqueos() }.aDominio()
    }

    override suspend fun bloquear(redId: String, accionId: String, alertaId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo {
        val api = api(redId)
        return conErrores { api.bloquear(BloqueoEntradaDto(accionId, alertaId, duracion.segundos, motivo)) }.aDominio()
    }

    override suspend fun aprobar(redId: String, bloqueoId: String): Bloqueo {
        val api = api(redId)
        return conErrores { api.aprobarBloqueo(bloqueoId) }.aDominio()
    }

    override suspend fun rechazar(redId: String, bloqueoId: String, motivo: String): Bloqueo {
        val api = api(redId)
        return conErrores { api.rechazarBloqueo(bloqueoId, MotivoEntradaDto(motivo)) }.aDominio()
    }

    override suspend fun desbloquear(redId: String, bloqueoId: String, motivo: String): Bloqueo {
        val api = api(redId)
        return conErrores { api.desbloquear(bloqueoId, MotivoEntradaDto(motivo)) }.aDominio()
    }

    override suspend fun cambiarDuracion(redId: String, bloqueoId: String, duracion: DuracionBloqueo, motivo: String): Bloqueo {
        val api = api(redId)
        return conErrores { api.cambiarDuracionBloqueo(bloqueoId, DuracionEntradaDto(duracion.segundos, motivo)) }.aDominio()
    }
}
