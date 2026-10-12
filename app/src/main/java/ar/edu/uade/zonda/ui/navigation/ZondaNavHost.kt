package ar.edu.uade.zonda.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.zonda.ui.ajustes.AjustesScreen
import ar.edu.uade.zonda.ui.alertas.AlertasScreen
import ar.edu.uade.zonda.ui.detalle.RutaDetalle
import ar.edu.uade.zonda.ui.detalle.detalleAlerta
import ar.edu.uade.zonda.ui.estado.EstadoScreen

/**
 * Grafo de navegación de la app: una barra inferior con tres destinos y una pila de pantallas
 * que se cierran con Volver (informe, sección 11.11). La barra solo se muestra en los tres destinos
 * principales; en el detalle y en las pantallas de la pila desaparece.
 */
@Composable
fun ZondaNavHost(navController: NavHostController = rememberNavController()) {
    val entradaActual by navController.currentBackStackEntryAsState()
    val rutaActual = entradaActual?.destination?.route
    val mostrarBarra = Destino.barraInferior.any { it.ruta == rutaActual }

    Scaffold(
        bottomBar = {
            if (mostrarBarra) {
                NavigationBar {
                    Destino.barraInferior.forEach { destino ->
                        NavigationBarItem(
                            selected = rutaActual == destino.ruta,
                            onClick = {
                                navController.navigate(destino.ruta) {
                                    // Vuelve al inicio del grafo sin apilar copias y conserva el estado de cada pestaña.
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destino.icono, contentDescription = null) },
                            label = { Text(stringResource(destino.titulo)) },
                        )
                    }
                }
            }
        },
    ) { relleno ->
        NavHost(
            navController = navController,
            startDestination = Destino.Estado.ruta,
            modifier = Modifier.padding(relleno),
        ) {
            composable(Destino.Estado.ruta) { EstadoScreen() }
            composable(Destino.Alertas.ruta) {
                AlertasScreen(alAbrirDetalle = { alertaId -> navController.navigate(RutaDetalle.crear(alertaId)) })
            }
            composable(Destino.Ajustes.ruta) { AjustesScreen() }
            detalleAlerta(alVolver = { navController.popBackStack() })
        }
    }
}
