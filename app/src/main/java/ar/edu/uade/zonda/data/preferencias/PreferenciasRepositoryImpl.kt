package ar.edu.uade.zonda.data.preferencias

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore de preferencias del teléfono, uno solo para toda la app. */
val Context.preferenciasZonda: DataStore<Preferences> by preferencesDataStore(name = "zonda_preferencias")

/**
 * Preferencias que son de este teléfono y no de la red: avisos por severidad y si el primer inicio
 * y el recorrido ya se hicieron. DataStore reemplaza a SharedPreferences con lectura reactiva (Flow).
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

    override fun observarPrimerInicioHecho(): Flow<Boolean> = dataStore.data.map { it[PRIMER_INICIO_HECHO] ?: false }

    override suspend fun marcarPrimerInicioHecho() {
        dataStore.edit { it[PRIMER_INICIO_HECHO] = true }
    }

    override fun observarRecorridoHecho(): Flow<Boolean> = dataStore.data.map { it[RECORRIDO_HECHO] ?: false }

    override suspend fun marcarRecorridoHecho() {
        dataStore.edit { it[RECORRIDO_HECHO] = true }
    }

    private fun modoONull(nombre: String): ModoAviso? = ModoAviso.entries.firstOrNull { it.name == nombre }

    private companion object {
        val AVISO_ALTA = stringPreferencesKey("aviso_alta")
        val AVISO_MEDIA = stringPreferencesKey("aviso_media")
        val AVISO_BAJA = stringPreferencesKey("aviso_baja")
        val PRIMER_INICIO_HECHO = booleanPreferencesKey("primer_inicio_hecho")
        val RECORRIDO_HECHO = booleanPreferencesKey("recorrido_hecho")
    }
}
