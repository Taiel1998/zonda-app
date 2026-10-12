package ar.edu.uade.zonda.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import ar.edu.uade.zonda.R

/**
 * Destinos de la barra inferior (informe, sección 11.11): Estado, Alertas y Ajustes.
 * Cada uno tiene su ruta de navegación, el título que se muestra y el ícono.
 */
sealed class Destino(
    val ruta: String,
    @param:StringRes val titulo: Int,
    val icono: ImageVector,
) {
    data object Estado : Destino("estado", R.string.destino_estado, Icons.Filled.Home)
    data object Alertas : Destino("alertas", R.string.destino_alertas, Icons.Filled.Notifications)
    data object Ajustes : Destino("ajustes", R.string.destino_ajustes, Icons.Filled.Settings)

    companion object {
        val barraInferior: List<Destino> = listOf(Estado, Alertas, Ajustes)
    }
}
