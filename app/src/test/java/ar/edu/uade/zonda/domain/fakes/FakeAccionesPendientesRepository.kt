package ar.edu.uade.zonda.domain.fakes

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Cola de acciones en memoria para las pruebas. */
class FakeAccionesPendientesRepository : AccionesPendientesRepository {

    private val acciones = MutableStateFlow<List<AccionPendiente>>(emptyList())

    val todas: List<AccionPendiente>
        get() = acciones.value

    override fun observar(redId: String): Flow<List<AccionPendiente>> =
        acciones.map { lista -> lista.filter { it.redId == redId }.sortedBy { it.creadaEn } }

    override suspend fun deAlerta(redId: String, alertaId: String): AccionPendiente? =
        acciones.value.firstOrNull { it.redId == redId && it.alertaId == alertaId }

    override suspend fun encolar(accion: AccionPendiente) {
        val sinLaAnterior = acciones.value.filterNot {
            accion.alertaId != null && it.redId == accion.redId && it.alertaId == accion.alertaId
        }
        acciones.value = sinLaAnterior + accion
    }

    override suspend fun quitar(accionId: String) {
        acciones.value = acciones.value.filter { it.id != accionId }
    }

    override suspend fun borrarDeRed(redId: String) {
        acciones.value = acciones.value.filter { it.redId != redId }
    }
}

/** Conectividad controlada desde el test. */
class FakeConectividad(conectadoAlInicio: Boolean = true) : Conectividad {

    val estado = MutableStateFlow(conectadoAlInicio)

    override val conectado: Flow<Boolean>
        get() = estado

    override suspend fun hayConexion(): Boolean = estado.value
}

/** Ids previsibles: accion-1, accion-2, ... */
class GeneradorDeIdsFijo : GeneradorDeIds {
    private var contador = 0

    override fun nuevo(): String = "accion-${++contador}"
}
