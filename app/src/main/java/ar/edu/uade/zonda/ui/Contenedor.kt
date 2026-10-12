package ar.edu.uade.zonda.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.edu.uade.zonda.ZondaApp
import ar.edu.uade.zonda.di.AppContainer

/**
 * Crea (o recupera) el ViewModel de una pantalla con las dependencias del [AppContainer].
 *
 * Sin Hilt (docs/decisiones.md, decisión 2), cada pantalla dice cómo se arma su ViewModel:
 * `viewModelDeZonda { contenedor -> AlertasViewModel(contenedor.observarAlertas, ...) }`.
 * Navigation Compose guarda el ViewModel por entrada de la pila, así que cada pantalla abierta
 * (cada detalle, por ejemplo) tiene el suyo y sobrevive a la rotación.
 */
@Composable
inline fun <reified VM : ViewModel> viewModelDeZonda(crossinline crear: (AppContainer) -> VM): VM {
    val contenedor = (LocalContext.current.applicationContext as ZondaApp).container
    return viewModel(factory = viewModelFactory { initializer { crear(contenedor) } })
}
