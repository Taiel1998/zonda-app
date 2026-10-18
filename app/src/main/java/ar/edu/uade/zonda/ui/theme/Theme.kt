package ar.edu.uade.zonda.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
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
    tertiary = TonosClaros.info.texto,
    onTertiary = Blanco,
    tertiaryContainer = TonosClaros.info.fondo,
    onTertiaryContainer = TonosClaros.info.texto,
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
 * El modo oscuro (corrección 8, decisión 43), con los mismos roles: fondo y tarjetas #1B1B19, barra
 * inferior y recuadros #262522, bordes #3D3C37 y texto #E8E6DF. El aviso inferior y el globito se
 * invierten: fondo claro, letra oscura y la acción en verde.
 */
private val EsquemaZondaOscuro = darkColorScheme(
    primary = VerdeOscuro,
    onPrimary = SobreVerdeOscuro,
    primaryContainer = VerdeElegidoOscuro,
    onPrimaryContainer = SobreVerdeElegidoOscuro,
    inversePrimary = VerdeZonda,
    secondary = VerdeOscuro,
    onSecondary = SobreVerdeOscuro,
    secondaryContainer = VerdeElegidoOscuro,
    onSecondaryContainer = SobreVerdeElegidoOscuro,
    tertiary = TonosOscuros.info.texto,
    onTertiary = TonosOscuros.info.fondo,
    tertiaryContainer = TonosOscuros.info.fondo,
    onTertiaryContainer = TonosOscuros.info.texto,
    background = FondoOscuro,
    onBackground = TextoOscuro,
    surface = FondoOscuro,
    onSurface = TextoOscuro,
    surfaceVariant = NeutroOscuro,
    onSurfaceVariant = TextoSecundarioOscuro,
    surfaceTint = FondoOscuro,
    inverseSurface = TextoOscuro,
    inverseOnSurface = FondoOscuro,
    error = TonosOscuros.alta.texto,
    onError = TonosOscuros.alta.fondo,
    errorContainer = TonosOscuros.alta.fondo,
    onErrorContainer = TonosOscuros.alta.texto,
    outline = BordeOscuro,
    outlineVariant = BordeOscuro,
    scrim = Color.Black,
    surfaceBright = RecuadroOscuro,
    surfaceDim = FondoOscuro,
    surfaceContainerLowest = FondoOscuro,
    surfaceContainerLow = FondoOscuro,
    surfaceContainer = RecuadroOscuro,
    surfaceContainerHigh = RecuadroOscuro,
    surfaceContainerHighest = NeutroOscuro,
)

/**
 * Tema de la app, claro u [oscuro] (decisión 43). Sin colores dinámicos: con ellos, en Android 12 o más
 * nuevo la app tomaría los colores del fondo de pantalla y dejaría de parecerse al Figma. Cuál va lo
 * decide la preferencia "Tema" de Ajustes (MainActivity); la imagen y el PDF del Resumen y el QR van
 * siempre en claro.
 */
@Composable
fun ZondaTheme(oscuro: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTonos provides if (oscuro) TonosOscuros else TonosClaros,
        LocalColoresZonda provides if (oscuro) ColoresOscuros else ColoresClaros,
    ) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaZondaOscuro else EsquemaZonda,
            typography = TipografiaZonda,
            content = content,
        )
    }
}

/** Los colores propios de Zonda del tema actual ([ColoresZonda]). */
val MaterialTheme.zonda: ColoresZonda
    @Composable @ReadOnlyComposable get() = LocalColoresZonda.current
