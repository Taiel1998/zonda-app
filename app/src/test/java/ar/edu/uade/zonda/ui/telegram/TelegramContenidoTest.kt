package ar.edu.uade.zonda.ui.telegram

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las pantallas de Telegram dibujadas con Compose sobre Robolectric (Figma 50-942, 50-1003, 66-4205 y 66-3484). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelegramContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val sinVincular = TelegramUiState(
        red = Fixtures.red("red-1").copy(nombre = "Oficina central", telegramBot = "zonda_alertas_bot"),
        nombreDelTelefono = "Celular Martín",
    )
    private val vinculado = sinVincular.copy(red = sinVincular.red!!.copy(telegramVinculado = true))

    private fun mostrar(estado: TelegramUiState, acciones: AccionesTelegram = AccionesTelegram(), desdeAjustes: Boolean = false) {
        compose.setContent { ZondaTheme { TelegramContenido(estado = estado, acciones = acciones, desdeAjustes = desdeAjustes) } }
    }

    @Test
    fun `conectar Telegram dice como se hace y que viaja, sin flecha en el primer inicio`() {
        val tocados = mutableListOf<String>()
        mostrar(sinVincular, AccionesTelegram(alAbrirTelegram = { tocados += "abrir" }, alAhoraNo = { tocados += "ahora no" }))

        compose.onNodeWithText("Conectar Telegram").assertIsDisplayed()
        compose.onNodeWithText("Recibí los avisos por Telegram").assertIsDisplayed()
        compose.onNodeWithText("Tocá \"Abrir Telegram\": se abre el chat de @zonda_alertas_bot").assertIsDisplayed()
        compose.onNodeWithText("En Telegram, tocá \"Iniciar\"").assertIsDisplayed()
        compose.onNodeWithText("Solo la severidad, el servicio y la hora de cada alerta. Nunca direcciones IP ni nombres de equipos.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").assertDoesNotExist()

        compose.onNodeWithText("Abrir Telegram").performScrollTo().performClick()
        compose.onNodeWithText("Ahora no").performScrollTo().performClick()
        compose.onNodeWithText("Podés hacerlo después desde Ajustes → Telegram.").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("abrir", "ahora no"), tocados)
    }

    @Test
    fun `mientras pide el codigo no se puede volver a tocar Abrir Telegram`() {
        mostrar(sinVincular.copy(ocupado = true))

        compose.onNodeWithText("Abrir Telegram").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `desde Ajustes tiene flecha para volver`() {
        var volvio = false
        mostrar(sinVincular, AccionesTelegram(alVolver = { volvio = true }), desdeAjustes = true)

        compose.onNodeWithContentDescription("Volver").performClick()

        assertTrue(volvio)
    }

    @Test
    fun `ya vinculado en el primer inicio dice a que chat llegan los avisos y como avisa cada severidad`() {
        var continuo = false
        mostrar(vinculado.copy(avisos = PreferenciasAvisos(media = ModoAviso.SIN_AVISO)), AccionesTelegram(alContinuar = { continuo = true }))

        compose.onNodeWithText("Telegram conectado").assertIsDisplayed()
        compose.onNodeWithText("Los avisos de Oficina central van a llegar al chat de @zonda_alertas_bot en este teléfono.").assertIsDisplayed()
        compose.onNodeWithText("Alta con sonido · Media sin aviso · Baja sin aviso. Se cambia desde Ajustes.").assertIsDisplayed()
        compose.onNodeWithText("Continuar").performClick()
        assertTrue(continuo)
    }

    @Test
    fun `vinculado desde Ajustes muestra como avisa, el aviso de prueba y desvincular`() {
        val tocados = mutableListOf<String>()
        mostrar(
            vinculado,
            AccionesTelegram(alEnviarPrueba = { tocados += "prueba" }, alDesvincular = { tocados += "desvincular" }, alVolver = {}),
            desdeAjustes = true,
        )

        compose.onNodeWithText("Telegram").assertIsDisplayed()
        compose.onNodeWithText("Los avisos de Oficina central llegan al chat de @zonda_alertas_bot en Celular Martín.").assertIsDisplayed()
        compose.onNodeWithText("Cómo avisa").assertIsDisplayed()
        compose.onNodeWithText("Con sonido").assertIsDisplayed()
        compose.onNodeWithText("Con vibración").assertIsDisplayed()
        compose.onNodeWithText("Sin aviso").assertIsDisplayed()
        compose.onNodeWithText("Enviar un aviso de prueba").performClick()
        compose.onNodeWithText("Desvincular Telegram").performClick()
        assertEquals(listOf("prueba", "desvincular"), tocados)
    }

    @Test
    fun `los avisos inferiores se muestran y se consumen`() {
        var consumidos = 0
        mostrar(vinculado.copy(aviso = AvisoTelegram.PruebaEnviada), AccionesTelegram(alAvisoMostrado = { consumidos++ }), desdeAjustes = true)

        compose.onNodeWithText("Aviso de prueba enviado. Fijate en el chat de @zonda_alertas_bot.").assertIsDisplayed()
        assertEquals(1, consumidos)
    }

    @Test
    fun `sin el bot en el servidor lo dice`() {
        mostrar(sinVincular.copy(aviso = AvisoTelegram.Error(ErrorZonda.TelegramNoDisponible())))

        compose.onNodeWithText("El servidor de esta red todavía no tiene el bot de Telegram. Avisale al administrador.").assertIsDisplayed()
    }
}
