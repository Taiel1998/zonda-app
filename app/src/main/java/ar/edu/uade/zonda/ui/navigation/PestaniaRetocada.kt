package ar.edu.uade.zonda.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter

/**
 * Las pestañas de la barra inferior que se tocaron estando ya en ellas. Como en las apps grandes, tocar
 * la pestaña en la que estás vuelve su lista arriba (plan de la 1.0.5, corrección 9; no cambia el Figma).
 */
val LocalPestaniaRetocada = staticCompositionLocalOf<SharedFlow<String>> { MutableSharedFlow() }

/** Corre [accion] cada vez que se vuelve a tocar la pestaña de [ruta] (por ejemplo, subir la lista). */
@Composable
fun AlRetocarPestania(ruta: String, accion: suspend () -> Unit) {
    val retoques = LocalPestaniaRetocada.current
    val accionActual by rememberUpdatedState(accion)
    LaunchedEffect(retoques, ruta) {
        retoques.filter { it == ruta }.collect { accionActual() }
    }
}
