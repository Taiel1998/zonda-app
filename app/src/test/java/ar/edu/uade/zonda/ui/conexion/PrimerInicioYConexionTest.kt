package ar.edu.uade.zonda.ui.conexion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.ui.components.HojaCondiciones
import ar.edu.uade.zonda.ui.primerinicio.AvisosScreen
import ar.edu.uade.zonda.ui.primerinicio.BienvenidaScreen
import ar.edu.uade.zonda.ui.primerinicio.TutorialScreen
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El primer inicio y la pantalla Conexión dibujados con Compose sobre Robolectric, sin emulador (Figma 35-213 a 50-729). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrimerInicioYConexionTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    private fun mostrar(estado: ConexionUiState, acciones: AccionesConexion = AccionesConexion()) {
        compose.setContent { ZondaTheme { ConexionContenido(estado = estado, acciones = acciones, zona = utc) } }
    }

    // ---- Primer inicio ----

    @Test
    fun `para empezar o saltear la introduccion hay que aceptar las condiciones`() {
        var empezo = false
        var omitio = false
        compose.setContent {
            ZondaTheme { BienvenidaScreen(alEmpezar = { empezo = true }, alOmitirIntroduccion = { omitio = true }) }
        }

        compose.onNodeWithText("Empezar").assertIsNotEnabled()
        compose.onNodeWithText("Ya conozco Zonda: omitir la introducción").assertIsNotEnabled()
        // Se toca la casilla, a la izquierda: el centro de la fila puede caer en el enlace de las condiciones.
        compose.onNode(isToggleable()).performScrollTo().performTouchInput { click(centerLeft + Offset(20f, 0f)) }
        compose.onNodeWithText("Empezar").assertIsEnabled().performScrollTo().performClick()
        compose.onNodeWithText("Ya conozco Zonda: omitir la introducción").performScrollTo().performClick()

        assertTrue(empezo && omitio)
    }

    @Test
    fun `la hoja de condiciones tiene los cuatro bloques del Figma y se cierra con Entendido`() {
        var cerrada = false
        compose.setContent { ZondaTheme { HojaCondiciones(alCerrar = { cerrada = true }) } }

        compose.onNodeWithText("Condiciones de uso y privacidad").assertIsDisplayed()
        compose.onNodeWithText("Qué guarda Zonda").assertIsDisplayed()
        compose.onNodeWithText("Qué sale del teléfono").assertIsDisplayed()
        compose.onNodeWithText("Cuánto tiempo").assertIsDisplayed()
        compose.onNodeWithText("Datos personales").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Entendido").performScrollTo().performClick()

        assertTrue(cerrada)
    }

    @Test
    fun `el tutorial tiene tres pasos y el ultimo lleva a configurar los avisos`() {
        var termino = false
        var omitio = false
        compose.setContent { ZondaTheme { TutorialScreen(alOmitir = { omitio = true }, alTerminar = { termino = true }) } }

        compose.onNodeWithText("Paso 1 de 3").assertIsDisplayed()
        compose.onNodeWithText("Mirá el estado de la red de un vistazo").assertIsDisplayed()
        compose.onNodeWithText("Siguiente").performScrollTo().performClick()
        compose.onNodeWithText("Paso 2 de 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Los avisos llegan a tu teléfono").assertIsDisplayed()
        compose.onNodeWithText("Siguiente").performScrollTo().performClick()
        compose.onNodeWithText("Paso 3 de 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin señal, seguís trabajando").assertIsDisplayed()
        compose.onNodeWithText("Configurar mis avisos").performScrollTo().performClick()
        assertTrue(termino)

        compose.onNodeWithText("Omitir").performScrollTo().performClick()
        assertTrue(omitio)
    }

    @Test
    fun `avisos no tiene flecha de volver y pide el permiso de notificaciones con la campana`() {
        var continuo = false
        compose.setContent { ZondaTheme { AvisosScreen(avisos = PreferenciasAvisos(), alCambiar = { _, _ -> }, alContinuar = { continuo = true }) } }

        compose.onNodeWithText("Antes de empezar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").assertDoesNotExist()
        compose.onNodeWithText("Avisos por Telegram").assertIsDisplayed()
        compose.onNodeWithText("Permitir notificaciones").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continuar").performScrollTo().performClick()

        assertTrue(continuo)
    }

    // ---- Conexión ----

    @Test
    fun `conectar se habilita con la direccion y los ocho caracteres del codigo`() {
        var conecto = false
        var estado by mutableStateOf<ConexionUiState>(ConexionUiState.Formulario())
        compose.setContent {
            ZondaTheme { ConexionContenido(estado = estado, acciones = AccionesConexion(alConectar = { conecto = true })) }
        }

        compose.onNodeWithText("Conectar").assertIsNotEnabled()
        estado = ConexionUiState.Formulario(direccion = "zonda-empresa.duckdns.org", codigo = "K7M2QX9P")
        compose.onNodeWithText("Conectar").performScrollTo().assertIsEnabled().performClick()

        assertTrue(conecto)
    }

    @Test
    fun `lo que se escribe en el codigo llega al ViewModel`() {
        var escrito = ""
        mostrar(ConexionUiState.Formulario(), AccionesConexion(alCambiarCodigo = { escrito = it }))

        compose.onNodeWithContentDescription("Código de pareo, obligatorio").performTextInput("k7m2")

        assertEquals("k7m2", escrito)
    }

    @Test
    fun `con las casillas a la vista, TalkBack no lee la etiqueta del codigo aparte ni el asterisco`() {
        mostrar(ConexionUiState.Formulario(codigo = "K7M2"))

        compose.onNodeWithContentDescription("Código de pareo, obligatorio").assertIsDisplayed()
        compose.onNodeWithText("Código de pareo *", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    @Test
    fun `que significa abre la hoja de la red de la empresa con sus tres pasos`() {
        mostrar(ConexionUiState.Formulario())

        compose.onNodeWithText("Qué significa").performClick()

        compose.onNodeWithText("Estar en la red de la empresa").assertIsDisplayed()
        compose.onNodeWithText("Conectate al wifi de la oficina, no a los datos del celular.").assertIsDisplayed()
        compose.onNodeWithText("Si no sabés cuál es el wifi, preguntale al administrador.").assertIsDisplayed()
    }

    @Test
    fun `la hoja de donde encuentro el codigo ofrece escanear el QR`() {
        var escanear = false
        mostrar(ConexionUiState.Formulario(), AccionesConexion(alEscanear = { escanear = true }))

        compose.onNodeWithText("¿Dónde encuentro el código?").performScrollTo().performClick()
        compose.onNodeWithText("El código se genera en la computadora donde está instalado Zonda.").assertIsDisplayed()
        compose.onNodeWithText("Escanear QR").performClick()

        assertTrue(escanear)
    }

    @Test
    fun `mientras conecta muestra la red y los tres pasos`() {
        mostrar(ConexionUiState.Conectando("https://zonda-empresa.duckdns.org", PasoPareo.REGISTRANDO_TELEFONO))

        compose.onNodeWithText("zonda-empresa.duckdns.org").assertIsDisplayed()
        compose.onNodeWithText("Buscando el servidor").assertIsDisplayed()
        compose.onNodeWithText("Verificando que sea el verdadero").assertIsDisplayed()
        compose.onNodeWithText("Registrando este teléfono").assertIsDisplayed()
        compose.onNodeWithText("Descargando las alertas").assertDoesNotExist()
    }

    @Test
    fun `red conectada dice como quedo registrado el telefono y cuantas alertas hay para bajar`() {
        var detalles = false
        var continuo = false
        mostrar(
            ConexionUiState.Conectada(Fixtures.red(), alertasParaDescargar = 213, nombreDelTelefono = "Celular Martín"),
            AccionesConexion(alVerDetallesTecnicos = { detalles = true }, alContinuar = { continuo = true }),
        )

        compose.onNodeWithText("Oficina").assertIsDisplayed()
        compose.onNodeWithText("zonda-equipo.duckdns.org").assertIsDisplayed()
        compose.onNodeWithText("Conexión segura").assertIsDisplayed()
        compose.onNodeWithText("Este teléfono quedó registrado como \"Celular Martín\". El código ya se usó y no sirve para otro teléfono.").assertIsDisplayed()
        compose.onNodeWithText("213 alertas de los últimos 30 días listas para descargar").assertIsDisplayed()
        compose.onNodeWithText("Detalles técnicos").performClick()
        compose.onNodeWithText("Continuar").performScrollTo().performClick()

        assertTrue(detalles && continuo)
    }

    @Test
    fun `sin servidor dice hasta cuando vale el codigo y deja corregir la direccion`() {
        var corregir = false
        var reintentar = false
        mostrar(
            ConexionUiState.Fallo("https://zonda-empresa.duckdns.org", ErrorPareo.SIN_SERVIDOR, codigoValidoHasta = Instant.parse("2026-10-12T02:19:00Z")),
            AccionesConexion(alIngresarAMano = { corregir = true }, alReintentar = { reintentar = true }),
        )

        compose.onNodeWithText("No se pudo conectar").assertIsDisplayed()
        compose.onNodeWithText("El código sigue siendo válido hasta las 02:19.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Corregir la dirección").performClick()
        compose.onNodeWithText("¿Dónde encuentro el código? Lo genera el administrador en la computadora del servidor Zonda.").performScrollTo().assertIsDisplayed()

        assertTrue(corregir && reintentar)
    }

    @Test
    fun `con el certificado invalido explica que no se envio nada y ofrece volver`() {
        var volver = false
        mostrar(ConexionUiState.Fallo("https://zonda-empresa.duckdns.org", ErrorPareo.SERVIDOR_NO_VERIFICADO), AccionesConexion(alIngresarAMano = { volver = true }))

        compose.onNodeWithText("No se pudo verificar el servidor").assertIsDisplayed()
        compose.onNodeWithText("La dirección respondió, pero su certificado no es válido para ese nombre. La app no envió el código ni ningún otro dato.").assertIsDisplayed()
        compose.onNodeWithText("Avisale al administrador. Si estás fuera de la empresa, conectate a la VPN y reintentá.").assertIsDisplayed()
        compose.onNodeWithText("Volver").performClick()

        assertTrue(volver)
    }

    @Test
    fun `con una direccion http explica que Zonda solo se conecta por HTTPS`() {
        mostrar(ConexionUiState.Fallo("http://192.168.0.10:8000", ErrorPareo.SERVIDOR_NO_VERIFICADO))

        compose.onNodeWithText("No se pudo verificar el servidor").assertIsDisplayed()
        compose.onNodeWithText("http://192.168.0.10:8000").assertIsDisplayed()
        compose.onNodeWithText("La dirección empieza con http://, sin cifrar, y Zonda solo se conecta por HTTPS. La app no envió el código ni ningún otro dato.").assertIsDisplayed()
        compose.onNodeWithText("Avisale al administrador: la dirección del código tiene que empezar con https://.").assertIsDisplayed()
        compose.onNodeWithText("Si estás fuera de la empresa", substring = true).assertDoesNotExist()
    }

    @Test
    fun `con el codigo vencido ofrece escanear otro o ingresar otro a mano`() {
        var escanear = false
        var aMano = false
        mostrar(
            ConexionUiState.Fallo("https://zonda-empresa.duckdns.org", ErrorPareo.CODIGO_VENCIDO),
            AccionesConexion(alEscanear = { escanear = true }, alIngresarAMano = { aMano = true }),
        )

        compose.onNodeWithText("El código venció o ya se usó").assertIsDisplayed()
        compose.onNodeWithText("Escanear otro código").performClick()
        compose.onNodeWithText("Ingresar otro código a mano").performClick()
        compose.onNodeWithText("El código sigue siendo válido", substring = true).assertDoesNotExist()

        assertTrue(escanear && aMano)
        assertFalse(compose.onAllNodesWithTextExists("Corregir la dirección"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(texto: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(texto)).fetchSemanticsNodes().isNotEmpty()
}
