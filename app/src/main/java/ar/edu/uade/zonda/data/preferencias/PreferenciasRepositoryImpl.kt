package ar.edu.uade.zonda.data.preferencias

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore de preferencias del teléfono, uno solo para toda la app. */
val Context.preferenciasZonda: DataStore<Preferences> by preferencesDataStore(name = "zonda_preferencias")

/**
 * Preferencias que son de este teléfono y no de la red: avisos por severidad, si el primer inicio
 * y el recorrido ya se hicieron y hasta dónde se recordaron las altas de cada red. DataStore reemplaza
 * a SharedPreferences con lectura reactiva (Flow).
 */
class PreferenciasRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : PreferenciasRepository {

    override fun observarAvisos(): Flow<PreferenciasAvisos> = dataStore.data.map { preferencias ->
        val porDefecto = PreferenciasAvisos()
        PreferenciasAvisos(
            alta = preferencias[AVISO_ALTA]?.let(::modoONull) ?: porDefecto.alta,
            media = preferencias[AVISO_MEDIA]?.let(::modoONull) ?: porDefecto.media,
            baja = preferencias[AVISO_BAJA]?.let(::modoONull) ?: porDefecto.baja,
        )
    }

    override suspend fun guardarAvisos(preferencias: PreferenciasAvisos) {
        dataStore.edit {
            it[AVISO_ALTA] = preferencias.alta.name
            it[AVISO_MEDIA] = preferencias.media.name
            it[AVISO_BAJA] = preferencias.baja.name
        }
    }

    override fun observarNotificacionesActivas(): Flow<Boolean> = dataStore.data.map { it[NOTIFICACIONES_ACTIVAS] ?: true }

    override suspend fun guardarNotificacionesActivas(activas: Boolean) {
        dataStore.edit { it[NOTIFICACIONES_ACTIVAS] = activas }
    }

    override fun observarPrimerInicioHecho(): Flow<Boolean> = dataStore.data.map { it[PRIMER_INICIO_HECHO] ?: false }

    override suspend fun marcarPrimerInicioHecho() {
        dataStore.edit { it[PRIMER_INICIO_HECHO] = true }
    }

    override fun observarRecorridoHecho(): Flow<Boolean> = dataStore.data.map { it[RECORRIDO_HECHO] ?: false }

    override suspend fun marcarRecorridoHecho() {
        dataStore.edit { it[RECORRIDO_HECHO] = true }
    }

    override suspend fun recordatoriosHasta(redId: String): Instant? =
        dataStore.data.first()[recordatoriosDe(redId)]?.let(Instant::ofEpochMilli)

    override suspend fun guardarRecordatoriosHasta(redId: String, hasta: Instant) {
        dataStore.edit { it[recordatoriosDe(redId)] = hasta.toEpochMilli() }
    }

    /** Una clave por red: "recordatorios_hasta_<id de la red>". */
    private fun recordatoriosDe(redId: String) = longPreferencesKey("recordatorios_hasta_$redId")

    /** Hasta la 1.0.2 el mensaje sin sonido se guardaba como "VIBRACION" (cambio 6): se lee como [ModoAviso.SILENCIO]. */
    private fun modoONull(nombre: String): ModoAviso? =
        if (nombre == NOMBRE_ANTERIOR_SILENCIO) ModoAviso.SILENCIO else ModoAviso.entries.firstOrNull { it.name == nombre }

    private companion object {
        const val NOMBRE_ANTERIOR_SILENCIO = "VIBRACION"
        val AVISO_ALTA = stringPreferencesKey("aviso_alta")
        val AVISO_MEDIA = stringPreferencesKey("aviso_media")
        val AVISO_BAJA = stringPreferencesKey("aviso_baja")
        val PRIMER_INICIO_HECHO = booleanPreferencesKey("primer_inicio_hecho")
        val RECORRIDO_HECHO = booleanPreferencesKey("recorrido_hecho")
        val NOTIFICACIONES_ACTIVAS = booleanPreferencesKey("notificaciones_activas")
    }
}
