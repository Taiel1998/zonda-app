package ar.edu.uade.zonda.ui.alertas

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.OpcionesDeFiltro
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.recorrido.LocalRegistroRecorrido
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.RegistroRecorrido
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La pantalla Alertas dibujada con Compose sobre Robolectric, sin emulador (Figma 2-3 y sus hojas). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlertasContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    private fun lista(vararg alertas: ar.edu.uade.zonda.domain.model.Alerta) =
        ContenidoAlertas.Lista(alertas.toList(), sinRevisar = alertas.count { it.sinRevisar })

    private fun dibujar(estado: AlertasUiState, alCambiarFiltro: (FiltroAlertas) -> Unit = {}, alSincronizar: () -> Unit = {}, alQuitarFiltros: () -> Unit = {}, alAbrir: (String) -> Unit = {}) {
        compose.setContent {
            ZondaTheme {
                AlertasContenido(
                    estado = estado,
                    alAbrirDetalle = alAbrir,
                    alCambiarFiltro = alCambiarFiltro,
                    alSincronizar = alSincronizar,
                    alQuitarFiltros = alQuitarFiltros,
                    zona = utc,
                )
            }
        }
    }

    @Test
    fun `muestra las tarjetas, los chips y el resumen de siempre, y abre el detalle al tocar`() {
        var abierta: String? = null
        dibujar(
            AlertasUiState(
                contenido = lista(
                    Fixtures.alerta("a-1", severidad = Severidad.ALTA, envio = EstadoEnvio.PENDIENTE),
                    Fixtures.alerta("a-2", severidad = Severidad.MEDIA, revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
                ),
                datosDe = Fixtures.AHORA,
            ),
            alAbrir = { abierta = it },
        )

        compose.onAllNodesWithText("pc-09 → srv-web").assertCountEquals(2)
        compose.onNodeWithText("ALTA").assertIsDisplayed()
        compose.onNodeWithText("REVISADA").assertIsDisplayed()
        compose.onNodeWithText("pendiente").assertIsDisplayed()
        compose.onAllNodesWithText("10:50 · lun 12/10").assertCountEquals(2)
        // Por defecto: todas las de los últimos 30 días, con la hora de los datos.
        compose.onNodeWithText("2 alertas · 1 sin revisar · últimos 30 días · actualizado 12:00").assertIsDisplayed()
        // La fila se desliza: en la pantalla chica de la prueba, "Más filtros" queda a la derecha de "Fechas".
        listOf("Todas", "Severidad", "30 días", "Fechas", "Más filtros").forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }

        compose.onAllNodesWithText("pc-09 → srv-web").onFirst().performClick()
        assertEquals("a-1", abierta)
    }

    @Test
    fun `mientras sincroniza, la ruedita dice Sincronizando y no el nombre del boton`() {
        dibujar(AlertasUiState(contenido = lista(Fixtures.alerta("a-1")), datosDe = Fixtures.AHORA, sincronizando = true))

        compose.onNodeWithContentDescription("Sincronizando…").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Sincronizar ahora").assertCountEquals(0)
    }

    @Test
    fun `el resumen dice que se filtro, como los cuadros del Figma`() {
        val altas = Array(3) { Fixtures.alerta("a-$it", severidad = Severidad.ALTA) }
        val casos = listOf(
            FiltroAlertas(estado = FiltroEstado.SIN_REVISAR, severidades = setOf(Severidad.ALTA), periodo = Periodo.ULTIMAS_24_HORAS) to
                "3 alertas altas sin revisar · últimas 24 h",
            FiltroAlertas(severidades = setOf(Severidad.ALTA), periodo = Periodo.ULTIMAS_24_HORAS) to
                "3 alertas · 3 sin revisar · altas · últimas 24 h",
            FiltroAlertas(servicios = setOf("ssh", "ftp"), periodo = Periodo.ULTIMAS_24_HORAS) to
                "3 alertas · 3 sin revisar · SSH, FTP · últimas 24 h",
            FiltroAlertas(periodo = Periodo.ULTIMOS_7_DIAS) to "3 alertas · 3 sin revisar · últimos 7 días",
        )
        var filtro by mutableStateOf(casos.first().first)
        compose.setContent {
            ZondaTheme {
                AlertasContenido(
                    estado = AlertasUiState(contenido = lista(*altas), filtro = filtro, datosDe = Fixtures.AHORA),
                    alAbrirDetalle = {},
                    alCambiarFiltro = {},
                    alSincronizar = {},
                    alQuitarFiltros = {},
                    zona = utc,
                )
            }
        }

        casos.forEach { (caso, esperado) ->
            filtro = caso
            compose.onNodeWithText(esperado).assertIsDisplayed()
        }
    }

    @Test
    fun `revisadas y falsos positivos cuentan lo que quedo, y sin conexion dice de que hora son los datos`() {
        val revisadas = lista(
            Fixtures.alerta("a-1", revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
            Fixtures.alerta("a-2", revision = Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, origen = OrigenRevision.SERVIDOR)),
        )
        var estado by mutableStateOf(
            AlertasUiState(contenido = revisadas, filtro = FiltroAlertas(estado = FiltroEstado.REVISADAS, periodo = Periodo.ULTIMAS_24_HORAS)),
        )
        compose.setContent {
            ZondaTheme { AlertasContenido(estado = estado, alAbrirDetalle = {}, alCambiarFiltro = {}, alSincronizar = {}, alQuitarFiltros = {}, zona = utc) }
        }

        compose.onNodeWithText("2 revisadas · últimas 24 h").assertIsDisplayed()
        // El chip de estado muestra lo elegido; el de período, su valor.
        compose.onNodeWithText("Revisadas").assertIsDisplayed()
        compose.onNodeWithText("24 h").assertIsDisplayed()

        estado = AlertasUiState(contenido = revisadas, sinConexion = true, datosDe = Fixtures.AHORA)
        compose.onNodeWithText("2 alertas · 0 sin revisar · últimos 30 días · datos de las 12:00").assertIsDisplayed()
        compose.onNodeWithText("Sin conexión · mostrando datos de las 12:00").assertIsDisplayed()
    }

    @Test
    fun `sin resultados con un filtro ofrece limpiar los filtros`() {
        var quitados = false
        dibujar(
            AlertasUiState(contenido = ContenidoAlertas.Vacio(conFiltro = true), filtro = FiltroAlertas(severidades = setOf(Severidad.BAJA))),
            alQuitarFiltros = { quitados = true },
        )

        compose.onNodeWithText("Sin alertas para esos filtros").assertIsDisplayed()
        compose.onNodeWithText("Probá con otro período o quitá un filtro.").assertIsDisplayed()
        compose.onNodeWithText("Baja").assertIsDisplayed()
        compose.onNodeWithText("Limpiar filtros").performClick()
        assertTrue(quitados)
    }

    @Test
    fun `sin nada guardado y con el servidor caido muestra el error con reintentar`() {
        var reintentos = 0
        dibujar(AlertasUiState(contenido = ContenidoAlertas.Error, datosDe = Fixtures.AHORA), alSincronizar = { reintentos++ })

        compose.onNodeWithText("No se pudo cargar").assertIsDisplayed()
        compose.onNodeWithText("El servidor no respondió. Los datos de las 12:00 siguen disponibles sin conexión.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, reintentos)
    }

    @Test
    fun `sin red pareada y sin conexion lo dice con el banner`() {
        dibujar(AlertasUiState(contenido = ContenidoAlertas.SinRed, sinConexion = true))

        compose.onNodeWithText("Todavía no hay una red conectada").assertIsDisplayed()
        compose.onNodeWithText("Sin conexión · todavía no hay datos guardados").assertIsDisplayed()
    }

    @Test
    fun `las hojas de estado, severidad y periodo aplican lo elegido`() {
        val aplicados = mutableListOf<FiltroAlertas>()
        dibujar(AlertasUiState(contenido = lista(Fixtures.alerta("a-1"))), alCambiarFiltro = { aplicados += it })

        compose.onNodeWithText("Todas").performClick()
        compose.onNodeWithText("Estado de revisión").assertIsDisplayed()
        compose.onNodeWithText("Incluye las revisadas y los falsos positivos").assertIsDisplayed()
        compose.onNodeWithText("Sin revisar").performClick()
        compose.onNodeWithText("Aplicar").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Severidad").performClick()
        // Sin filtro están las tres marcadas, como en el Figma.
        compose.onNodeWithText("Alta").assertIsOn()
        compose.onNodeWithText("Media").performClick()
        compose.onNodeWithText("Baja").performClick()
        compose.onNodeWithText("Media").assertIsOff()
        compose.onNodeWithText("Aplicar").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("30 días").performClick()
        compose.onNodeWithText("Todo lo que hay guardado en el teléfono").assertIsDisplayed()
        compose.onNodeWithText("Últimas 24 h").performClick()
        compose.onNodeWithText("Aplicar").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(
                FiltroAlertas(estado = FiltroEstado.SIN_REVISAR),
                FiltroAlertas(severidades = setOf(Severidad.ALTA)),
                FiltroAlertas(periodo = Periodo.ULTIMAS_24_HORAS),
            ),
            aplicados,
        )
    }

    @Test
    fun `mas filtros ofrece hasta diez servicios con Los demas, los equipos y los metodos, y limpiar los quita`() {
        val aplicados = mutableListOf<FiltroAlertas>()
        val opciones = OpcionesDeFiltro(
            servicios = listOf("ssh", "ftp", "smb", "rdp", "http", "dns", "ldap", "icmp", "netbios", "otro", "kerberos", "sip"),
            destinos = listOf(Equipo("192.168.10.50", "srv-sueldos"), Equipo("192.168.10.77")),
        )
        dibujar(AlertasUiState(contenido = lista(Fixtures.alerta("a-1")), opciones = opciones), alCambiarFiltro = { aplicados += it })

        compose.onNodeWithText("Más filtros").performScrollTo().performClick()
        listOf("SSH", "FTP", "SMB", "RDP", "HTTP", "DNS", "LDAP", "ICMP", "NETBIOS", "Sin identificar", "Los demás")
            .forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        listOf("srv-sueldos", "192.168.10.77", "Modelo de IA", "Umbrales", "Reglas")
            .forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("KERBEROS").assertDoesNotExist()
        compose.onNodeWithText("OTRO").assertDoesNotExist()
        compose.onNodeWithText("SSH").performScrollTo().performClick()
        compose.onNodeWithText("Los demás").performScrollTo().performClick()
        compose.onNodeWithText("srv-sueldos").performScrollTo().performClick()
        compose.onNodeWithText("192.168.10.77").performScrollTo().performClick()
        compose.onNodeWithText("Reglas").performScrollTo().performClick()
        compose.onNodeWithText("Aplicar").performScrollTo().performClick()
        compose.waitForIdle()

        val esperado = FiltroAlertas(
            servicios = setOf("ssh"),
            otrosServicios = setOf("ssh", "ftp", "smb", "rdp", "http", "dns", "ldap", "icmp", "netbios", "otro"),
            destinos = setOf("192.168.10.50", "192.168.10.77"),
            metodos = setOf(MetodoDeteccion.REGLAS),
        )
        assertEquals(listOf(esperado), aplicados)
    }

    @Test
    fun `el recorrido resalta los chips y la primera tarjeta con los margenes del Figma`() {
        val registro = RegistroRecorrido()
        var ancho = 0f
        var densidad = 1f
        compose.setContent {
            densidad = LocalDensity.current.density
            ZondaTheme {
                CompositionLocalProvider(LocalRegistroRecorrido provides registro) {
                    Box(modifier = Modifier.onGloballyPositioned { ancho = it.size.width.toFloat() }) {
                        AlertasContenido(
                            estado = AlertasUiState(contenido = lista(Fixtures.alerta("a-1"), Fixtures.alerta("a-2"))),
                            alAbrirDetalle = {},
                            alCambiarFiltro = {},
                            alSincronizar = {},
                            alQuitarFiltros = {},
                            zona = utc,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val chips = registro.posicionDe(ObjetivoRecorrido.CHIPS)!!
        assertEquals(16f, chips.left / densidad, 0.5f)
        assertEquals(16f, (ancho - chips.right) / densidad, 0.5f)
        val tarjeta = registro.posicionDe(ObjetivoRecorrido.PRIMERA_TARJETA)!!
        assertTrue("la primera tarjeta va debajo de los chips", tarjeta.top > chips.bottom)
    }

    @Test
    fun `con las revisiones no aplicadas muestra su chip y la cruz lo saca`() {
        var cambiado: FiltroAlertas? = null
        dibujar(
            AlertasUiState(
                contenido = lista(Fixtures.alerta("a-7", envio = EstadoEnvio.CONFLICTO)),
                filtro = FiltroAlertas(soloNoAplicadas = true),
            ),
            alCambiarFiltro = { cambiado = it },
        )

        compose.onNodeWithContentDescription("Quitar el filtro Revisiones no aplicadas").assertIsDisplayed().performClick()

        assertEquals(FiltroAlertas(), cambiado)
    }

    @Test
    fun `con una sola alerta nueva el aviso de la sincronizacion va en singular`() {
        dibujar(AlertasUiState(contenido = lista(), aviso = AvisoAlertas.Sincronizado(Fixtures.AHORA, nuevas = 1, altasNuevas = 1, manual = false)))

        // Antes decía "1 nuevas": pasa con la alerta alta que crea `zonda emitir` en la demo.
        compose.onNodeWithText("Sincronizado: 1 nueva, 1 de severidad alta.").assertIsDisplayed()
    }
}
