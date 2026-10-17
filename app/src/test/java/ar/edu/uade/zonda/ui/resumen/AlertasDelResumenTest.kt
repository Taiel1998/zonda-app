package ar.edu.uade.zonda.ui.resumen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.usecase.ObservarResumen
import ar.edu.uade.zonda.ui.navigation.Rutas
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Ver estas alertas" del Resumen (cambio 16; Figma 463-1669): la lista con sus tarjetas y la línea de
 * dónde salen, y la vuelta: del detalle a la lista y de la lista al Resumen, con los filtros como estaban.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h917dp")
class AlertasDelResumenTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneOffset.UTC

    private fun alertas() = listOf(
        Fixtures.alerta("fuerza-1", tipoAtaque = TipoAtaque.FUERZA_BRUTA),
        Fixtures.alerta("escaneo-1", tipoAtaque = TipoAtaque.ESCANEO, servicio = "smb", creadaEn = Fixtures.AHORA.minus(Duration.ofHours(3))),
    )

    @Test
    fun `la lista muestra cuantas son, de donde salen y sus tarjetas, y tocar una abre su detalle`() {
        val resumen = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, Fixtures.AHORA, utc, FiltroResumen(tipos = setOf(TipoAtaque.ESCANEO)))
        val abiertas = mutableListOf<String>()
        compose.setContent {
            ZondaTheme {
                AlertasDelResumenContenido(
                    estado = ResumenUiState(contenido = ContenidoResumen.Datos(resumen), filtro = resumen.filtro),
                    alAbrirDetalle = { abiertas += it },
                    alVolver = {},
                    zona = utc,
                )
            }
        }

        compose.onNodeWithText("1 alerta").assertIsDisplayed()
        compose.onNodeWithText("Del Resumen: del 06/10 al 12/10 · escaneo de puertos").assertIsDisplayed()
        compose.onNodeWithText("SMB · 22").performClick()

        assertEquals(listOf("escaneo-1"), abiertas)
    }

    @Test
    fun `sin alertas con esos filtros lo dice`() {
        val resumen = ResumenActividad.calcular(alertas(), PeriodoResumen.SIETE_DIAS, Fixtures.AHORA, utc, FiltroResumen(tipos = setOf(TipoAtaque.DDOS)))
        compose.setContent {
            ZondaTheme {
                AlertasDelResumenContenido(
                    estado = ResumenUiState(contenido = ContenidoResumen.Datos(resumen), filtro = resumen.filtro),
                    alAbrirDetalle = {},
                    alVolver = {},
                    zona = utc,
                )
            }
        }

        compose.onNodeWithText("0 alertas").assertIsDisplayed()
        compose.onNodeWithText("Ninguna alerta con estos filtros").assertIsDisplayed()
    }

    @Test
    fun `de la hoja a la lista, al detalle, y de vuelta al Resumen con los filtros como estaban`() {
        val redes = FakeRedesRepository().apply { agregar(Fixtures.red("red-1")) }
        val repo = FakeAlertasRepository().apply { agregar(*alertas().toTypedArray()) }
        lateinit var nav: NavHostController
        compose.setContent {
            ZondaTheme {
                val controlador = rememberNavController()
                nav = controlador
                val fabrica = remember {
                    viewModelFactory {
                        initializer { ResumenViewModel(ObservarResumen(redes, repo, reloj = { Fixtures.AHORA }, zona = { utc }), redes, FakeConectividad()) }
                    }
                }
                NavHost(navController = controlador, startDestination = Rutas.RESUMEN) {
                    composable(Rutas.RESUMEN) {
                        val viewModel: ResumenViewModel = viewModel(factory = fabrica)
                        val estado by viewModel.estado.collectAsStateWithLifecycle()
                        ResumenContenido(
                            estado = estado,
                            alVolver = {},
                            alElegirPeriodo = viewModel::elegirPeriodo,
                            alTocar = viewModel::tocar,
                            alCompartir = {},
                            alAplicarFiltro = viewModel::aplicarFiltro,
                            alVerAlertas = { filtro ->
                                viewModel.aplicarFiltro(filtro)
                                controlador.navigate(Rutas.ALERTAS_DEL_RESUMEN)
                            },
                            zona = utc,
                        )
                    }
                    composable(Rutas.ALERTAS_DEL_RESUMEN) { entrada ->
                        val dueño = remember(entrada) { controlador.getBackStackEntry(Rutas.RESUMEN) }
                        val viewModel: ResumenViewModel = viewModel(viewModelStoreOwner = dueño, factory = fabrica)
                        AlertasDelResumenScreen(
                            viewModel = viewModel,
                            alAbrirDetalle = { id -> controlador.navigate("detalle/$id") },
                            alVolver = { controlador.popBackStack() },
                        )
                    }
                    composable("detalle/{id}") { entrada ->
                        Column {
                            Button(onClick = { controlador.popBackStack() }) { Text("Volver") }
                            Text("Detalle de ${entrada.arguments?.getString("id")}")
                        }
                    }
                }
            }
        }

        compose.onNodeWithText("Filtros").performClick()
        compose.onNode(hasText("Escaneo de puertos") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performScrollTo().performClick()
        compose.onNodeWithText("Ver esta alerta en la lista →").performScrollTo().performClick()

        compose.onNodeWithText("1 alerta").assertIsDisplayed()
        compose.onNodeWithText("SMB · 22").performClick()
        compose.onNodeWithText("Detalle de escaneo-1").assertIsDisplayed()

        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithText("1 alerta").assertIsDisplayed()
        compose.runOnIdle { nav.popBackStack() }

        compose.onNodeWithText("Filtros (1)").assertIsDisplayed()
        compose.onNodeWithText("Mostrando escaneo de puertos: 1 alerta. Tocá de nuevo para sacarlo.").performScrollTo().assertIsDisplayed()
    }
}
