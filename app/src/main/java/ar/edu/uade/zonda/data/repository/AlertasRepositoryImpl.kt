package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.local.dao.AlertasDao
import ar.edu.uade.zonda.data.local.entidades.AlertaConDetalles
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.sync.SinSincronizador
import ar.edu.uade.zonda.data.sync.Sincronizador
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repositorio de alertas sobre Room. Todo lo que observa la interfaz sale de la base local;
 * la red solo la actualiza (informe, sección 14).
 *
 * Las operaciones que hablan con el servidor ([descargarDetalle] y [sincronizar]) las hace el
 * [Sincronizador]; las pruebas que solo miran la base le pasan [SinSincronizador].
 */
class AlertasRepositoryImpl(
    private val dao: AlertasDao,
    private val sincronizador: Sincronizador = SinSincronizador,
    private val reloj: () -> Instant = Instant::now,
) : AlertasRepository {

    override fun observarAlertas(redId: String, filtro: FiltroAlertas): Flow<List<Alerta>> =
        dao.observar(
            redId = redId,
            estado = filtro.estado?.name,
            severidad = filtro.severidad?.name,
            desde = filtro.periodo.desde(reloj())?.toEpochMilli(),
            servicio = filtro.servicio,
            destinoIp = filtro.destinoIp,
            metodo = filtro.metodo?.name,
        ).map { filas -> filas.map(AlertaConDetalles::aDominio) }

    override fun observarAlerta(redId: String, alertaId: String): Flow<Alerta?> =
        dao.observarUna(redId, alertaId).map { it?.aDominio() }

    override fun observarCantidad(redId: String): Flow<Int> = dao.observarCantidad(redId)

    override fun observarCantidadTotal(): Flow<Int> = dao.observarCantidadTotal()

    override fun observarContadoresSinRevisar(redId: String): Flow<ContadoresSinRevisar> =
        dao.observarConteoSinRevisar(redId).map { filas ->
            fun cantidad(severidad: Severidad) = filas.firstOrNull { it.severidad == severidad.name }?.cantidad ?: 0
            ContadoresSinRevisar(
                altas = cantidad(Severidad.ALTA),
                medias = cantidad(Severidad.MEDIA),
                bajas = cantidad(Severidad.BAJA),
            )
        }

    override suspend fun obtenerAlertasDesde(redId: String, desde: Instant): List<Alerta> =
        dao.desde(redId, desde.toEpochMilli()).map { it.aDominio() }

    override suspend fun aplicarRevisionLocal(redId: String, alertaId: String, revision: Revision): Alerta {
        val actual = dao.obtener(alertaId)
        if (actual == null || actual.alerta.redId != redId) throw ErrorZonda.NoEncontrado()
        dao.aplicarRevisionLocal(alertaId, revision.aEntidad(alertaId), EstadoRevision.de(revision.resultado).name)
        return dao.obtener(alertaId)!!.aDominio()
    }

    override suspend fun restaurarEstadoDelServidor(redId: String, alertaId: String) {
        dao.restaurarEstadoDelServidor(alertaId)
    }

    override suspend fun descargarDetalle(redId: String, alertaId: String): Alerta =
        sincronizador.descargarDetalle(redId, alertaId)

    override suspend fun sincronizar(redId: String): ResultadoSincronizacion =
        sincronizador.sincronizar(redId)

    override suspend fun borrarDeRed(redId: String) = dao.borrarDeRed(redId)

    /**
     * Guarda alertas tal como llegaron del servidor. Lo usa la carga de datos de ejemplo y las pruebas.
     * El historial del servidor reemplaza al guardado; una revisión local pendiente se conserva.
     */
    suspend fun guardarDelServidor(alertas: List<Alerta>) {
        val revisiones = alertas.associate { alerta ->
            alerta.id to alerta.historial
                .filter { it.origen == OrigenRevision.SERVIDOR }
                .map { it.aEntidad(alerta.id) }
        }
        dao.guardarDelServidor(alertas.map { it.aEntidad() }, revisiones)
    }

    /** Borra las alertas anteriores a una fecha (caché de 30 días, RNF03). Devuelve cuántas borró. */
    suspend fun purgarAnterioresA(redId: String, antesDe: Instant): Int =
        dao.purgarAnterioresA(redId, antesDe.toEpochMilli())
}
