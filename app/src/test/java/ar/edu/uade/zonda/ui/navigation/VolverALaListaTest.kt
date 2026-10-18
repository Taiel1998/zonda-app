package ar.edu.uade.zonda.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.detalle.AvisoRevisasteEnLasListas
import ar.edu.uade.zonda.ui.detalle.FormularioRevision
import ar.edu.uade.zonda.ui.detalle.PasoRevision
import ar.edu.uade.zonda.ui.detalle.RevisionPorDeshacer
import ar.edu.uade.zonda.ui.detalle.RutaDetalle
import ar.edu.uade.zonda.ui.detalle.VentanaDeshacer
import ar.edu.uade.zonda.ui.detalle.detalleAlerta
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Volver solo a la lista después de revisar (corrección 7): el detalle se cierra al confirmar, la
 * pantalla de la que se vino dice "Revisaste ..." y Deshacer vuelve a abrir el detalle. El grafo usa
 * las rutas reales y el aviso de la app, con pantallas simples en lugar de las de verdad (que
 * necesitan la base cifrada); el detalle de mentira confirma como el de verdad: abre la ventana y vuelve.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolverALaListaTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController

    private val alertas = FakeAlertasRepository()
    private val acciones = FakeAccionesPendientesRepository()

    // El tiempo de la ventana no corre en estas pruebas: el envío y el descarte en la base los prueba VentanaDeshacerTest.
    private val ventana = VentanaDeshacer(
        DeshacerRevision(alertas, acciones),
        SincronizarAlertas(FakeRedesRepository(listOf(Fixtures.red("red-1"))), alertas),
        FakeConectividad(),
        CoroutineScope(StandardTestDispatcher()),
    )

    private val hoja = FormularioRevision(
        paso = PasoRevision.MOTIVO,
        resultado = ResultadoRevision.ATAQUE_REAL,
        motivo = Motivo.INVESTIGANDO,
        comentario = "Sigo mañana.",
    )

    /** Arranca Zonda con [intento] y dibuja las tres listas, el detalle y el aviso de la app. */
    private fun abrir(intento: Intent = Intent(Intent.ACTION_MAIN)) {
        val actividad = Robolectric.buildActivity(ComponentActivity::class.java, intento).setup().get()
        actividad.setContent {
            ZondaTheme {
                val controlador = rememberNavController()
                nav = controlador
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = controlador, startDestination = Rutas.ESTADO) {
                        composable(Rutas.ESTADO) { Lista("Estado", controlador) }
                        composable(Rutas.ALERTAS) { Lista("Alertas", controlador) }
                        composable(Rutas.ALERTAS_DEL_RESUMEN) { Lista("Del Resumen", controlador) }
                        detalleAlerta(alVolver = { controlador.volver() }) { alertaId, _, alVolver ->
                            Column {
                                Text("Detalle de $alertaId")
                                Button(onClick = {
                                    ventana.abrir(RevisionPorDeshacer("red-1", alertaId, "pc-09 → srv-web", hoja, conConexion = true))
                                    alVolver()
                                }) { Text("Confirmar revisión") }
                            }
                        }
                    }
                    AvisoRevisasteEnLasListas(ventana = ventana, navController = controlador, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun Lista(nombre: String, controlador: NavHostController) {
        Column {
            Text("Lista $nombre")
            Button(onClick = { controlador.navigate(RutaDetalle.crear("a-1")) }) { Text("Abrir a-1") }
        }
    }

    /** Desde [lista]: abre a-1, la revisa, vuelve con el aviso y Deshacer reabre su detalle. */
    private fun revisarYDeshacerDesde(lista: String) {
        compose.onNodeWithText("Lista $lista").assertIsDisplayed()
        compose.onNodeWithText("Abrir a-1").performClick()
        compose.onNodeWithText("Confirmar revisión").performClick()

        compose.onNodeWithText("Lista $lista").assertIsDisplayed()
        compose.onNodeWithText("Revisaste pc-09 → srv-web.").assertIsDisplayed()

        compose.onNodeWithText("Deshacer").performClick()
        compose.onNodeWithText("Detalle de a-1").assertIsDisplayed()
        compose.onNodeWithText("Revisaste pc-09 → srv-web.").assertDoesNotExist()
        assertNull(ventana.abierta.value)
        // El detalle que se abre recupera la hoja como estaba.
        assertEquals(hoja, ventana.recuperarFormulario("a-1"))
    }

    @Test
    fun `desde Estado vuelve a Estado con el aviso y Deshacer reabre el detalle`() {
        abrir()
        revisarYDeshacerDesde("Estado")
    }

    @Test
    fun `desde Alertas vuelve a Alertas con el aviso y Deshacer reabre el detalle`() {
        abrir()
        compose.runOnIdle { nav.navigate(Rutas.ALERTAS) }
        revisarYDeshacerDesde("Alertas")
    }

    @Test
    fun `desde la lista del Resumen vuelve a esa lista con el aviso y Deshacer reabre el detalle`() {
        abrir()
        compose.runOnIdle { nav.navigate(Rutas.ALERTAS_DEL_RESUMEN) }
        revisarYDeshacerDesde("Del Resumen")
    }

    @Test
    fun `abierto desde un aviso de Telegram vuelve a Estado con el aviso`() {
        // Como lo abre la pantalla puente: el enlace del aviso en una tarea nueva, con Estado debajo.
        abrir(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://taiel1998.github.io/a/a-1?red=red-1&severidad=alta&servicio=ssh"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        compose.onNodeWithText("Detalle de a-1").assertIsDisplayed()

        compose.onNodeWithText("Confirmar revisión").performClick()

        compose.onNodeWithText("Lista Estado").assertIsDisplayed()
        compose.onNodeWithText("Revisaste pc-09 → srv-web.").assertIsDisplayed()
    }

    @Test
    fun `sin conexion el aviso dice que se envia cuando haya`() {
        abrir()
        compose.runOnIdle { ventana.abrir(RevisionPorDeshacer("red-1", "a-1", "pc-09 → srv-web", hoja, conConexion = false)) }

        compose.onNodeWithText("Revisaste pc-09 → srv-web. Se envía cuando haya conexión.").assertIsDisplayed()
    }

    @Test
    fun `el aviso no se muestra encima de otro detalle`() {
        abrir()
        compose.onNodeWithText("Abrir a-1").performClick()
        compose.onNodeWithText("Confirmar revisión").performClick()
        compose.onNodeWithText("Revisaste pc-09 → srv-web.").assertIsDisplayed()

        compose.runOnIdle { nav.navigate(RutaDetalle.crear("a-2")) }
        compose.onNodeWithText("Detalle de a-2").assertIsDisplayed()
        compose.onNodeWithText("Revisaste pc-09 → srv-web.").assertDoesNotExist()
    }
}
