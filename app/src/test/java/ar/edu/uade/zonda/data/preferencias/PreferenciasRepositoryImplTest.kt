package ar.edu.uade.zonda.data.preferencias

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.ModoNotificacion
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Tema
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DataStore sobre un archivo temporal, sin Android. */
class PreferenciasRepositoryImplTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    @Test
    fun `los avisos arrancan con los valores por defecto y se guardan`() = runTest {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = PreferenciasRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta.root, "zonda.preferences_pb") },
        )

        assertEquals(PreferenciasAvisos(), repo.observarAvisos().first())

        repo.guardarAvisos(PreferenciasAvisos(alta = ModoAviso.SILENCIO, media = ModoAviso.SIN_AVISO, baja = ModoAviso.SONIDO))

        assertEquals(PreferenciasAvisos(ModoAviso.SILENCIO, ModoAviso.SIN_AVISO, ModoAviso.SONIDO), repo.observarAvisos().first())
        alcance.cancel()
    }

    @Test
    fun `un aviso con vibracion guardado por la 1_0_2 se lee como en silencio`() = runTest {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val almacen = PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta.root, "anterior.preferences_pb") }
        almacen.edit { it[stringPreferencesKey("aviso_media")] = "VIBRACION" }

        val avisos = PreferenciasRepositoryImpl(almacen).observarAvisos().first()

        assertEquals(ModoAviso.SILENCIO, avisos.media)
        alcance.cancel()
    }

    @Test
    fun `las notificaciones de Zonda arrancan con los valores de fabrica y se guardan`() = runTest {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = PreferenciasRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta.root, "notificaciones.preferences_pb") },
        )

        val deFabrica = repo.observarNotificaciones().first()
        assertEquals(ModoNotificacion.SONIDO_Y_VIBRACION, deFabrica.alta)
        assertEquals(ModoNotificacion.SOLO_VIBRACION, deFabrica.media)
        assertEquals(ModoNotificacion.SIN_AVISO, deFabrica.baja)
        assertTrue(deFabrica.recordatorio)

        val elegidas = PreferenciasNotificaciones(ModoNotificacion.EN_SILENCIO, ModoNotificacion.SIN_AVISO, ModoNotificacion.SOLO_SONIDO, recordatorio = false)
        repo.guardarNotificaciones(elegidas)

        assertEquals(elegidas, repo.observarNotificaciones().first())
        alcance.cancel()
    }

    @Test
    fun `con Notificaciones del sistema apagado en la 1_0_3, todo queda en Sin aviso y el recordatorio apagado`() = runTest {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val almacen = PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta.root, "apagadas.preferences_pb") }
        almacen.edit { it[booleanPreferencesKey("notificaciones_activas")] = false }
        val repo = PreferenciasRepositoryImpl(almacen)

        assertEquals(PreferenciasNotificaciones.NINGUNA, repo.observarNotificaciones().first())

        // Al guardar lo nuevo, el interruptor viejo deja de contar.
        repo.guardarNotificaciones(PreferenciasNotificaciones())
        assertEquals(PreferenciasNotificaciones(), repo.observarNotificaciones().first())
        assertFalse(almacen.data.first().contains(booleanPreferencesKey("notificaciones_activas")))
        alcance.cancel()
    }

    @Test
    fun `con Notificaciones del sistema prendido en la 1_0_3 quedan los valores de fabrica`() = runTest {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val almacen = PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta.root, "prendidas.preferences_pb") }
        almacen.edit { it[booleanPreferencesKey("notificaciones_activas")] = true }

        assertEquals(PreferenciasNotificaciones(), PreferenciasRepositoryImpl(almacen).observarNotificaciones().first())
        alcance.cancel()
    }

    @Test
    fun `el primer inicio y el recorrido se marcan una sola vez`() = runTest {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = PreferenciasRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta.root, "inicio.preferences_pb") },
        )

        assertFalse(repo.observarPrimerInicioHecho().first())
        assertFalse(repo.observarRecorridoHecho().first())

        repo.marcarPrimerInicioHecho()
        repo.marcarRecorridoHecho()

        assertTrue(repo.observarPrimerInicioHecho().first())
        assertTrue(repo.observarRecorridoHecho().first())
        alcance.cancel()
    }

    @Test
    fun `el tema arranca como el telefono y se guarda`() = runTest {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = PreferenciasRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta.root, "tema.preferences_pb") },
        )

        assertEquals(Tema.COMO_EL_TELEFONO, repo.observarTema().first())

        repo.guardarTema(Tema.OSCURO)

        assertEquals(Tema.OSCURO, repo.observarTema().first())
        alcance.cancel()
    }
}
