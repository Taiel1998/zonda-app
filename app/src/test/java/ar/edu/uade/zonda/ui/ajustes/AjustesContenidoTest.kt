package ar.edu.uade.zonda.ui.ajustes

import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ajustes dibujada con Compose sobre Robolectric (Figma 4-66, 51-1137, 51-1314, 4-130, 51-1451 y 66-3906). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AjustesContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")
    private val red = Fixtures.red("red-1")

    private val estado = AjustesUiState(
        redes = listOf(RedConEstado(red, ContadoresSinRevisar(altas = 3, medias = 0, bajas = 0), alertasGuardadas = 213)),
        redActiva = red,
        alertasDeLaRedActiva = 213,
        alertasGuardadas = 213,
        nombreDelTelefono = "Celular Martín",
    )

    private fun mostrar(acciones: AccionesAjustes = AccionesAjustes(), estado: AjustesUiState = this.estado) {
        compose.setContent {
            ZondaTheme { AjustesContenido(estado = estado, acciones = acciones, zona = utc, ahora = Fixtures.AHORA) }
        }
    }

    @Test
    fun `muestra la red, Telegram sin conectar, los avisos y la ultima sincronizacion`() {
        var telegram = false
        mostrar(AccionesAjustes(alConectarTelegram = { telegram = true }))

        compose.onNodeWithText("Oficina").assertIsDisplayed()
        compose.onNodeWithText("zonda-equipo.duckdns.org · 1 red pareada").assertIsDisplayed()
        compose.onNodeWithText("Telegram sin conectar").assertIsDisplayed()
        compose.onNodeWithText("Alta con sonido · Media con vibración · Baja sin aviso").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Permitidas · avisan cuando la sincronización trae alertas altas nuevas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hoy 12:00 · 213 alertas en el teléfono").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Conectar Telegram").performScrollTo().performClick()
        assertTrue(telegram)
    }

    @Test
    fun `el interruptor apaga las notificaciones del sistema`() {
        var activas: Boolean? = null
        mostrar(AccionesAjustes(alCambiarNotificaciones = { activas = it }))

        compose.onNodeWithText("Notificaciones del sistema").performScrollTo().performClick()

        assertEquals(false, activas)
    }

    @Test
    @Config(sdk = [32])
    fun `sin permiso del sistema la fila del interruptor pasa a la tarjeta ambar con Abrir ajustes`() {
        val gestor = ApplicationProvider.getApplicationContext<Context>().getSystemService(NotificationManager::class.java)
        shadowOf(gestor).setNotificationsEnabled(false)
        mostrar()

        compose.onNodeWithText("Notificaciones desactivadas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Abrir ajustes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lleva directo a las notificaciones de Zonda en el sistema").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notificaciones del sistema").assertDoesNotExist()
    }

    @Test
    fun `borrar datos locales pide confirmar en una hoja y recien ahi borra`() {
        var borrados = false
        mostrar(AccionesAjustes(alBorrarDatos = { borrados = true }))

        compose.onNodeWithText("Borrar datos locales").performScrollTo().performClick()
        compose.onNodeWithText("¿Borrar los datos de este teléfono?").assertIsDisplayed()
        compose.onNodeWithText(
            "Se borran las 213 alertas guardadas y las acciones que todavía no se enviaron. " +
                "El servidor no cambia: las alertas se vuelven a descargar al sincronizar.",
        ).assertIsDisplayed()
        assertFalse(borrados)

        compose.onNodeWithText("Borrar datos").performClick()
        assertTrue(borrados)
    }

    @Test
    fun `quitar este dispositivo dice a que telefono y red afecta antes de confirmar`() {
        var quitado = false
        mostrar(AccionesAjustes(alQuitarDispositivo = { quitado = true }))

        compose.onNodeWithText("Quitar este dispositivo").performScrollTo().performClick()
        compose.onNodeWithText("¿Quitar este dispositivo?").assertIsDisplayed()
        compose.onNodeWithText("Celular Martín · Oficina · 213 alertas guardadas").assertIsDisplayed()

        compose.onNodeWithText("Quitar dispositivo").performClick()
        assertTrue(quitado)
    }

    @Test
    fun `el aviso inferior se ve aunque el ViewModel lo consuma enseguida`() {
        var actual by mutableStateOf(estado.copy(aviso = AvisoAjustes.Sincronizado(nuevas = 3)))
        compose.setContent {
            ZondaTheme {
                AjustesContenido(
                    estado = actual,
                    acciones = AccionesAjustes(alAvisoMostrado = { actual = actual.copy(aviso = null) }),
                    zona = utc,
                    ahora = Fixtures.AHORA,
                )
            }
        }

        compose.onNodeWithText("Sincronizado: llegaron 3 alertas nuevas.").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
    }
}
