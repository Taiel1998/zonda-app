package ar.edu.uade.zonda.ui.navigation

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.ajustes.AjustesScreen
import ar.edu.uade.zonda.ui.ajustes.AjustesViewModel
import ar.edu.uade.zonda.ui.ajustes.AvisosPorSeveridadScreen
import ar.edu.uade.zonda.ui.ajustes.CondicionesScreen
import ar.edu.uade.zonda.ui.ajustes.DetallesTecnicosScreen
import ar.edu.uade.zonda.ui.ajustes.RedesScreen
import ar.edu.uade.zonda.ui.ajustes.ajustesViewModel
import ar.edu.uade.zonda.ui.alertas.AlertasScreen
import ar.edu.uade.zonda.ui.alertas.alertasViewModel
import ar.edu.uade.zonda.ui.conexion.ConexionScreen
import ar.edu.uade.zonda.ui.detalle.RutaDetalle
import ar.edu.uade.zonda.ui.detalle.detalleAlerta
import ar.edu.uade.zonda.ui.estado.EstadoScreen
import ar.edu.uade.zonda.ui.inicio.ArranqueViewModel
import ar.edu.uade.zonda.ui.inicio.DestinoInicial
import ar.edu.uade.zonda.ui.primerinicio.SeveridadesScreen
import ar.edu.uade.zonda.ui.primerinicio.TutorialScreen
import ar.edu.uade.zonda.ui.primerinicio.primerInicio
import ar.edu.uade.zonda.ui.recorrido.CapaRecorrido
import ar.edu.uade.zonda.ui.recorrido.LocalRegistroRecorrido
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.PantallaRecorrido
import ar.edu.uade.zonda.ui.recorrido.RecorridoViewModel
import ar.edu.uade.zonda.ui.recorrido.RegistroRecorrido
import ar.edu.uade.zonda.ui.recorrido.objetivoDelRecorrido
import ar.edu.uade.zonda.ui.resumen.AlertasDelResumenScreen
import ar.edu.uade.zonda.ui.resumen.ResumenScreen
import ar.edu.uade.zonda.ui.resumen.resumenViewModel
import ar.edu.uade.zonda.ui.telegram.TelegramScreen
import ar.edu.uade.zonda.ui.theme.etiquetaBarra
import ar.edu.uade.zonda.ui.viewModelDeZonda

/**
 * Grafo de navegación de la app (informe, sección 11.11; plan, sección 4.4).
 *
 * - Entrada por el ícono: sin red pareada, el primer inicio (o Conexión si la introducción ya se hizo);
 *   con red, Estado.
 * - Entradas externas: el enlace del aviso de Telegram abre el detalle de esa alerta; la notificación
 *   local abre la pestaña Alertas o el detalle. El enlace entra por PuenteDelAvisoActivity, que abre
 *   Zonda en su propia tarea con Estado debajo del detalle, también si se toca dos veces el mismo
 *   aviso (cambio 23). Si el detalle quedara como única pantalla, la flecha abre Zonda en su tarea,
 *   en Estado ([volver]).
 * - Una barra inferior con Estado, Alertas y Ajustes, y una pila de pantallas que se cierran con Volver
 *   (el Resumen se abre desde Estado). La barra solo se ve en los tres destinos principales.
 * - El recorrido de once pasos se muestra sobre las pestañas la primera vez (RF05).
 */
@Composable
fun ZondaNavHost(
    navController: NavHostController = rememberNavController(),
    arranque: ArranqueViewModel = viewModelDeZonda { ArranqueViewModel(it.redes, it.preferencias) },
    recorrido: RecorridoViewModel = viewModelDeZonda { RecorridoViewModel(it.preferencias, it.redes, it.alertas) },
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
    val alertaDelRecorrido by recorrido.alertaDelDetalle.collectAsStateWithLifecycle()
    val enDetalle = rutaActual == RutaDetalle.PATRON
    val enResumen = rutaActual == Rutas.RESUMEN
    val registroRecorrido = remember { RegistroRecorrido() }

    // El recorrido navega solo a la pantalla de cada paso: el del detalle abre la primera alerta y los
    // del Resumen lo abren encima de Estado, como el ícono de barras.
    LaunchedEffect(pasoRecorrido, rutaActual) {
        val paso = pasoRecorrido ?: return@LaunchedEffect
        if (!enPestania && !enDetalle && !enResumen) return@LaunchedEffect
        val alerta = alertaDelRecorrido
        if (paso.pantalla == PantallaRecorrido.DETALLE && alerta != null) {
            if (!enDetalle) navController.navigate(RutaDetalle.crear(alerta))
            return@LaunchedEffect
        }
        if (paso.pantalla == PantallaRecorrido.RESUMEN) {
            if (!enResumen) {
                if (enDetalle) navController.popBackStack()
                navController.irAPestania(Rutas.ESTADO)
                navController.navigate(Rutas.RESUMEN)
            }
            return@LaunchedEffect
        }
        val ruta = rutaDelRecorrido(paso.pantalla)
        // Al dejar el detalle o el Resumen se cierran, para que no se vuelvan a abrir después.
        if (enDetalle || enResumen) navController.popBackStack()
        if (rutaActual != ruta) navController.irAPestania(ruta)
    }

    /** Sin ninguna red pareada (se quitó la última), la app vuelve a Conexión como única pantalla. */
    val irAConexionSinVolver: () -> Unit = {
        navController.navigate(Rutas.CONEXION) { popUpTo(navController.graph.id) { inclusive = true } }
    }

    // Un solo Scaffold con la barra inferior. No suma insets: cada pantalla maneja los suyos con su
    // barra superior, y las pestañas no vuelven a sumar el de abajo (lo cubre la barra inferior).
    // El recorrido va encima de todo, también de la barra inferior (Figma 107-3321 a 107-4731).
    CompositionLocalProvider(LocalRegistroRecorrido provides registroRecorrido) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (enPestania) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                Destino.barraInferior.forEach { destino ->
                                    val elegida = rutaActual == destino.ruta
                                    NavigationBarItem(
                                        selected = elegida,
                                        onClick = { navController.irAPestania(destino.ruta) },
                                        modifier = if (destino.ruta == Rutas.ALERTAS) Modifier.objetivoDelRecorrido(ObjetivoRecorrido.PESTANIA_ALERTAS) else Modifier,
                                        icon = { Icon(destino.icono, contentDescription = null) },
                                        // Figma: la pestaña elegida en SemiBold 12 y las otras en Medium 12.
                                        label = {
                                            Text(
                                                text = stringResource(destino.titulo),
                                                style = MaterialTheme.typography.etiquetaBarra,
                                                fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Medium,
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { relleno ->
                Box(modifier = Modifier.padding(relleno)) {
                    NavHost(navController = navController, startDestination = rutaDeInicio) {
                        primerInicio(navController)
                        composable(Rutas.CONEXION) {
                            ConexionScreen(
                                alTerminar = { navController.irAPrincipal() },
                                alVolver = if (navController.previousBackStackEntry == null) null else ({ navController.popBackStack() }),
                                alVerDetallesTecnicos = { navController.navigate(Rutas.DETALLES_TECNICOS) },
                            )
                        }
                        composable(Rutas.ESTADO) {
                            EstadoScreen(
                                alAbrirResumen = { navController.navigate(Rutas.RESUMEN) },
                                alAbrirDetalle = { alertaId -> navController.navigate(RutaDetalle.crear(alertaId)) },
                                alVerTodas = {
                                    // La lista completa, como el Figma (de 1-3 a 2-3), aunque Alertas tuviera un filtro puesto.
                                    navController.irAPestania(Rutas.ALERTAS)
                                    navController.currentBackStackEntry?.savedStateHandle?.set(VER_TODAS, true)
                                },
                                alVerNoAplicadas = {
                                    // "Ver" del aviso con varias revisiones no aplicadas: Alertas con esas (cambio 7).
                                    navController.irAPestania(Rutas.ALERTAS)
                                    navController.currentBackStackEntry?.savedStateHandle?.set(VER_NO_APLICADAS, true)
                                },
                                alConectar = { navController.navigate(Rutas.CONEXION) },
                            )
                        }
                        composable(Rutas.RESUMEN) {
                            ResumenScreen(
                                alVolver = { navController.popBackStack() },
                                alVerAlertas = { navController.navigate(Rutas.ALERTAS_DEL_RESUMEN) },
                            )
                        }
                        // "Ver estas alertas" (cambio 16): usa el ViewModel del Resumen, que queda abajo con sus filtros.
                        composable(Rutas.ALERTAS_DEL_RESUMEN) { entrada ->
                            val dueño = remember(entrada) { navController.getBackStackEntry(Rutas.RESUMEN) }
                            AlertasDelResumenScreen(
                                viewModel = resumenViewModel(dueño),
                                alAbrirDetalle = { alertaId -> navController.navigate(RutaDetalle.crear(alertaId)) },
                                alVolver = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = Rutas.ALERTAS,
                            arguments = listOf(EnlacesInternos.ARG_RED, EnlacesInternos.ARG_ESTADO, EnlacesInternos.ARG_SEVERIDAD, EnlacesInternos.ARG_ENVIO).map { nombre ->
                                navArgument(nombre) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            },
                            deepLinks = listOf(navDeepLink { uriPattern = EnlacesInternos.PATRON_ALERTAS }),
                        ) { entrada ->
                            // Lo que pide el enlace se aplica una sola vez: volver a la pestaña no lo repite.
                            val filtroPedido = remember(entrada) {
                                if (entrada.savedStateHandle.get<Boolean>(ENLACE_APLICADO) == true) {
                                    null
                                } else {
                                    entrada.savedStateHandle[ENLACE_APLICADO] = true
                                    entrada.arguments?.getString(EnlacesInternos.ARG_RED)?.let(arranque::activarRed)
                                    filtroDelEnlace(entrada.arguments)
                                }
                            }
                            val viewModel = alertasViewModel()
                            val verTodas by entrada.savedStateHandle.getStateFlow(VER_TODAS, false).collectAsStateWithLifecycle()
                            LaunchedEffect(verTodas) {
                                if (verTodas) {
                                    viewModel.quitarFiltros()
                                    entrada.savedStateHandle[VER_TODAS] = false
                                }
                            }
                            val verNoAplicadas by entrada.savedStateHandle.getStateFlow(VER_NO_APLICADAS, false).collectAsStateWithLifecycle()
                            LaunchedEffect(verNoAplicadas) {
                                if (verNoAplicadas) {
                                    viewModel.aplicarFiltro(FiltroAlertas(soloNoAplicadas = true))
                                    entrada.savedStateHandle[VER_NO_APLICADAS] = false
                                }
                            }
                            AlertasScreen(
                                alAbrirDetalle = { alertaId -> navController.navigate(RutaDetalle.crear(alertaId)) },
                                alConectarDeNuevo = { navController.navigate(Rutas.CONEXION) },
                                filtroPedido = filtroPedido,
                                viewModel = viewModel,
                            )
                        }
                        composable(Rutas.AJUSTES) {
                            AjustesScreen(
                                alAbrirRedes = { navController.navigate(Rutas.REDES) },
                                alAbrirDetallesTecnicos = { navController.navigate(Rutas.DETALLES_TECNICOS) },
                                alAbrirAvisos = { navController.navigate(Rutas.AVISOS_POR_SEVERIDAD) },
                                alAbrirSeveridades = { navController.navigate(Rutas.SEVERIDADES) },
                                alAbrirTelegram = { navController.navigate(Rutas.TELEGRAM) },
                                alVerIntroduccion = { navController.navigate(Rutas.INTRODUCCION) },
                                alVerCondiciones = { navController.navigate(Rutas.CONDICIONES_DE_USO) },
                                alAgregarRed = { navController.navigate(Rutas.CONEXION) },
                                alQuedarSinRedes = irAConexionSinVolver,
                            )
                        }
                        composable(Rutas.REDES) { entrada ->
                            RedesScreen(
                                viewModel = viewModelDeAjustes(navController, entrada),
                                alAgregarRed = { navController.navigate(Rutas.CONEXION) },
                                alConectarDeNuevo = { navController.navigate(Rutas.CONEXION) },
                                alQuedarSinRedes = irAConexionSinVolver,
                                alVolver = { navController.popBackStack() },
                            )
                        }
                        composable(Rutas.DETALLES_TECNICOS) {
                            DetallesTecnicosScreen(alVolver = { navController.popBackStack() })
                        }
                        composable(Rutas.TELEGRAM) {
                            TelegramScreen(alVolver = { navController.popBackStack() })
                        }
                        composable(Rutas.AVISOS_POR_SEVERIDAD) { entrada ->
                            AvisosPorSeveridadScreen(
                                viewModel = viewModelDeAjustes(navController, entrada),
                                alCambiarSeveridades = { navController.navigate(Rutas.SEVERIDADES) },
                                alVolver = { navController.popBackStack() },
                            )
                        }
                        composable(Rutas.SEVERIDADES) { entrada ->
                            val viewModel = viewModelDeAjustes(navController, entrada)
                            val estado by viewModel.estado.collectAsStateWithLifecycle()
                            SeveridadesScreen(
                                severidades = estado.borradorSeveridades ?: estado.severidades,
                                alCambiar = viewModel::cambiarSeveridadDelBorrador,
                                textoBoton = stringResource(R.string.ajustes_guardar_severidades),
                                titulo = stringResource(R.string.ajustes_severidad_por_tipo),
                                intro = stringResource(R.string.ajustes_severidades_intro),
                                alGuardar = {
                                    viewModel.guardarSeveridades()
                                    navController.popBackStack()
                                },
                                alVolver = {
                                    viewModel.descartarBorrador()
                                    navController.popBackStack()
                                },
                            )
                        }
                        // Desde Ajustes: "Omitir" vuelve y "Configurar mis avisos" abre los avisos por severidad.
                        composable(Rutas.INTRODUCCION) {
                            TutorialScreen(
                                alOmitir = { navController.popBackStack() },
                                alTerminar = {
                                    navController.navigate(Rutas.AVISOS_POR_SEVERIDAD) { popUpTo(Rutas.INTRODUCCION) { inclusive = true } }
                                },
                            )
                        }
                        composable(Rutas.CONDICIONES_DE_USO) {
                            CondicionesScreen(alVolver = { navController.popBackStack() })
                        }
                        // El detalle puede ser la única pantalla (abierto desde Telegram): la flecha usa volver().
                        detalleAlerta(alVolver = { navController.volver() }, alPedirRed = arranque::activarRed)
                    }
                }
            }
            val paso = pasoRecorrido
            if (paso != null && (enPestania || enDetalle || enResumen)) {
                CapaRecorrido(paso = paso, registro = registroRecorrido, alSiguiente = recorrido::siguiente, alOmitir = recorrido::terminar)
            }
        }
    }
}

/** La ruta de cada pantalla del recorrido: el detalle va sobre Alertas y el Resumen sobre Estado. */
private fun rutaDelRecorrido(pantalla: PantallaRecorrido): String = when (pantalla) {
    PantallaRecorrido.ESTADO -> Rutas.ESTADO
    PantallaRecorrido.RESUMEN -> Rutas.RESUMEN
    PantallaRecorrido.ALERTAS, PantallaRecorrido.DETALLE -> Rutas.ALERTAS
    PantallaRecorrido.AJUSTES -> Rutas.AJUSTES
}

private fun rutaDe(destino: DestinoInicial): String = when (destino) {
    DestinoInicial.PRINCIPAL -> Rutas.ESTADO
    DestinoInicial.PRIMER_INICIO -> Rutas.PRIMER_INICIO
    DestinoInicial.CONEXION -> Rutas.CONEXION
}

/**
 * Redes, Avisos por severidad y Severidad por tipo de ataque usan el ViewModel de Ajustes, que es desde donde
 * se llega: así comparten los borradores y los avisos.
 */
@Composable
private fun viewModelDeAjustes(navController: NavHostController, entrada: NavBackStackEntry): AjustesViewModel {
    val dueño = remember(entrada) { navController.getBackStackEntry(Rutas.AJUSTES) }
    return ajustesViewModel(dueño)
}

private const val ENLACE_APLICADO = "enlace_aplicado"

/** "Ver todas" de Estado: Alertas quita los filtros al llegar. */
private const val VER_TODAS = "ver_todas"

/** "Ver" del aviso de Estado con varias revisiones no aplicadas: Alertas muestra solo esas (cambio 7). */
private const val VER_NO_APLICADAS = "ver_no_aplicadas"

/**
 * El filtro de un enlace interno (`estado=sin_revisar&severidad=alta`, o `envio=conflicto` para las
 * revisiones que no se aplicaron), o null si no trae ninguno.
 */
private fun filtroDelEnlace(argumentos: Bundle?): FiltroAlertas? {
    val estado = argumentos?.getString(EnlacesInternos.ARG_ESTADO)?.let { valor -> FiltroEstado.entries.firstOrNull { it.name.equals(valor, ignoreCase = true) } }
    val severidad = argumentos?.getString(EnlacesInternos.ARG_SEVERIDAD)?.let { valor -> Severidad.entries.firstOrNull { it.name.equals(valor, ignoreCase = true) } }
    val noAplicadas = argumentos?.getString(EnlacesInternos.ARG_ENVIO) == EnlacesInternos.ENVIO_CONFLICTO
    if (estado == null && severidad == null && !noAplicadas) return null
    return FiltroAlertas(estado = estado ?: FiltroEstado.TODAS, severidades = setOfNotNull(severidad), soloNoAplicadas = noAplicadas)
}
