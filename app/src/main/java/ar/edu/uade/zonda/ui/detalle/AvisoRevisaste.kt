package ar.edu.uade.zonda.ui.detalle

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.navigation.Rutas

/** Las pantallas a las que vuelve el detalle después de revisar, que muestran el "Revisaste ...". */
val RUTAS_CON_AVISO_REVISASTE = setOf(Rutas.ESTADO, Rutas.ALERTAS, Rutas.ALERTAS_DEL_RESUMEN)

/**
 * El "Revisaste ..." de la app (corrección 7): se ve mientras la [ventana] de Deshacer esté abierta y
 * se esté en Estado, Alertas o la lista del Resumen. Deshacer descarta la revisión y vuelve a abrir
 * el detalle de esa alerta, que abre la hoja en el paso 2 como estaba (como Gmail, que al deshacer
 * vuelve a abrir el mensaje).
 */
@Composable
fun AvisoRevisasteEnLasListas(ventana: VentanaDeshacer, navController: NavHostController, modifier: Modifier = Modifier) {
    val abierta by ventana.abierta.collectAsStateWithLifecycle()
    val entrada by navController.currentBackStackEntryAsState()
    val enUnaLista = entrada?.destination?.route in RUTAS_CON_AVISO_REVISASTE
    AvisoRevisaste(
        revision = abierta.takeIf { enUnaLista },
        alDeshacer = { ventana.deshacer()?.let { navController.navigate(RutaDetalle.crear(it.alertaId)) } },
        modifier = modifier,
    )
}

/**
 * "Revisaste pc-rrhh → srv-sueldos." con Deshacer (Figma 468-1320), o "... Se envía cuando haya
 * conexión." sin ella. Nombra la alerta porque ya no se está en su detalle. Queda a la vista mientras
 * haya una [revision]: cuando la ventana se cierra, el efecto se cancela y el aviso se va.
 */
@Composable
fun AvisoRevisaste(revision: RevisionPorDeshacer?, alDeshacer: () -> Unit, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val recursos = LocalResources.current
    LaunchedEffect(revision) {
        if (revision == null) return@LaunchedEffect
        val texto = recursos.getString(
            if (revision.conConexion) R.string.aviso_revisaste else R.string.aviso_revisaste_sin_conexion,
            revision.alerta,
        )
        val resultado = snackbar.showSnackbar(
            message = texto,
            actionLabel = recursos.getString(R.string.aviso_deshacer),
            duration = SnackbarDuration.Indefinite,
        )
        if (resultado == SnackbarResult.ActionPerformed) alDeshacer()
    }
    SnackbarHost(snackbar, modifier = modifier) { AvisoInferior(it) }
}
