package ar.edu.uade.zonda.domain.fakes

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroServicio
import ar.edu.uade.zonda.domain.model.OpcionesDeFiltro
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Repositorio de alertas en memoria para probar los casos de uso sin Room ni red.
 * Aplica los filtros de estado, severidad, servicio, destino y método; el período no (no hay reloj):
 * eso lo prueba AlertasRepositoryImplTest contra la base.
 */
class FakeAlertasRepository(iniciales: List<Alerta> = emptyList()) : AlertasRepository {

    private val alertas = MutableStateFlow(iniciales)

    /** Última versión de cada alerta "tal como llegó del servidor", para restaurar al deshacer. */
    private val delServidor = iniciales.associateBy { it.id }.toMutableMap()

    /** Lo que devuelve [descargarDetalle], por id. */
    var alertasRemotas: Map<String, Alerta> = emptyMap()

    var resultadoSincronizacion = ResultadoSincronizacion(0, 0, 0, 0, 0, Instant.EPOCH)
    var errorAlSincronizar: ErrorZonda? = null
    val sincronizaciones = mutableListOf<String>()

    /** Si no es null, la sincronización espera hasta que la prueba lo complete (para ver qué pasa mientras corre). */
    var esperaAlSincronizar: CompletableDeferred<Unit>? = null

    /** La primera descarga en curso, para la tarjeta de Estado. */
    val progresoDescarga = MutableStateFlow<ProgresoDescarga?>(null)

    val todas: List<Alerta>
        get() = alertas.value

    fun agregar(vararg nuevas: Alerta) {
        alertas.value = alertas.value + nuevas
        nuevas.forEach { delServidor[it.id] = it }
    }

    override fun observarAlertas(redId: String, filtro: FiltroAlertas): Flow<List<Alerta>> =
        alertas.map { lista ->
            lista.filter { it.redId == redId && filtro.acepta(it) }.sortedByDescending { it.inicio }
        }

    override fun observarOpcionesDeFiltro(redId: String): Flow<OpcionesDeFiltro> =
        alertas.map { lista ->
            val deLaRed = lista.filter { it.redId == redId }
            OpcionesDeFiltro(
                servicios = deLaRed.groupBy { it.servicio }.entries
                    .sortedWith(compareByDescending<Map.Entry<String, List<Alerta>>> { it.value.size }.thenBy { it.key })
                    .map { it.key },
                destinos = deLaRed.groupBy { it.destino.ip }.entries
                    .sortedWith(compareByDescending<Map.Entry<String, List<Alerta>>> { it.value.size }.thenBy { it.key })
                    .map { (ip, deEseDestino) -> Equipo(ip, deEseDestino.firstNotNullOfOrNull { it.destino.nombre }) },
            )
        }

    override fun observarAlerta(redId: String, alertaId: String): Flow<Alerta?> =
        alertas.map { lista -> lista.firstOrNull { it.redId == redId && it.id == alertaId } }

    override fun observarCantidad(redId: String): Flow<Int> = alertas.map { lista -> lista.count { it.redId == redId } }

    override fun observarCantidadTotal(): Flow<Int> = alertas.map { it.size }

    override fun observarContadoresSinRevisar(redId: String): Flow<ContadoresSinRevisar> =
        alertas.map { lista ->
            val sinRevisar = lista.filter { it.redId == redId && it.sinRevisar }
            ContadoresSinRevisar(
                altas = sinRevisar.count { it.severidad == Severidad.ALTA },
                medias = sinRevisar.count { it.severidad == Severidad.MEDIA },
                bajas = sinRevisar.count { it.severidad == Severidad.BAJA },
            )
        }

    override suspend fun aplicarRevisionLocal(redId: String, alertaId: String, revision: Revision): Alerta {
        val actual = alertas.value.firstOrNull { it.redId == redId && it.id == alertaId } ?: throw ErrorZonda.NoEncontrado()
        // Igual que la base real: una revisión local reemplaza a la anterior que no llegó al servidor.
        val historial = actual.historial.filter { it.origen != OrigenRevision.ESTE_TELEFONO } + revision
        val nueva = actual.copy(
            estadoRevision = EstadoRevision.de(revision.resultado),
            revisionActual = revision,
            historial = historial,
            envio = EstadoEnvio.PENDIENTE,
        )
        reemplazar(nueva)
        return nueva
    }

    override suspend fun restaurarEstadoDelServidor(redId: String, alertaId: String) {
        delServidor[alertaId]?.let { reemplazar(it) }
    }

    override suspend fun descargarDetalle(redId: String, alertaId: String): Alerta {
        val remota = alertasRemotas[alertaId] ?: throw ErrorZonda.NoEncontrado()
        agregar(remota)
        return remota
    }

    override suspend fun sincronizar(redId: String): ResultadoSincronizacion {
        sincronizaciones += redId
        esperaAlSincronizar?.await()
        errorAlSincronizar?.let { throw it }
        return resultadoSincronizacion
    }

    override fun observarProgresoDescarga(redId: String): Flow<ProgresoDescarga?> =
        progresoDescarga.map { progreso -> progreso?.takeIf { it.redId == redId } }

    override suspend fun borrarDeRed(redId: String) {
        alertas.value = alertas.value.filter { it.redId != redId }
    }

    private fun reemplazar(nueva: Alerta) {
        alertas.value = alertas.value.map { if (it.id == nueva.id) nueva else it }
    }

    private fun FiltroAlertas.acepta(alerta: Alerta): Boolean =
        (estado.estados?.contains(alerta.estadoRevision) ?: true) &&
            (severidades.isEmpty() || alerta.severidad in severidades) &&
            aceptaServicio(alerta.servicio) &&
            (destinoIp == null || alerta.destino.ip == destinoIp) &&
            (metodo == null || metodo in alerta.metodos)

    private fun FiltroAlertas.aceptaServicio(nombre: String): Boolean = when (val elegido = servicio) {
        null -> true
        is FiltroServicio.Uno -> nombre == elegido.nombre
        is FiltroServicio.Otros -> nombre !in elegido.conChip
    }
}
