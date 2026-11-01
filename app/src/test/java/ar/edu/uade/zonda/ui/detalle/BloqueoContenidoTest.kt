package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeBloqueosRepository.Companion.bloqueo
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.EstadoFirewall
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.TipoCambio
import ar.edu.uade.zonda.ui.ajustes.tituloDelCambio
import ar.edu.uade.zonda.ui.components.FilaPersona
import ar.edu.uade.zonda.ui.estado.TarjetaBloqueos
import ar.edu.uade.zonda.ui.estado.TarjetaEstadoSistema
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El bloqueo del origen dibujado con Compose (decisión 51): la tarjeta, la hoja, Estado y el registro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BloqueoContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")
    private val conectado = EstadoFirewall(habilitado = true, conectado = true, ultimaConsulta = Fixtures.AHORA, nuncaBloquear = emptyList())

    private fun detalle(
        actual: Bloqueo? = null,
        perfil: Perfil = Perfil.ADMINISTRADOR,
        puedeBloquear: Boolean = perfil.esAdministrador,
        firewall: EstadoFirewall = conectado,
        nuncaSeBloquea: Boolean = false,
        acciones: AccionesDetalle = AccionesDetalle(),
    ) {
        val estado = DetalleUiState.Contenido(
            alerta = Fixtures.alerta("a-1"),
            accion = null,
            sinConexion = false,
            datosDe = Fixtures.AHORA,
            perfil = perfil,
            puedeBloquear = puedeBloquear,
            bloqueo = SeccionBloqueo(firewall, actual, nuncaSeBloquea),
        )
        compose.setContent { ZondaTheme { DetalleContenido(estado = estado, acciones = acciones, zona = utc, ahora = Fixtures.AHORA) } }
    }

    @Test
    fun `un administrador ve Bloquear y la hoja abre con el motivo propuesto`() {
        var propuesto: String? = null
        detalle(acciones = AccionesDetalle(alBloquear = { propuesto = it }))

        compose.onNodeWithText("Sin bloquear").performScrollTo().assertExists()
        compose.onNodeWithText("Bloquear").performScrollTo().performClick()

        assertEquals("Fuerza bruta de pc-09 a srv-web.", propuesto)
    }

    @Test
    fun `un analista ve Pedir bloqueo`() {
        detalle(perfil = Perfil.ANALISTA)

        compose.onNodeWithText("Pedir bloqueo").performScrollTo().assertExists()
        compose.onNodeWithText("Bloquear").assertDoesNotExist()
    }

    @Test
    fun `el analista de guardia con permiso ve Bloquear`() {
        detalle(perfil = Perfil.ANALISTA, puedeBloquear = true)

        compose.onNodeWithText("Bloquear").performScrollTo().assertExists()
        compose.onNodeWithText("Pedir bloqueo").assertDoesNotExist()
    }

    @Test
    fun `el analista de guardia con permiso aprueba y rechaza los pedidos`() {
        detalle(actual = bloqueo(estado = EstadoBloqueo.PEDIDO, pedidoPor = "Diego"), perfil = Perfil.ANALISTA, puedeBloquear = true)

        compose.onNodeWithText("Aprobar").performScrollTo().assertExists()
        compose.onNodeWithText("Rechazar").assertExists()
    }

    @Test
    fun `un equipo que el firewall nunca bloquea no tiene boton`() {
        detalle(nuncaSeBloquea = true)

        compose.onNodeWithText("El firewall nunca bloquea este equipo").performScrollTo().assertExists()
        compose.onNodeWithText("Bloquear").assertDoesNotExist()
    }

    @Test
    fun `con el firewall sin responder se puede bloquear igual y lo dice`() {
        detalle(firewall = conectado.copy(conectado = false))

        compose.onNodeWithText("El firewall no responde").performScrollTo().assertExists()
        compose.onNodeWithText("Bloquear").assertExists()
    }

    @Test
    fun `bloqueado dice hasta cuando y quien, y el administrador puede cambiarlo o soltarlo`() {
        var desbloquear = 0
        detalle(actual = bloqueo(estado = EstadoBloqueo.APLICADO), acciones = AccionesDetalle(alDesbloquear = { desbloquear++ }))

        compose.onNodeWithText("Bloqueado · vence 12:58").performScrollTo().assertExists()
        compose.onNodeWithText("Lo bloqueó Este teléfono · hoy 11:58").assertExists()
        compose.onNodeWithText("Cambiar duración").assertExists()
        compose.onNodeWithText("Desbloquear").performScrollTo().performClick()
        assertEquals(1, desbloquear)
    }

    @Test
    fun `aprobado espera al firewall y un analista no tiene botones`() {
        detalle(actual = bloqueo(estado = EstadoBloqueo.APROBADO, segundos = null), perfil = Perfil.ANALISTA)

        compose.onNodeWithText("Bloqueado hasta desbloquear").performScrollTo().assertExists()
        compose.onNodeWithText("Esperando al firewall").assertExists()
        compose.onNodeWithText("Desbloquear").assertDoesNotExist()
    }

    @Test
    fun `un pedido muestra quien y por que, con Aprobar y Rechazar para el administrador`() {
        var aprobar = 0
        detalle(actual = bloqueo(estado = EstadoBloqueo.PEDIDO, pedidoPor = "Laura"), acciones = AccionesDetalle(alAprobarBloqueo = { aprobar++ }))

        compose.onNodeWithText("Pedido de bloqueo · 1 h").performScrollTo().assertExists()
        compose.onNodeWithText("Lo pidió Laura · hoy 11:58").assertExists()
        compose.onNodeWithText("“Fuerza bruta de pc-09 a srv-web.”").assertExists()
        compose.onNodeWithText("Rechazar").assertExists()
        compose.onNodeWithText("Aprobar").performScrollTo().performClick()
        assertEquals(1, aprobar)
    }

    @Test
    fun `un analista con su pedido abierto ve que espera a alguien que pueda bloquear`() {
        detalle(actual = bloqueo(estado = EstadoBloqueo.PEDIDO), perfil = Perfil.ANALISTA)

        compose.onNodeWithText("Espera a alguien que pueda bloquear").performScrollTo().assertExists()
        compose.onNodeWithText("Aprobar").assertDoesNotExist()
    }

    private fun hoja(formulario: FormularioBloqueo, puedeBloquear: Boolean = true) {
        compose.setContent { ZondaTheme { Column { ContenidoHojaBloqueo(formulario, Fixtures.alerta("a-1"), puedeBloquear, AccionesDetalle()) } } }
    }

    @Test
    fun `tres duraciones y hasta desbloquear se elige sin revisar la alerta`() {
        hoja(FormularioBloqueo(AccionBloqueo.BLOQUEAR))

        compose.onNodeWithText("Bloquear pc-09").assertExists()
        compose.onNodeWithText("1 hora").assertIsEnabled()
        compose.onNodeWithText("24 horas").assertIsEnabled()
        compose.onNodeWithText("Hasta desbloquear").assertIsEnabled()
        compose.onNodeWithText("15 minutos").assertDoesNotExist()
        compose.onNodeWithText("8 horas").assertDoesNotExist()
    }

    @Test
    fun `la hoja de un analista pide y la de desbloquear no tiene duraciones`() {
        hoja(FormularioBloqueo(AccionBloqueo.BLOQUEAR, conError = true), puedeBloquear = false)
        compose.onNodeWithText("Pedir bloqueo de pc-09").assertExists()
        compose.onNodeWithText("Escribí el motivo.").assertExists()
    }

    @Test
    fun `desbloquear lleva solo el motivo`() {
        hoja(FormularioBloqueo(AccionBloqueo.DESBLOQUEAR))

        compose.onNodeWithText("Desbloquear pc-09").assertExists()
        compose.onNodeWithText("1 hora").assertDoesNotExist()
        compose.onNodeWithText("pc-09 (192.168.10.9) vuelve a llegar a otras áreas y a internet.").assertExists()
    }

    @Test
    fun `la hoja dice que se corta y por cuanto tiempo`() {
        hoja(FormularioBloqueo(AccionBloqueo.BLOQUEAR))
        compose.onNodeWithText("pc-09 (192.168.10.9) no va a llegar a otras áreas ni a internet durante 1 hora.").assertExists()
    }

    @Test
    fun `hasta desbloquear dice que dura hasta que alguien lo suelte`() {
        hoja(FormularioBloqueo(AccionBloqueo.BLOQUEAR, duracion = DuracionBloqueo.HASTA_DESBLOQUEAR))
        compose.onNodeWithText("pc-09 (192.168.10.9) no va a llegar a otras áreas ni a internet hasta que alguien lo desbloquee.").assertExists()
    }

    @Test
    fun `con el origen bloqueado Revisar trae propuesto Bloqueado manualmente`() {
        var propuesto: String? = null
        var sinPropuesta = 0
        detalle(actual = bloqueo(estado = EstadoBloqueo.APLICADO), acciones = AccionesDetalle(alRevisarBloqueada = { propuesto = it }, alRevisar = { sinPropuesta++ }))

        compose.onNodeWithText("Revisar").performScrollTo().performClick()

        assertEquals("Se bloqueó el acceso SSH de pc-09 y se avisó al proveedor.", propuesto)
        assertEquals(0, sinPropuesta)
    }

    @Test
    fun `sin bloqueo Revisar abre la revision de siempre`() {
        var sinPropuesta = 0
        detalle(acciones = AccionesDetalle(alRevisar = { sinPropuesta++ }))

        compose.onNodeWithText("Revisar").performScrollTo().performClick()

        assertEquals(1, sinPropuesta)
    }

    @Test
    fun `asignar no ofrece los navegadores del tablero y Personas los marca`() {
        val taiel = Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, null, vos = true)
        val laura = Persona("d-2", "Laura", "Teléfono de Laura", Perfil.ANALISTA, null, vos = false)
        val tablero = Persona("d-3", null, "Tablero web", Perfil.ADMINISTRADOR, null, vos = false, navegador = true)
        compose.setContent {
            ZondaTheme {
                Column {
                    ContenidoHojaAsignar(listOf(taiel, laura, tablero), responsableId = null) {}
                    FilaPersona(tablero, Fixtures.AHORA, utc)
                }
            }
        }

        compose.onNodeWithText("Laura").assertExists()
        // El tablero aparece una sola vez: en la fila de Personas, marcado como navegador.
        compose.onNodeWithText("Tablero web").assertExists()
        compose.onNodeWithText("Navegador · Administrador").assertExists()
    }

    @Test
    fun `Personas dice quien de los analistas bloquea`() {
        val laura = Persona("d-2", "Laura", "Teléfono de Laura", Perfil.ANALISTA, null, vos = false, permisoDeBloquear = true)
        val diego = Persona("d-4", "Diego", "Teléfono de Diego", Perfil.ANALISTA, null, vos = false)
        compose.setContent { ZondaTheme { Column { FilaPersona(laura, Fixtures.AHORA, utc); FilaPersona(diego, Fixtures.AHORA, utc) } } }

        compose.onNodeWithText("Analista · Bloquea · Teléfono de Laura").assertExists()
        compose.onNodeWithText("Analista · Teléfono de Diego").assertExists()
    }

    @Test
    fun `Estado del sistema muestra el firewall solo si la red tiene uno`() {
        val sistema = EstadoSistema(
            sensor = EstadoPieza.FUNCIONA,
            analisis = EstadoPieza.FUNCIONA,
            servidor = EstadoPieza.FUNCIONA,
            ultimaCorrida = Fixtures.AHORA.minusSeconds(12),
            ultimoFlujo = Fixtures.AHORA.minusSeconds(240),
            consultadoEn = Fixtures.AHORA.minusSeconds(7),
            firewall = EstadoPieza.FUNCIONA,
            ultimaConsultaFirewall = Fixtures.AHORA.minusSeconds(6),
        )
        compose.setContent { ZondaTheme { Column { TarjetaEstadoSistema(sistema, { Fixtures.AHORA }); TarjetaEstadoSistema(sistema.copy(firewall = null), { Fixtures.AHORA }) } } }

        // Como las otras piezas, TalkBack lee la fila entera; la segunda tarjeta, sin firewall, no tiene la fila.
        compose.onNodeWithContentDescription("Firewall: Funciona").assertExists()
        compose.onNodeWithText("Última consulta hace 6 s").assertExists()
    }

    @Test
    fun `la tarjeta de bloqueos de Estado abre la alerta de cada uno`() {
        var abierta: String? = null
        val bloqueos = listOf(
            bloqueo(id = "b-1", alertaId = "a-1", estado = EstadoBloqueo.APLICADO),
            bloqueo(id = "b-2", alertaId = "a-2", estado = EstadoBloqueo.PEDIDO, pedidoPor = "Laura").copy(ip = "192.168.30.50", nombre = "pc-rrhh"),
        )
        compose.setContent { ZondaTheme { TarjetaBloqueos(bloqueos, utc, Fixtures.AHORA, { abierta = it }) } }

        compose.onNodeWithText("Bloqueado · vence 12:58").assertExists()
        compose.onNodeWithText("Pedido de Laura · espera aprobación").assertExists()
        compose.onNodeWithText("pc-rrhh").performClick()
        assertEquals("a-2", abierta)
    }

    @Test
    fun `el registro cuenta los bloqueos con palabras`() {
        fun cambio(tipo: TipoCambio, antes: String?, despues: String?) =
            CambioRegistro(1, Fixtures.AHORA, tipo, "d-1", "Taiel", "192.168.30.50", "pc-rrhh", antes, despues, "Fuerza bruta.")
        compose.setContent {
            ZondaTheme {
                Column {
                    Text(tituloDelCambio(cambio(TipoCambio.BLOQUEO, null, "3600")))
                    Text(tituloDelCambio(cambio(TipoCambio.BLOQUEO, null, "hasta_desbloquear")))
                    Text(tituloDelCambio(cambio(TipoCambio.BLOQUEO, "3600", "28800")))
                    Text(tituloDelCambio(cambio(TipoCambio.BLOQUEO, "3600", "hasta_desbloquear")))
                    Text(tituloDelCambio(cambio(TipoCambio.BLOQUEO, null, "900")))
                    Text(tituloDelCambio(cambio(TipoCambio.DESBLOQUEO, "3600", null)))
                    Text(tituloDelCambio(cambio(TipoCambio.PERMISO_BLOQUEO, "pide_aprobacion", "bloquea").copy(objeto = "d-2", nombre = "Laura")))
                    Text(tituloDelCambio(cambio(TipoCambio.PERMISO_BLOQUEO, "bloquea", "pide_aprobacion").copy(objeto = "d-4", nombre = "Diego")))
                }
            }
        }

        compose.onNodeWithText("Bloqueó pc-rrhh por 1 h").assertExists()
        compose.onNodeWithText("Bloqueó pc-rrhh hasta desbloquear").assertExists()
        compose.onNodeWithText("Cambió el bloqueo de pc-rrhh a 8 h").assertExists()
        compose.onNodeWithText("Dejó pc-rrhh bloqueado hasta desbloquear").assertExists()
        compose.onNodeWithText("Bloqueó pc-rrhh por 15 min").assertExists()
        compose.onNodeWithText("Desbloqueó pc-rrhh").assertExists()
        compose.onNodeWithText("Le dio permiso de bloquear a Laura").assertExists()
        compose.onNodeWithText("Le sacó el permiso de bloquear a Diego").assertExists()
    }
}
