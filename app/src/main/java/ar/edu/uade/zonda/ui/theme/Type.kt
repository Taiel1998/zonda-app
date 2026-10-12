package ar.edu.uade.zonda.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ar.edu.uade.zonda.R

/** Inter, la letra del Figma, en los tres pesos que usa: normal, mediano y seminegrita (licencia OFL en docs/licencias). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val base = Typography()

private fun TextStyle.conInter() = copy(fontFamily = Inter)

/**
 * Los tamaños del informe (títulos de 22, texto de 14 a 16 y etiquetas de 11 a 13) ya son los de
 * Material 3 por defecto, así que solo cambia la familia. Todo en sp: respeta el tamaño de letra
 * del sistema (RNF09).
 */
val TipografiaZonda = Typography(
    displayLarge = base.displayLarge.conInter(),
    displayMedium = base.displayMedium.conInter(),
    displaySmall = base.displaySmall.conInter(),
    headlineLarge = base.headlineLarge.conInter(),
    headlineMedium = base.headlineMedium.conInter(),
    headlineSmall = base.headlineSmall.conInter(),
    titleLarge = base.titleLarge.conInter(),
    titleMedium = base.titleMedium.conInter(),
    titleSmall = base.titleSmall.conInter(),
    bodyLarge = base.bodyLarge.conInter(),
    bodyMedium = base.bodyMedium.conInter(),
    bodySmall = base.bodySmall.conInter(),
    labelLarge = base.labelLarge.conInter(),
    labelMedium = base.labelMedium.conInter(),
    labelSmall = base.labelSmall.conInter(),
)
