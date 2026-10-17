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
import ar.edu.uade.zonda.domain.model.ModoNotificacion
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore de preferencias del teléfono, uno solo para toda la app. */
val Context.preferenciasZonda: DataStore<Preferences> by preferencesDataStore(name = "zonda_preferencias")

/**
 * Preferencias que son de este teléfono y no de la red: avisos de Telegram y notificaciones de Zonda por severidad, si el primer inicio
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

    /**
     * Las notificaciones de Zonda (1.0.4). Hasta la 1.0.3 había un solo interruptor, "Notificaciones del
     * sistema": si quedó apagado y todavía no se guardó nada nuevo, se lee como todo en "Sin aviso" y el
     * recordatorio apagado, así nadie empieza a recibir notificaciones que había apagado.
     */
    override fun observarNotificaciones(): Flow<PreferenciasNotificaciones> = dataStore.data.map { preferencias ->
        val porDefecto = if (preferencias[NOTIFICACIONES_ACTIVAS] == false) PreferenciasNotificaciones.NINGUNA else PreferenciasNotificaciones()
        PreferenciasNotificaciones(
            alta = preferencias[NOTIFICACION_ALTA]?.let(::modoNotificacionONull) ?: porDefecto.alta,
            media = preferencias[NOTIFICACION_MEDIA]?.let(::modoNotificacionONull) ?: porDefecto.media,
            baja = preferencias[NOTIFICACION_BAJA]?.let(::modoNotificacionONull) ?: porDefecto.baja,
            recordatorio = preferencias[RECORDATORIO_ALTAS] ?: porDefecto.recordatorio,
        )
    }

    /** Guarda las cuatro y borra el interruptor de la 1.0.3, que ya no se usa. */
    override suspend fun guardarNotificaciones(preferencias: PreferenciasNotificaciones) {
        dataStore.edit {
            it[NOTIFICACION_ALTA] = preferencias.alta.name
            it[NOTIFICACION_MEDIA] = preferencias.media.name
            it[NOTIFICACION_BAJA] = preferencias.baja.name
            it[RECORDATORIO_ALTAS] = preferencias.recordatorio
            it.remove(NOTIFICACIONES_ACTIVAS)
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

    private fun modoNotificacionONull(nombre: String): ModoNotificacion? = ModoNotificacion.entries.firstOrNull { it.name == nombre }

    private companion object {
        const val NOMBRE_ANTERIOR_SILENCIO = "VIBRACION"
        val AVISO_ALTA = stringPreferencesKey("aviso_alta")
        val AVISO_MEDIA = stringPreferencesKey("aviso_media")
        val AVISO_BAJA = stringPreferencesKey("aviso_baja")
        val PRIMER_INICIO_HECHO = booleanPreferencesKey("primer_inicio_hecho")
        val RECORRIDO_HECHO = booleanPreferencesKey("recorrido_hecho")
        /** El interruptor "Notificaciones del sistema" de la 1.0.3: solo se lee para pasarlo a las notificaciones nuevas. */
        val NOTIFICACIONES_ACTIVAS = booleanPreferencesKey("notificaciones_activas")
        val NOTIFICACION_ALTA = stringPreferencesKey("notificacion_alta")
        val NOTIFICACION_MEDIA = stringPreferencesKey("notificacion_media")
        val NOTIFICACION_BAJA = stringPreferencesKey("notificacion_baja")
        val RECORDATORIO_ALTAS = booleanPreferencesKey("recordatorio_altas")
    }
}
