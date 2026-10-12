package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.local.dao.AccionesPendientesDao
import ar.edu.uade.zonda.data.local.entidades.AccionPendienteEntity
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Cola de acciones pendientes sobre Room. */
class AccionesPendientesRepositoryImpl(
    private val dao: AccionesPendientesDao,
) : AccionesPendientesRepository {

    override fun observar(redId: String): Flow<List<AccionPendiente>> =
        dao.observar(redId).map { filas -> filas.map(AccionPendienteEntity::aDominio) }

    override suspend fun deAlerta(redId: String, alertaId: String): AccionPendiente? =
        dao.deAlerta(redId, alertaId)?.aDominio()

    override suspend fun encolar(accion: AccionPendiente) = dao.encolar(accion.aEntidad())

    override suspend fun quitar(accionId: String) = dao.borrar(accionId)

    override suspend fun borrarDeRed(redId: String) = dao.borrarDeRed(redId)

    /** Lo usa la sincronización (fase 4) para marcar reintentos, errores y conflictos. */
    suspend fun actualizarEnvio(accionId: String, estado: EstadoEnvio, intentos: Int, ultimoError: String?) =
        dao.actualizarEnvio(accionId, estado.name, intentos, ultimoError)

    suspend fun listar(redId: String): List<AccionPendiente> = dao.listar(redId).map { it.aDominio() }
}
