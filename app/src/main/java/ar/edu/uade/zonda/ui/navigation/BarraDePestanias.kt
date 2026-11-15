package ar.edu.uade.zonda.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.objetivoDelRecorrido
import ar.edu.uade.zonda.ui.theme.etiquetaBarra

/**
 * La barra inferior con [pestanias]: Estado, Alertas y Ajustes, y Administración para el administrador.
 * Tocar la pestaña elegida avisa [alRetocar] (su lista vuelve arriba, como en las apps grandes); tocar
 * otra, [alElegir]. Una pestaña con el título abreviado («Admin.») le dice a TalkBack el nombre entero.
 */
@Composable
fun BarraDePestanias(
    pestanias: List<Destino>,
    rutaActual: String?,
    alElegir: (Destino) -> Unit,
    alRetocar: (Destino) -> Unit,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            pestanias.forEach { destino ->
                val elegida = rutaActual == destino.ruta
                NavigationBarItem(
                    selected = elegida,
                    onClick = { if (elegida) alRetocar(destino) else alElegir(destino) },
                    modifier = if (destino.ruta == Rutas.ALERTAS) Modifier.objetivoDelRecorrido(ObjetivoRecorrido.PESTANIA_ALERTAS) else Modifier,
                    icon = { Icon(destino.icono, contentDescription = null) },
                    // Figma: la pestaña elegida en SemiBold 12 y las otras en Medium 12.
                    label = {
                        val descripcion = destino.descripcion?.let { stringResource(it) }
                        Text(
                            text = stringResource(destino.titulo),
                            style = MaterialTheme.typography.etiquetaBarra,
                            fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = if (descripcion != null) Modifier.clearAndSetSemantics { contentDescription = descripcion } else Modifier,
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
