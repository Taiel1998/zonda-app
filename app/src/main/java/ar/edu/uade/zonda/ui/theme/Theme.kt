package ar.edu.uade.zonda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsquemaClaro = lightColorScheme(
    primary = VerdeZonda,
    onPrimary = Color.White,
    primaryContainer = VerdeZondaClaro,
    onPrimaryContainer = VerdeZondaOscuro,
    secondary = SeveridadBaja,
    secondaryContainer = SeveridadBajaFondo,
    error = SeveridadAlta,
    errorContainer = SeveridadAltaFondo,
    background = Color(0xFFFCFDFC),
    surface = Color(0xFFFCFDFC),
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF7FD3BD),
    onPrimary = VerdeZondaOscuro,
    primaryContainer = VerdeZondaOscuro,
    onPrimaryContainer = VerdeZondaClaro,
    error = Color(0xFFF2B8B5),
)

/**
 * Tema de la app: Material 3 con los colores de Zonda. La tipografía es la de Material 3 por defecto,
 * que alcanza para esta app (indicación de la cátedra) y respeta el tamaño de letra del sistema.
 */
@Composable
fun ZondaTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        content = content,
    )
}
