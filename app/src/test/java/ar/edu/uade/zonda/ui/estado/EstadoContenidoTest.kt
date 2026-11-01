package ar.edu.uade.zonda.ui.estado

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.recorrido.LocalRegistroRecorrido
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.RegistroRecorrido
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pantalla Estado dibujada con Compose sobre Robolectric, sin emulador (Figma 1-3 a 1-256, 99-2 y
 * 99-122 a 99-442), en una pantalla del tamaño del cuadro del Figma: la lista solo dibuja lo que entra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h917dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EstadoContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    /** Fixtures.AHORA es el lunes 12/10 a las 12:00 en UTC. */
    private val ahora = Fixtures.AHORA

    private fun datos(
        semaforo: Semaforo = Semaforo.ROJO,
        sinRevisar: ContadoresSinRevisar = ContadoresSinRevisar(altas = 2, medias = 1, bajas = 0),
        alertas: List<Alerta> = listOf(
            Fixtures.alerta("a-1", severidad = Severidad.ALTA),
            Fixtures.alerta("a-2", severidad = Severidad.ALTA),
            Fixtures.alerta("m-1", severidad = Severidad.MEDIA),
        ),
    ) = ContenidoEstado.Datos(
        semaforo = semaforo,
        sinRevisar = sinRevisar,
        ultimaDeteccion = ahora.minus(Duration.ofMinutes(5)),
        alertas = alertas,
    )

    private fun dibujar(estado: EstadoUiState, acciones: AccionesEstado = AccionesEstado(), registro: RegistroRecorrido? = null) {
        compose.setContent {
            ZondaTheme {
                CompositionLocalProvider(LocalRegistroRecorrido provides registro) {
                    EstadoContenido(estado = estado, acciones = acciones, ahora = ahora, zona = utc)
                }
            }
        }
    }

    @Test
    fun `con altas sin revisar muestra el semaforo rojo, los contadores y la lista como el Figma`() {
        dibujar(EstadoUiState(contenido = datos(), datosDe = ahora))

        compose.onNodeWithText("ATENCIÓN").assertIsDisplayed()
        compose.onNodeWithText("2 alertas altas sin revisar").assertIsDisplayed()
        compose.onNodeWithText("Última alta sin revisar: hace 5 min · actualizado 12:00").assertIsDisplayed()
        compose.onNodeWithText("Tocá un contador para filtrar la lista.").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 sin revisar de severidad Alta").assertIsNotSelected()
        compose.onNodeWithContentDescription("1 sin revisar de severidad Media").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 sin revisar de severidad Baja").assertIsDisplayed()
        compose.onNodeWithText("Sin revisar").assertIsDisplayed()
        compose.onNodeWithText("Ver todas →").assertIsDisplayed()
        compose.onAllNodesWithText("pc-09 → srv-web").assertCountEquals(3)
        // Con datos, la barra tiene los dos íconos (Figma 1-3).
        compose.onNodeWithContentDescription("Sincronizar ahora").assertIsDisplayed()
        compose.onNodeWithContentDescription("Abrir el Resumen").assertIsDisplayed()
    }

    @Test
    fun `mientras sincroniza, el boton dice Sincronizando y no su nombre`() {
        dibujar(EstadoUiState(contenido = datos(), datosDe = ahora, sincronizando = true))

        compose.onNodeWithContentDescription("Sincronizando…").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Sincronizar ahora").assertCountEquals(0)
    }

    @Test
    fun `con solo medias el semaforo es amarillo y sigue pidiendo atencion`() {
        dibujar(
            EstadoUiState(
                contenido = datos(
                    semaforo = Semaforo.AMARILLO,
                    sinRevisar = ContadoresSinRevisar(altas = 0, medias = 1, bajas = 0),
                    alertas = listOf(Fixtures.alerta("m-1", severidad = Severidad.MEDIA)),
                ),
                datosDe = ahora,
            ),
        )

        compose.onNodeWithText("ATENCIÓN").assertIsDisplayed()
        compose.onNodeWithText("1 alerta media sin revisar").assertIsDisplayed()
    }

    @Test
    fun `tocar un contador, Ver todas, una tarjeta, sincronizar y el Resumen avisan a quien corresponde`() {
        val tocados = mutableListOf<String>()
        dibujar(
            EstadoUiState(contenido = datos(), datosDe = ahora),
            AccionesEstado(
                alTocarContador = { tocados += "contador ${it.name}" },
                alVerTodas = { tocados += "ver todas" },
                alAbrirDetalle = { tocados += "detalle $it" },
                alSincronizar = { tocados += "sincronizar" },
                alAbrirResumen = { tocados += "resumen" },
            ),
        )

        compose.onNodeWithContentDescription("1 sin revisar de severidad Media").performClick()
        compose.onNodeWithText("Ver todas →").performClick()
        compose.onAllNodesWithText("pc-09 → srv-web")[0].performClick()
        compose.onNodeWithContentDescription("Sincronizar ahora").performClick()
        compose.onNodeWithContentDescription("Abrir el Resumen").performClick()

        assertEquals(listOf("contador MEDIA", "ver todas", "detalle a-1", "sincronizar", "resumen"), tocados)
    }

    @Test
    fun `con un contador elegido cambian la ayuda y el encabezado, y el contador queda marcado`() {
        dibujar(
            EstadoUiState(
                contenido = datos(alertas = listOf(Fixtures.alerta("a-1", severidad = Severidad.ALTA))),
                severidadElegida = Severidad.ALTA,
                datosDe = ahora,
            ),
        )

        compose.onNodeWithText("Mostrando solo altas sin revisar. Tocá de nuevo para ver todas.").assertIsDisplayed()
        compose.onNodeWithText("Solo altas sin revisar").assertIsDisplayed()
        compose.onNodeWithText("Ver todas →").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 sin revisar de severidad Alta").assertIsSelected()
        compose.onNodeWithContentDescription("1 sin revisar de severidad Media").assertIsNotSelected()
    }

    @Test
    fun `el contador elegido mide 4 mas de ancho y de alto, como el Figma`() {
        dibujar(EstadoUiState(contenido = datos(), severidadElegida = Severidad.MEDIA, datosDe = ahora))

        val elegido = compose.onNodeWithContentDescription("1 sin revisar de severidad Media").getUnclippedBoundsInRoot()
        val otro = compose.onNodeWithContentDescription("2 sin revisar de severidad Alta").getUnclippedBoundsInRoot()

        assertEquals(74f, elegido.height.value, 0.5f)
        assertEquals(70f, otro.height.value, 0.5f)
        assertEquals(otro.width.value + 4f, elegido.width.value, 1f)
        assertEquals("los de arriba alineados", otro.top.value, elegido.top.value, 0.5f)
        assertEquals("12 entre contadores", 12f, (elegido.left - otro.right).value, 1f)
    }

    @Test
    fun `sin bajas sin revisar y con Baja elegida muestra la nota del Figma`() {
        dibujar(EstadoUiState(contenido = datos(alertas = emptyList()), severidadElegida = Severidad.BAJA, datosDe = ahora))

        compose.onNodeWithText("Sin alertas bajas sin revisar").assertIsDisplayed()
        compose.onNodeWithText("No hay alertas bajas sin revisar. Las bajas no cambian el semáforo: esperan en la lista hasta que las revises.").assertIsDisplayed()
    }

    @Test
    fun `con bajas pendientes el verde dice sin urgencias, cuantas faltan y cuando llego la ultima`() {
        dibujar(
            EstadoUiState(
                contenido = datos(
                    semaforo = Semaforo.VERDE,
                    sinRevisar = ContadoresSinRevisar(altas = 0, medias = 0, bajas = 4),
                    alertas = listOf(Fixtures.alerta("b-1", severidad = Severidad.BAJA)),
                ),
                datosDe = ahora,
            ),
        )

        compose.onNodeWithText("SIN URGENCIAS").assertIsDisplayed()
        compose.onNodeWithText("4 alertas bajas sin revisar").assertIsDisplayed()
        compose.onNodeWithText("Última baja sin revisar: hace 5 min · actualizado 12:00").assertIsDisplayed()
        compose.onAllNodesWithText("TRANQUILA").assertCountEquals(0)
        compose.onNodeWithText("Rojo: hay alertas altas sin revisar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amarillo: solo quedan medias sin revisar").assertIsDisplayed()
        compose.onNodeWithText("Verde: no queda ninguna alta ni media; las bajas esperan en la lista").assertIsDisplayed()
        compose.onAllNodesWithText("Ver todas →").assertCountEquals(0)
    }

    @Test
    fun `sin conexion muestra el banner con la hora y el ultimo estado conocido`() {
        dibujar(EstadoUiState(contenido = datos(), datosDe = ahora, sinConexion = true))

        compose.onNodeWithText("Sin conexión · mostrando datos de las 12:00").assertIsDisplayed()
        compose.onNodeWithText("Último estado conocido · puede haber alertas nuevas").assertIsDisplayed()
        compose.onNodeWithText("2 alertas altas sin revisar").assertIsDisplayed()
    }

    @Test
    fun `datos de otro dia dicen el dia, no solo la hora`() {
        dibujar(
            EstadoUiState(
                contenido = datos(semaforo = Semaforo.VERDE, sinRevisar = ContadoresSinRevisar.CERO, alertas = emptyList()),
                datosDe = ahora.minus(Duration.ofDays(1)),
            ),
        )

        compose.onNodeWithText("Ninguna alerta sin revisar · actualizado ayer 12:00").assertIsDisplayed()
        compose.onNodeWithText("No queda ninguna alerta sin revisar. Las nuevas aparecen acá apenas se sincronizan.").assertIsDisplayed()
    }

    @Test
    fun `sin nada pendiente la red esta tranquila, con la leyenda y sin Ver todas`() {
        dibujar(
            EstadoUiState(
                contenido = datos(semaforo = Semaforo.VERDE, sinRevisar = ContadoresSinRevisar.CERO, alertas = emptyList()),
                datosDe = ahora,
            ),
        )

        compose.onNodeWithText("TRANQUILA").assertIsDisplayed()
        compose.onNodeWithText("La red está tranquila").assertIsDisplayed()
        compose.onNodeWithText("Ninguna alerta sin revisar · actualizado 12:00").assertIsDisplayed()
        compose.onNodeWithText("Verde: no queda ninguna alta ni media; las bajas esperan en la lista").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Ver todas →").assertCountEquals(0)
    }

    @Test
    fun `sin datos el semaforo es gris, nunca verde, y el boton sincroniza`() {
        var sincronizar = 0
        dibujar(EstadoUiState(contenido = ContenidoEstado.SinDatos), AccionesEstado(alSincronizar = { sincronizar++ }))

        compose.onNodeWithText("SIN DATOS").assertIsDisplayed()
        compose.onNodeWithText("Sin datos todavía").assertIsDisplayed()
        compose.onAllNodesWithText("TRANQUILA").assertCountEquals(0)
        compose.onNodeWithText("Conectá con la red de tu empresa").assertIsDisplayed()
        // Como el cuadro 1-222, la barra va sin el ícono de sincronizar: lo hace el botón.
        compose.onAllNodesWithContentDescription("Sincronizar ahora").assertCountEquals(0)
        compose.onNodeWithText("Sincronizar ahora").performClick()
        assertEquals(1, sincronizar)
    }

    @Test
    fun `sin red pareada ofrece configurar la conexion`() {
        var conectar = 0
        dibujar(EstadoUiState(contenido = ContenidoEstado.SinRed), AccionesEstado(alConectar = { conectar++ }))

        compose.onNodeWithText("Necesitás el código de pareo que el administrador genera en la computadora del servidor Zonda.").assertIsDisplayed()
        compose.onNodeWithText("Configurar conexión").performClick()
        assertEquals(1, conectar)
    }

    @Test
    fun `durante la primera descarga muestra la tarjeta con cuantas van`() {
        dibujar(EstadoUiState(contenido = ContenidoEstado.Descargando(descargadas = 120, total = 213)))

        compose.onNodeWithText("Descargando alertas…").assertIsDisplayed()
        compose.onNodeWithText("120 de 213 · últimos 30 días · solo la primera vez").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sincronizar ahora").assertIsDisplayed()
    }

    @Test
    fun `con el acceso vencido ofrece conectar de nuevo`() {
        var conectar = 0
        dibujar(EstadoUiState(contenido = datos(), datosDe = ahora, accesoVencido = true), AccionesEstado(alConectar = { conectar++ }))

        compose.onNodeWithText("El acceso a esta red venció. Lo guardado sigue a la vista.").assertIsDisplayed()
        compose.onNodeWithText("Conectar de nuevo").performClick()
        assertEquals(1, conectar)
    }

    @Test
    fun `al sincronizar a pedido aparece el aviso con la fecha y la hora y se va solo`() {
        compose.mainClock.autoAdvance = false
        var mostrado = 0
        dibujar(
            EstadoUiState(contenido = datos(), datosDe = ahora, aviso = AvisoEstado.Sincronizado(ahora, nuevas = 0)),
            AccionesEstado(alAvisoMostrado = { mostrado++ }),
        )
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Sincronizado").assertIsDisplayed()
        compose.onNodeWithText("lun 12/10 · 12:00").assertIsDisplayed()
        compose.onNodeWithText("Ya tenés las alertas más recientes.").assertIsDisplayed()
        assertEquals(1, mostrado)

        compose.mainClock.advanceTimeBy(DURACION_AVISO_SINCRONIZADO_MS + 100)
        compose.onAllNodesWithText("Sincronizado").assertCountEquals(0)
    }

    @Test
    fun `si la sincronizacion trajo alertas, el aviso dice cuantas`() {
        dibujar(EstadoUiState(contenido = datos(), datosDe = ahora, aviso = AvisoEstado.Sincronizado(ahora, nuevas = 3)))

        compose.onNodeWithText("Llegaron 3 alertas nuevas.").assertIsDisplayed()
    }

    @Test
    fun `un error al sincronizar se avisa abajo con Reintentar, sin tapar los datos`() {
        var sincronizar = 0
        dibujar(
            EstadoUiState(contenido = datos(), datosDe = ahora, aviso = AvisoEstado.Error(ErrorZonda.SinConexion())),
            AccionesEstado(alSincronizar = { sincronizar++ }),
        )

        compose.onNodeWithText("Sin conexión. Se muestran los datos guardados.").assertIsDisplayed()
        compose.onNodeWithText("2 alertas altas sin revisar").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitForIdle()
        assertEquals(1, sincronizar)
    }

    @Test
    fun `el recorrido encuentra el semaforo, los contadores y los dos iconos de la barra`() {
        val registro = RegistroRecorrido()
        dibujar(EstadoUiState(contenido = datos(), datosDe = ahora), registro = registro)
        compose.waitForIdle()

        val semaforo = presente(registro.posicionDe(ObjetivoRecorrido.SEMAFORO))
        val contadores = presente(registro.posicionDe(ObjetivoRecorrido.CONTADORES))
        assertTrue("los contadores van debajo del semáforo", contadores.top > semaforo.bottom)
        presente(registro.posicionDe(ObjetivoRecorrido.SINCRONIZAR))
        presente(registro.posicionDe(ObjetivoRecorrido.ICONO_RESUMEN))
    }

    private fun <T : Any> presente(valor: T?): T {
        assertTrue(valor != null)
        return valor!!
    }

    @Test
    fun `una revision que no se aplico se avisa arriba del semaforo y Ver abre esa alerta`() {
        val tocados = mutableListOf<String>()
        dibujar(
            EstadoUiState(contenido = datos(), datosDe = ahora, revisionesNoAplicadas = listOf("a-7")),
            AccionesEstado(alAbrirDetalle = { tocados += "detalle $it" }, alVerNoAplicadas = { tocados += "alertas" }),
        )

        compose.onNodeWithText("Una revisión tuya no se aplicó").assertIsDisplayed()
        compose.onNodeWithText("Otra persona revisó esa alerta antes que vos.").assertIsDisplayed()
        compose.onNodeWithText("Ver").performClick()

        assertEquals(listOf("detalle a-7"), tocados)
    }

    @Test
    fun `con varias revisiones que no se aplicaron Ver abre Alertas con esas`() {
        val tocados = mutableListOf<String>()
        dibujar(
            EstadoUiState(contenido = datos(), datosDe = ahora, revisionesNoAplicadas = listOf("a-7", "a-8")),
            AccionesEstado(alAbrirDetalle = { tocados += "detalle $it" }, alVerNoAplicadas = { tocados += "alertas" }),
        )

        compose.onNodeWithText("2 revisiones tuyas no se aplicaron").assertIsDisplayed()
        compose.onNodeWithText("Alguien más revisó esas alertas antes que vos.").assertIsDisplayed()
        compose.onNodeWithText("Ver").performClick()

        assertEquals(listOf("alertas"), tocados)
    }

    @Test
    fun `sin revisiones en conflicto no hay aviso`() {
        dibujar(EstadoUiState(contenido = datos(), datosDe = ahora))

        compose.onNodeWithText("Ver").assertDoesNotExist()
    }
}
