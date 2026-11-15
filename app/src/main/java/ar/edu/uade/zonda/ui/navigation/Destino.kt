package ar.edu.uade.zonda.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import ar.edu.uade.zonda.R

/**
 * Destinos de la barra inferior (informe, sección 11.11): Estado, Alertas y Ajustes, y Administración
 * solo para el administrador de la red activa (como el tablero, 15/11). Cada uno tiene su ruta de
 * navegación, el título que se muestra, el ícono y, si el título va abreviado, lo que lee TalkBack.
 */
sealed class Destino(
    val ruta: String,
    @param:StringRes val titulo: Int,
    val icono: ImageVector,
    @param:StringRes val descripcion: Int? = null,
) {
    data object Estado : Destino(Rutas.ESTADO, R.string.destino_estado, Icons.Filled.VerifiedUser)
    data object Alertas : Destino(Rutas.ALERTAS, R.string.destino_alertas, Icons.Filled.Notifications)
    data object Ajustes : Destino(Rutas.AJUSTES, R.string.destino_ajustes, Icons.Filled.Settings)

    /**
     * El escudo con una persona, el ícono de administración de Android: el escudo con tilde ya es el de
     * Estado. «Administración» entero no entra en la barra: dice «Admin.» y TalkBack lee el nombre entero.
     */
    data object Administracion : Destino(
        Rutas.ADMINISTRACION,
        R.string.destino_administracion,
        Icons.Filled.AdminPanelSettings,
        R.string.destino_administracion_descripcion,
    )

    companion object {
        /** Las pestañas de la barra: Administración, al final, solo si este teléfono es administrador en la red activa. */
        fun barraInferior(conAdministracion: Boolean): List<Destino> =
            if (conAdministracion) listOf(Estado, Alertas, Ajustes, Administracion) else listOf(Estado, Alertas, Ajustes)
    }
}
