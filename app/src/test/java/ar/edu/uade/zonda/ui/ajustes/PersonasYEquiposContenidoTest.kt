package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.CodigoPersona
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.TipoCambio
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
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

/** Ajustes → Personas, el código, Equipos y el Registro de cambios dibujados con Compose (Figma 468-2454 a 468-3283). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonasYEquiposContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")
    private val red = Fixtures.red("red-1").copy(nombre = "Oficina central")

    private val taiel = Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, Fixtures.AHORA.minus(Duration.ofMinutes(30)), vos = true)
    private val meli = Persona("d-3", "Meli", "Galaxy A54", Perfil.ADMINISTRADOR, Fixtures.AHORA.minus(Duration.ofDays(1)), vos = false)
    private val laura = Persona("d-2", "Laura", "Teléfono de Laura", Perfil.ANALISTA, Fixtures.AHORA.minus(Duration.ofDays(3)), vos = false)

    // Las computadoras con el tablero web; la primera se llamaba solo "Tablero web".
    private val chrome = Persona(
        "d-7", null, "Tablero web · Chrome en Windows", Perfil.ADMINISTRADOR, Fixtures.AHORA.minus(Duration.ofMinutes(30)), vos = false, navegador = true,
    )
    private val primerTablero = Persona("d-8", "Tablero web", "Tablero web", Perfil.ANALISTA, Fixtures.AHORA.minus(Duration.ofDays(2)), vos = false, navegador = true)

    private val equipos = listOf(
        EquipoDeLaRed("192.168.20.20", "srv-sueldos", Importancia.CRITICO),
        EquipoDeLaRed("192.168.20.21", "srv-facturacion", Importancia.CRITICO),
        EquipoDeLaRed("192.168.30.80", "impresora-2", Importancia.POCO_IMPORTANTE),
        EquipoDeLaRed("192.168.30.50", "pc-rrhh", Importancia.NORMAL),
    )

    @Test
    fun `ajustes muestra personas, equipos y registro con su resumen`() {
        val estado = AjustesUiState(
            redes = listOf(RedConEstado(red, ContadoresSinRevisar(0, 0, 0), alertasGuardadas = 0)),
            redActiva = red,
            personas = listOf(taiel, meli, laura),
            equipos = equipos,
        )
        var abiertas = ""
        compose.setContent {
            ZondaTheme {
                AjustesContenido(
                    estado = estado,
                    acciones = AccionesAjustes(
                        alAbrirPersonas = { abiertas += "personas," },
                        alAbrirEquipos = { abiertas += "equipos," },
                        alAbrirRegistro = { abiertas += "registro" },
                    ),
                    zona = utc,
                    ahora = Fixtures.AHORA,
                )
            }
        }

        compose.onNodeWithText("3 personas · sos administrador").performClick()
        compose.onNodeWithText("2 críticos · 1 poco importante").performClick()
        compose.onNodeWithText("Quién cambió la configuración y por qué").performClick()
        assertEquals("personas,equipos,registro", abiertas)
        compose.onNodeWithText("Qué ataques son altos, medios o bajos").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `ajustes cuenta solo a las personas y los navegadores van en su fila`() {
        val estado = AjustesUiState(
            redes = listOf(RedConEstado(red, ContadoresSinRevisar(0, 0, 0), alertasGuardadas = 0)),
            redActiva = red,
            personas = listOf(taiel, meli, laura, chrome, primerTablero),
            equipos = equipos,
        )
        var abiertas = ""
        compose.setContent {
            ZondaTheme {
                AjustesContenido(
                    estado = estado,
                    acciones = AccionesAjustes(alAbrirPersonas = { abiertas += "personas," }, alAbrirNavegadores = { abiertas += "navegadores" }),
                    zona = utc,
                    ahora = Fixtures.AHORA,
                )
            }
        }

        compose.onNodeWithText("3 personas · sos administrador").performClick()
        compose.onNodeWithText("2 computadoras con el tablero web").performScrollTo().performClick()
        assertEquals("personas,navegadores", abiertas)
    }

    @Test
    fun `navegadores muestra cada computadora y el administrador le quita el acceso`() {
        val quitados = mutableListOf<Persona>()
        val estado = PersonasUiState(red = red, personas = listOf(taiel), navegadores = listOf(chrome, primerTablero))
        compose.setContent {
            ZondaTheme { NavegadoresContenido(estado = estado, alVolver = {}, alQuitar = { quitados += it }, zona = utc, ahora = Fixtures.AHORA) }
        }

        compose.onNodeWithText(
            "Las computadoras que abren el tablero web de Oficina central. Cada una entra con su propio código, como un teléfono.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Chrome en Windows").assertIsDisplayed()
        compose.onNodeWithText("Administrador · hoy 11:30").assertIsDisplayed()
        // La primera se llamaba solo "Tablero web".
        compose.onNodeWithText("Navegador").assertIsDisplayed()
        compose.onNodeWithText("Analista · hace 2 días").assertIsDisplayed()
        compose.onNodeWithText("Chrome en Windows").performClick()
        compose.onNodeWithText("¿Quitarle el acceso a Chrome en Windows?").assertIsDisplayed()
        compose.onNodeWithText("Ese navegador deja de ver el tablero y no puede volver a entrar sin un código nuevo.").assertIsDisplayed()
        compose.onNodeWithText("Quitar acceso").performClick()

        assertEquals(listOf(chrome), quitados)
    }

    @Test
    fun `la analista ve los navegadores sin poder quitarles el acceso`() {
        var quitar = false
        val estado = PersonasUiState(red = red.copy(perfil = Perfil.ANALISTA), personas = listOf(laura.copy(vos = true)), navegadores = listOf(chrome))
        compose.setContent {
            ZondaTheme { NavegadoresContenido(estado = estado, alVolver = {}, alQuitar = { quitar = true }, zona = utc, ahora = Fixtures.AHORA) }
        }

        compose.onNodeWithText("Chrome en Windows").performClick()
        compose.onNodeWithText("¿Quitarle el acceso a Chrome en Windows?").assertDoesNotExist()
        assertFalse(quitar)
    }

    @Test
    fun `la analista ve que las severidades las cambia un administrador`() {
        val analista = red.copy(perfil = Perfil.ANALISTA)
        val estado = AjustesUiState(redActiva = analista, personas = listOf(taiel, laura.copy(vos = true)), equipos = equipos.map { it.copy(importancia = Importancia.NORMAL) })
        compose.setContent { ZondaTheme { AjustesContenido(estado = estado, acciones = AccionesAjustes(), zona = utc, ahora = Fixtures.AHORA) } }

        compose.onNodeWithText("2 personas · sos analista").assertIsDisplayed()
        compose.onNodeWithText("Todos normales").assertIsDisplayed()
        compose.onNodeWithText("La cambia un administrador").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `personas del administrador con su detalle y Agregar persona`() {
        var agregar = false
        val estado = PersonasUiState(red = red, personas = listOf(taiel, meli, laura))
        compose.setContent {
            ZondaTheme { PersonasContenido(estado = estado, acciones = AccionesPersonas(alAgregar = { agregar = true }), zona = utc, ahora = Fixtures.AHORA) }
        }

        compose.onNodeWithText("Quiénes usan Zonda en Oficina central. Los administradores cambian la configuración de la red y suman gente.").assertIsDisplayed()
        compose.onNodeWithText("Taiel (vos)").assertIsDisplayed()
        compose.onNodeWithText("Administrador · moto g(9) plus · hoy 11:30").assertIsDisplayed()
        compose.onNodeWithText("Administrador · Galaxy A54 · ayer 12:00").assertIsDisplayed()
        compose.onNodeWithText("Analista · Teléfono de Laura · hace 3 días").assertIsDisplayed()
        compose.onNodeWithText("Agregar persona").performClick()
        assertTrue(agregar)
    }

    @Test
    fun `la analista no tiene Agregar persona`() {
        val estado = PersonasUiState(red = red.copy(perfil = Perfil.ANALISTA), personas = listOf(laura.copy(vos = true), taiel.copy(vos = false)))
        compose.setContent { ZondaTheme { PersonasContenido(estado = estado, acciones = AccionesPersonas(), zona = utc, ahora = Fixtures.AHORA) } }

        compose.onNodeWithText("Laura (vos)").assertIsDisplayed()
        compose.onNodeWithText("Agregar persona").assertDoesNotExist()
    }

    @Test
    fun `la hoja de una analista ofrece hacerla administrador o quitarla`() {
        var perfil: Perfil? = null
        var quitar = false
        compose.setContent {
            ZondaTheme {
                Column {
                    ContenidoHojaPersona(laura, Fixtures.AHORA, utc, alCambiarPerfil = { perfil = it }, alQuitar = { quitar = true })
                }
            }
        }

        compose.onNodeWithText("Analista · Teléfono de Laura · hace 3 días").assertIsDisplayed()
        compose.onNodeWithText("Hacer administrador").performClick()
        compose.onNodeWithText("Quitar del equipo").performClick()
        assertEquals(Perfil.ADMINISTRADOR, perfil)
        assertTrue(quitar)
    }

    @Test
    fun `agregar persona sin nombre lo pide y con el error del servidor lo dice`() {
        compose.setContent {
            ZondaTheme {
                Column {
                    ContenidoHojaAgregarPersona(
                        FormularioPersona(faltaNombre = true, error = ar.edu.uade.zonda.domain.model.ErrorZonda.SinConexion()),
                        AccionesPersonas(),
                    )
                }
            }
        }

        compose.onNodeWithText("Escribí el nombre de la persona.").assertIsDisplayed()
        // Sin red dice qué pasó con el código, no el texto general de los datos guardados.
        compose.onNodeWithText("Sin conexión: el código no se generó.").assertIsDisplayed()
        compose.onNodeWithText("Revisa las alertas, pide bloqueos y elige sus avisos.").assertIsDisplayed()
        compose.onNodeWithText("Además bloquea, cambia las severidades y suma o quita gente.").assertIsDisplayed()
        compose.onNodeWithText("Generar código").assertIsDisplayed()
    }

    @Test
    fun `el codigo muestra el QR, el texto y cuanto le queda`() {
        val codigo = CodigoPersona("K7M2-QX9P", Fixtures.AHORA.plus(Duration.ofSeconds(592)), "zonda:https://zonda-equipo.duckdns.org#K7M2-QX9P", "Laura", Perfil.ANALISTA)
        compose.setContent { ZondaTheme { CodigoPersonaContenido(codigo = codigo, alVolver = {}, reloj = { Fixtures.AHORA }) } }

        compose.onNodeWithText("Código para Laura").assertIsDisplayed()
        compose.onNodeWithText("Laura entra como analista. En su teléfono, conectado a la red de la empresa: Zonda → Escanear código QR.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Código QR para sumar a Laura").assertIsDisplayed()
        compose.onNodeWithText("K7M2-QX9P").assertIsDisplayed()
        compose.onNodeWithText("Sirve una sola vez · vence en 9:52").assertIsDisplayed()
    }

    @Test
    fun `un codigo vencido lo dice`() {
        val codigo = CodigoPersona("K7M2-QX9P", Fixtures.AHORA.minusSeconds(1), "zonda:x", "Laura", Perfil.ANALISTA)
        compose.setContent { ZondaTheme { CodigoPersonaContenido(codigo = codigo, alVolver = {}, reloj = { Fixtures.AHORA }) } }

        compose.onNodeWithText("Venció. Volvé y generá otro código.").assertIsDisplayed()
    }

    @Test
    fun `el QR es un QR de verdad`() {
        val matriz = matrizQr("zonda:https://zonda-equipo.duckdns.org#K7M2-QX9P;vence=2026-10-12T12:10:00+00:00")

        assertTrue(matriz.width >= 21)
        assertEquals(matriz.width, matriz.height)
        // Las esquinas tienen los tres cuadrados de ubicación.
        assertTrue(matriz[0, 0] && matriz[matriz.width - 1, 0] && matriz[0, matriz.height - 1])
    }

    @Test
    fun `equipos con su importancia`() {
        compose.setContent {
            ZondaTheme {
                Column {
                    EquiposContenido(estado = EquiposUiState(red = red, equipos = equipos), alVolver = {})
                }
            }
        }
        compose.onNodeWithText("srv-sueldos").assertIsDisplayed()
        compose.onNodeWithText("192.168.30.80").assertIsDisplayed()
        compose.onNodeWithText("Poco importante").assertIsDisplayed()
    }

    @Test
    fun `la hoja de un equipo tiene las tres opciones y dice que pide el motivo`() {
        var elegida: Importancia? = null
        compose.setContent {
            ZondaTheme { Column { ContenidoHojaImportancia(equipos.first(), alElegir = { elegida = it }) } }
        }
        compose.onNodeWithText("Sus alertas suben un nivel: una media pasa a alta.").assertIsDisplayed()
        compose.onNodeWithText("Al cambiarla te pide el motivo y queda en el registro de cambios.").assertIsDisplayed()
        compose.onNodeWithText("Normal").performClick()
        assertEquals(Importancia.NORMAL, elegida)
    }

    @Test
    fun `el registro dice cada cambio con palabras, quien y cuando, y el motivo entre comillas`() {
        fun cambio(id: Long, horas: Long, tipo: TipoCambio, objeto: String, nombre: String?, antes: String?, despues: String?, motivo: String?) =
            CambioRegistro(id, Fixtures.AHORA.minus(Duration.ofHours(horas)), tipo, "d-1", "Taiel", objeto, nombre, antes, despues, motivo)
        val registro = listOf(
            cambio(4, 2, TipoCambio.SEVERIDAD, "fuerza_bruta", null, "alta", "media", "El backup de cada noche usa SSH y dispara alertas de fuerza bruta."),
            cambio(3, 3, TipoCambio.IMPORTANCIA, "192.168.20.20", "srv-sueldos", "normal", "critico", "Ahí están los sueldos de toda la empresa."),
            cambio(2, 30, TipoCambio.PERFIL, "d-3", "Meli", "analista", "administrador", null),
            cambio(1, 50, TipoCambio.PERSONA_NUEVA, "d-2", "Laura", null, "analista", null),
        )
        compose.setContent { ZondaTheme { RegistroContenido(red = "Oficina central", registro = registro, alVolver = {}, zona = utc, ahora = Fixtures.AHORA) } }

        compose.onNodeWithText("Hoy 10:00 · Taiel").assertIsDisplayed()
        compose.onNodeWithText("Fuerza bruta pasó de alta a media").assertIsDisplayed()
        compose.onNodeWithText("“El backup de cada noche usa SSH y dispara alertas de fuerza bruta.”").assertIsDisplayed()
        compose.onNodeWithText("srv-sueldos pasó de normal a crítico").assertIsDisplayed()
        compose.onNodeWithText("Ayer 06:00 · Taiel").assertIsDisplayed()
        compose.onNodeWithText("Meli pasó de analista a administrador").assertIsDisplayed()
        compose.onNodeWithText("10/10 10:00 · Taiel").assertIsDisplayed()
        compose.onNodeWithText("Sumó a Laura al equipo como analista").assertIsDisplayed()
    }

    @Test
    fun `quitar su propio telefono se lee distinto que quitar a otra persona`() {
        val ahora: Instant = Fixtures.AHORA
        val registro = listOf(
            CambioRegistro(2, ahora, TipoCambio.PERSONA_QUITADA, "d-2", "Laura", "d-2", "Laura", "analista", null, null),
            CambioRegistro(1, ahora, TipoCambio.PERSONA_QUITADA, "d-1", "Taiel", "d-5", "Ramiro", "analista", null, null),
        )
        compose.setContent { ZondaTheme { RegistroContenido(red = "Oficina central", registro = registro, alVolver = {}, zona = utc, ahora = ahora) } }

        compose.onNodeWithText("Quitó su teléfono del equipo").assertIsDisplayed()
        compose.onNodeWithText("Quitó a Ramiro del equipo").assertIsDisplayed()
    }

    @Test
    fun `el registro nombra a los navegadores del tablero como navegadores y no como personas`() {
        val ahora: Instant = Fixtures.AHORA
        val chromeEnWindows = "Tablero web · Chrome en Windows"
        val registro = listOf(
            CambioRegistro(5, ahora, TipoCambio.PERSONA_QUITADA, "d-7", chromeEnWindows, "d-7", chromeEnWindows, "administrador", null, null),
            CambioRegistro(4, ahora, TipoCambio.PERSONA_QUITADA, "d-1", "Taiel", "d-9", "Tablero web · Edge en Windows", "analista", null, null),
            CambioRegistro(3, ahora, TipoCambio.PERFIL, "d-1", "Taiel", "d-7", chromeEnWindows, "administrador", "analista", null),
            CambioRegistro(2, ahora, TipoCambio.PERSONA_NUEVA, "d-1", "Taiel", "d-7", chromeEnWindows, null, "administrador", null),
            CambioRegistro(1, ahora, TipoCambio.PERSONA_NUEVA, "d-1", "Taiel", "d-8", "Tablero web", null, "administrador", null),
        )
        compose.setContent { ZondaTheme { RegistroContenido(red = "Oficina central", registro = registro, alVolver = {}, zona = utc, ahora = ahora) } }

        compose.onNodeWithText("Salió del tablero").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Le quitó el acceso al navegador Edge en Windows").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("El navegador Chrome en Windows pasó de administrador a analista").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sumó el navegador Chrome en Windows como administrador").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sumó un navegador como administrador").performScrollTo().assertIsDisplayed()
    }
}
