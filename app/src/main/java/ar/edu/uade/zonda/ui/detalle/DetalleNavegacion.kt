package ar.edu.uade.zonda.ui.detalle

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink

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

fun NavGraphBuilder.detalleAlerta(alVolver: () -> Unit) {
    composable(
        route = RutaDetalle.PATRON,
        arguments = listOf(navArgument(RutaDetalle.ARG_ID) { type = NavType.StringType }),
        deepLinks = listOf(navDeepLink { uriPattern = RutaDetalle.ENLACE_AVISO }),
    ) { entrada ->
        val alertaId = entrada.arguments?.getString(RutaDetalle.ARG_ID).orEmpty()
        DetalleScreen(alertaId = alertaId, alVolver = alVolver)
    }
}
