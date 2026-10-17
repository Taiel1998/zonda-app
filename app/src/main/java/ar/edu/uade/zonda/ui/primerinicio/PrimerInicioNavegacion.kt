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
import ar.edu.uade.zonda.ui.telegram.TelegramInicialScreen
import ar.edu.uade.zonda.ui.viewModelDeZonda
import kotlinx.coroutines.launch

/**
 * El primer inicio como grafo anidado (RF05; informe, 11.5): bienvenida (las condiciones se abren en
 * una hoja), tutorial, avisos, pareo y Telegram, que se puede posponer con "Ahora no". Todas las
 * pantallas comparten un [PrimerInicioViewModel], que vive mientras el grafo esté en la pila.
 *
 * Desde la 1.0.5 no tiene el paso de severidades: son configuración de toda la red y las cambia un
 * administrador desde Ajustes, con su motivo (decisión 39). "Continuar" de los avisos conecta con la red.
 */
fun NavGraphBuilder.primerInicio(navController: NavHostController) {
    navigation(startDestination = Rutas.BIENVENIDA, route = Rutas.PRIMER_INICIO) {
        composable(Rutas.BIENVENIDA) { entrada ->
            val viewModel = primerInicioViewModel(navController, entrada)
            BienvenidaScreen(
                alEmpezar = { navController.navigate(Rutas.TUTORIAL) },
                alOmitirIntroduccion = {
                    viewModel.terminarIntroduccion()
                    navController.navigate(Rutas.CONEXION_INICIAL)
                },
            )
        }
        composable(Rutas.TUTORIAL) {
            TutorialScreen(
                alOmitir = { navController.navigate(Rutas.AVISOS) },
                alTerminar = { navController.navigate(Rutas.AVISOS) },
            )
        }
        composable(Rutas.AVISOS) { entrada ->
            val viewModel = primerInicioViewModel(navController, entrada)
            val avisos by viewModel.avisos.collectAsStateWithLifecycle()
            AvisosScreen(
                avisos = avisos,
                alCambiar = viewModel::cambiarAviso,
                alContinuar = {
                    viewModel.terminarIntroduccion()
                    navController.navigate(Rutas.CONEXION_INICIAL)
                },
            )
        }
        composable(Rutas.CONEXION_INICIAL) {
            // Sin flecha, como en el Figma (35-432): el botón Volver del teléfono igual vuelve atrás.
            ConexionScreen(
                alTerminar = { navController.navigate(Rutas.TELEGRAM_INICIAL) },
                alVolver = null,
                alVerDetallesTecnicos = { navController.navigate(Rutas.DETALLES_TECNICOS) },
            )
        }
        composable(Rutas.TELEGRAM_INICIAL) {
            // Con Telegram conectado o con "Ahora no", Estado queda como única pantalla de la pila.
            TelegramInicialScreen(alTerminar = { navController.irAPrincipal() })
        }
    }
}

/** El ViewModel del grafo del primer inicio, el mismo para todas sus pantallas. */
@Composable
private fun primerInicioViewModel(navController: NavHostController, entrada: NavBackStackEntry): PrimerInicioViewModel {
    val grafo = remember(entrada) { navController.getBackStackEntry(Rutas.PRIMER_INICIO) }
    return viewModelDeZonda(dueño = grafo) { contenedor -> PrimerInicioViewModel(contenedor.preferencias) }
}
