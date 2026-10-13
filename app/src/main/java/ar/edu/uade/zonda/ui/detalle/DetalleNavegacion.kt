package ar.edu.uade.zonda.ui.detalle

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ar.edu.uade.zonda.ui.navigation.EnlacesInternos

/**
 * Ruta del detalle de una alerta. Se abre desde Estado o Alertas (con [crear]) y desde el enlace
 * del aviso de Telegram (App Link con el dominio público del equipo). El enlace solo lleva el id:
 * los datos de la alerta se piden a la API o se leen de la base local (informe, sección 13.4).
 */
object RutaDetalle {
    const val ARG_ID = "alertaId"
    const val PATRON = "detalle/{$ARG_ID}"
    const val ENLACE_AVISO = "https://taiel1998.github.io/a/{$ARG_ID}"

    fun crear(alertaId: String): String = "detalle/$alertaId"
}

fun NavGraphBuilder.detalleAlerta(alVolver: () -> Unit, alPedirRed: (redId: String) -> Unit = {}) {
    composable(
        route = RutaDetalle.PATRON,
        arguments = listOf(
            navArgument(RutaDetalle.ARG_ID) { type = NavType.StringType },
            navArgument(EnlacesInternos.ARG_RED) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = RutaDetalle.ENLACE_AVISO },
            navDeepLink { uriPattern = EnlacesInternos.PATRON_DETALLE },
        ),
    ) { entrada ->
        val alertaId = entrada.arguments?.getString(RutaDetalle.ARG_ID).orEmpty()
        // La notificación de otra red trae su id: esa red pasa a ser la activa (una sola vez por entrada).
        LaunchedEffect(entrada) {
            val red = entrada.arguments?.getString(EnlacesInternos.ARG_RED)
            if (red != null && entrada.savedStateHandle.get<Boolean>(RED_PEDIDA) != true) {
                entrada.savedStateHandle[RED_PEDIDA] = true
                alPedirRed(red)
            }
        }
        DetalleScreen(alertaId = alertaId, alVolver = alVolver)
    }
}

private const val RED_PEDIDA = "red_pedida"
