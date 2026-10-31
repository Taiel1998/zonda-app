package ar.edu.uade.zonda.domain.fakes

import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Tema
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
    val notificaciones = MutableStateFlow(PreferenciasNotificaciones())
    val tema = MutableStateFlow(Tema.COMO_EL_TELEFONO)

    override fun observarTema(): Flow<Tema> = tema

    override suspend fun guardarTema(tema: Tema) {
        this.tema.value = tema
    }

    override fun observarAvisos(): Flow<PreferenciasAvisos> = avisos

    /** Si no es null, guardar los avisos espera hasta que la prueba lo complete, como DataStore escribiendo el archivo. */
    var esperaAlGuardarAvisos: CompletableDeferred<Unit>? = null

    override suspend fun guardarAvisos(preferencias: PreferenciasAvisos) {
        esperaAlGuardarAvisos?.await()
        avisos.value = preferencias
    }

    override fun observarNotificaciones(): Flow<PreferenciasNotificaciones> = notificaciones

    override suspend fun guardarNotificaciones(preferencias: PreferenciasNotificaciones) {
        notificaciones.value = preferencias
    }

    override fun observarPrimerInicioHecho(): Flow<Boolean> = primerInicio

    override suspend fun marcarPrimerInicioHecho() {
        primerInicio.value = true
    }

    override fun observarRecorridoHecho(): Flow<Boolean> = recorrido

    override suspend fun marcarRecorridoHecho() {
        recorrido.value = true
    }

    val recordatorios = mutableMapOf<String, Instant>()

    override suspend fun recordatoriosHasta(redId: String): Instant? = recordatorios[redId]

    override suspend fun guardarRecordatoriosHasta(redId: String, hasta: Instant) {
        recordatorios[redId] = hasta
    }
}
