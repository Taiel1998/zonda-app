package ar.edu.uade.zonda.ui.primerinicio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.conexion.ConexionScreen
import ar.edu.uade.zonda.ui.navigation.Rutas
import ar.edu.uade.zonda.ui.navigation.irAPrincipal
import ar.edu.uade.zonda.ui.viewModelDeZonda
import kotlinx.coroutines.launch

/**
 * El primer inicio como grafo anidado (RF05; informe, 11.5): bienvenida con las condiciones, tutorial,
 * avisos, severidades y pareo. Todas las pantallas comparten un [PrimerInicioViewModel], que vive
 * mientras el grafo esté en la pila. Telegram se agrega en la fase 10.
 */
fun NavGraphBuilder.primerInicio(navController: NavHostController) {
    navigation(startDestination = Rutas.BIENVENIDA, route = Rutas.PRIMER_INICIO) {
        composable(Rutas.BIENVENIDA) { entrada ->
            val viewModel = primerInicioViewModel(navController, entrada)
            BienvenidaScreen(
                alVerCondiciones = { navController.navigate(Rutas.CONDICIONES) },
                alEmpezar = { navController.navigate(Rutas.TUTORIAL) },
                alOmitirIntroduccion = {
                    viewModel.terminarIntroduccion()
                    navController.navigate(Rutas.CONEXION_INICIAL)
                },
            )
        }
        composable(Rutas.CONDICIONES) {
            CondicionesScreen(alVolver = { navController.popBackStack() })
        }
        composable(Rutas.TUTORIAL) {
            TutorialScreen(alTerminar = { navController.navigate(Rutas.AVISOS) })
        }
        composable(Rutas.AVISOS) { entrada ->
            val viewModel = primerInicioViewModel(navController, entrada)
            val avisos by viewModel.avisos.collectAsStateWithLifecycle()
            AvisosScreen(
                avisos = avisos,
                alCambiar = viewModel::cambiarAviso,
                alContinuar = { navController.navigate(Rutas.SEVERIDADES_INICIALES) },
                alVolver = { navController.popBackStack() },
            )
        }
        composable(Rutas.SEVERIDADES_INICIALES) { entrada ->
            val viewModel = primerInicioViewModel(navController, entrada)
            val severidades by viewModel.severidades.collectAsStateWithLifecycle()
            SeveridadesScreen(
                severidades = severidades,
                alCambiar = viewModel::cambiarSeveridad,
                textoBoton = stringResource(R.string.severidades_guardar),
                alGuardar = {
                    viewModel.terminarIntroduccion()
                    navController.navigate(Rutas.CONEXION_INICIAL)
                },
                alVolver = { navController.popBackStack() },
            )
        }
        composable(Rutas.CONEXION_INICIAL) { entrada ->
            val viewModel = primerInicioViewModel(navController, entrada)
            val alcance = rememberCoroutineScope()
            ConexionScreen(
                alTerminar = { red ->
                    alcance.launch {
                        viewModel.alParear(red)
                        navController.irAPrincipal()
                    }
                },
                alVolver = { navController.popBackStack() },
            )
        }
    }
}

/** El ViewModel del grafo del primer inicio, el mismo para todas sus pantallas. */
@Composable
private fun primerInicioViewModel(navController: NavHostController, entrada: NavBackStackEntry): PrimerInicioViewModel {
    val grafo = remember(entrada) { navController.getBackStackEntry(Rutas.PRIMER_INICIO) }
    return viewModelDeZonda(dueño = grafo) { contenedor ->
        PrimerInicioViewModel(contenedor.preferencias, contenedor.cambiarSeveridades, contenedor.sincronizarAlertas, contenedor.alcanceDeLaApp)
    }
}
