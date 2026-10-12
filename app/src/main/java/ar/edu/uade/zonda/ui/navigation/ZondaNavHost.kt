package ar.edu.uade.zonda.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.zonda.ui.ajustes.AjustesScreen
import ar.edu.uade.zonda.ui.alertas.AlertasScreen
import ar.edu.uade.zonda.ui.conexion.ConexionScreen
import ar.edu.uade.zonda.ui.detalle.RutaDetalle
import ar.edu.uade.zonda.ui.detalle.detalleAlerta
import ar.edu.uade.zonda.ui.estado.EstadoScreen
import ar.edu.uade.zonda.ui.inicio.ArranqueViewModel
import ar.edu.uade.zonda.ui.inicio.DestinoInicial
import ar.edu.uade.zonda.ui.primerinicio.primerInicio
import ar.edu.uade.zonda.ui.recorrido.RecorridoViewModel
import ar.edu.uade.zonda.ui.recorrido.TarjetaRecorrido
import ar.edu.uade.zonda.ui.viewModelDeZonda

/**
 * Grafo de navegación de la app (informe, sección 11.11; plan, sección 4.4).
 *
 * - Entrada por el ícono: sin red pareada, el primer inicio (o Conexión si la introducción ya se hizo);
 *   con red, Estado.
 * - Entrada por el enlace del aviso: el detalle de esa alerta.
 * - Una barra inferior con Estado, Alertas y Ajustes, y una pila de pantallas que se cierran con Volver.
 *   La barra solo se ve en los tres destinos principales.
 * - El recorrido de once pasos se muestra sobre las pestañas la primera vez (RF05).
 */
@Composable
fun ZondaNavHost(
    navController: NavHostController = rememberNavController(),
    arranque: ArranqueViewModel = viewModelDeZonda { ArranqueViewModel(it.redes, it.preferencias) },
    recorrido: RecorridoViewModel = viewModelDeZonda { RecorridoViewModel(it.preferencias, it.redes) },
) {
    val destinoInicial by arranque.destinoInicial.collectAsStateWithLifecycle()
    val inicio = destinoInicial
    if (inicio == null) {
        // La primera vez, abrir la base cifrada tarda unos segundos.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // El destino de inicio se fija una vez: después del pareo se navega a Estado, no se rearma el grafo.
    val rutaDeInicio = remember { rutaDe(inicio) }

    val entradaActual by navController.currentBackStackEntryAsState()
    val rutaActual = entradaActual?.destination?.route
    val enPestania = Destino.barraInferior.any { it.ruta == rutaActual }
    val pasoRecorrido by recorrido.paso.collectAsStateWithLifecycle()

    // Cada paso del recorrido se muestra sobre su pestaña.
    LaunchedEffect(pasoRecorrido, enPestania) {
        val paso = pasoRecorrido ?: return@LaunchedEffect
        if (enPestania && rutaActual != paso.ruta) navController.irAPestania(paso.ruta)
    }

    Scaffold(
        bottomBar = {
            if (enPestania) {
                NavigationBar {
                    Destino.barraInferior.forEach { destino ->
                        NavigationBarItem(
                            selected = rutaActual == destino.ruta,
                            onClick = { navController.irAPestania(destino.ruta) },
                            icon = { Icon(destino.icono, contentDescription = null) },
                            label = { Text(stringResource(destino.titulo)) },
                        )
                    }
                }
            }
        },
    ) { relleno ->
        Box(modifier = Modifier.padding(relleno)) {
            NavHost(navController = navController, startDestination = rutaDeInicio) {
                primerInicio(navController)
                composable(Rutas.CONEXION) {
                    ConexionScreen(alTerminar = { navController.irAPrincipal() }, alVolver = null)
                }
                composable(Rutas.ESTADO) { EstadoScreen() }
                composable(Rutas.ALERTAS) {
                    AlertasScreen(alAbrirDetalle = { alertaId -> navController.navigate(RutaDetalle.crear(alertaId)) })
                }
                composable(Rutas.AJUSTES) { AjustesScreen() }
                detalleAlerta(alVolver = { navController.popBackStack() })
            }
            val paso = pasoRecorrido
            if (paso != null && enPestania) {
                TarjetaRecorrido(paso = paso, alSiguiente = recorrido::siguiente, alOmitir = recorrido::terminar)
            }
        }
    }
}

private fun rutaDe(destino: DestinoInicial): String = when (destino) {
    DestinoInicial.PRINCIPAL -> Rutas.ESTADO
    DestinoInicial.PRIMER_INICIO -> Rutas.PRIMER_INICIO
    DestinoInicial.CONEXION -> Rutas.CONEXION
}
