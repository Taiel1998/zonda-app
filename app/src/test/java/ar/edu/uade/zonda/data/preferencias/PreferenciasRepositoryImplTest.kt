package ar.edu.uade.zonda.data.preferencias

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
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
}
