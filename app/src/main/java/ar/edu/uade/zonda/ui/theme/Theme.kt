package ar.edu.uade.zonda.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Los colores del Figma puestos en los roles de Material 3. Con el verde claro en
 * `secondaryContainer`, el chip elegido, el botón tonal y la píldora de la barra inferior salen
 * verdes sin tocar cada componente.
 */
private val EsquemaZonda = lightColorScheme(
    primary = VerdeZonda,
    onPrimary = Blanco,
    primaryContainer = VerdeZondaClaro,
    onPrimaryContainer = VerdeZonda,
    inversePrimary = VerdeAccionAviso,
    secondary = VerdeZonda,
    onSecondary = Blanco,
    secondaryContainer = VerdeZondaClaro,
    onSecondaryContainer = VerdeZonda,
    tertiary = Tonos.Info.texto,
    onTertiary = Blanco,
    tertiaryContainer = Tonos.Info.fondo,
    onTertiaryContainer = Tonos.Info.texto,
    background = Blanco,
    onBackground = Texto,
    surface = Blanco,
    onSurface = Texto,
    surfaceVariant = GrisCalido,
    onSurfaceVariant = TextoSecundario,
    surfaceTint = Blanco,
    inverseSurface = GrisAviso,
    inverseOnSurface = Blanco,
    error = RojoPeligro,
    onError = Blanco,
    errorContainer = RojoPeligroFondo,
    onErrorContainer = RojoPeligro,
    outline = Borde,
    outlineVariant = Borde,
    scrim = Color.Black,
    surfaceBright = Blanco,
    surfaceDim = GrisCalido,
    surfaceContainerLowest = Blanco,
    surfaceContainerLow = Blanco,
    surfaceContainer = CremaBarra,
    surfaceContainerHigh = Blanco,
    surfaceContainerHighest = GrisCalido,
)

/**
 * Tema de la app. Solo claro y sin colores dinámicos: con colores dinámicos, en Android 12 o más
 * nuevo la app tomaría los colores del fondo de pantalla y dejaría de parecerse al Figma, que
 * tampoco tiene tema oscuro (va a las limitaciones del README).
 */
@Composable
fun ZondaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaZonda,
        typography = TipografiaZonda,
        content = content,
    )
}
