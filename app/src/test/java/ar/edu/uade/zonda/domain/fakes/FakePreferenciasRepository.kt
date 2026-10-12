package ar.edu.uade.zonda.domain.fakes

import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Preferencias del teléfono en memoria para las pruebas; la real usa DataStore. */
class FakePreferenciasRepository(
    primerInicioHecho: Boolean = false,
    recorridoHecho: Boolean = false,
) : PreferenciasRepository {

    val avisos = MutableStateFlow(PreferenciasAvisos())
    val primerInicio = MutableStateFlow(primerInicioHecho)
    val recorrido = MutableStateFlow(recorridoHecho)

    override fun observarAvisos(): Flow<PreferenciasAvisos> = avisos

    override suspend fun guardarAvisos(preferencias: PreferenciasAvisos) {
        avisos.value = preferencias
    }

    override fun observarPrimerInicioHecho(): Flow<Boolean> = primerInicio

    override suspend fun marcarPrimerInicioHecho() {
        primerInicio.value = true
    }

    override fun observarRecorridoHecho(): Flow<Boolean> = recorrido

    override suspend fun marcarRecorridoHecho() {
        recorrido.value = true
    }
}
