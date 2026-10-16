package ar.edu.uade.zonda.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ar.edu.uade.zonda.R

/** Inter, la letra del Figma, en los tres pesos que usa: normal, mediano y seminegrita (licencia OFL en docs/licencias). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val base = Typography()

/**
 * Un estilo del Figma: Inter, sin espaciado entre letras y con el interlineado propio de la letra,
 * que es el "Auto" del Figma (1,21 veces el tamaño). Todo en sp: respeta el tamaño de letra del
 * sistema (RNF09).
 */
private fun estilo(desde: TextStyle, tamanio: Int, peso: FontWeight) = desde.copy(
    fontFamily = Inter,
    fontSize = tamanio.sp,
    fontWeight = peso,
    letterSpacing = 0.sp,
    lineHeight = TextUnit.Unspecified,
)

/**
 * Los estilos de texto del Figma, cada uno en el rol de Material 3 que lo usa:
 *
 * | Rol            | Figma               | Dónde                                              |
 * |----------------|---------------------|----------------------------------------------------|
 * | titleLarge     | Medium 22           | título de cada pantalla (barra superior)           |
 * | titleSmall     | Medium 15           | título de una fila o de una tarjeta                |
 * | bodyMedium     | Regular 14          | texto de las hojas inferiores                      |
 * | bodySmall      | Regular 13          | detalle de una fila, introducciones, descripciones |
 * | labelLarge     | SemiBold 14         | botones, enlaces y títulos de avisos               |
 * | labelMedium    | SemiBold 13         | título chico de una sección ("Red", "Avisos")      |
 * | labelSmall     | SemiBold 11         | etiquetas de severidad                             |
 *
 * Los que no tienen rol propio están abajo como extensiones ([nota], [tituloHoja], [opcion],
 * [valor] y [etiquetaBarra]). Los tamaños grandes quedan los de Material 3.
 */
val TipografiaZonda = Typography(
    displayLarge = estilo(base.displayLarge, 57, FontWeight.Normal),
    displayMedium = estilo(base.displayMedium, 45, FontWeight.Normal),
    displaySmall = estilo(base.displaySmall, 36, FontWeight.Normal),
    headlineLarge = estilo(base.headlineLarge, 32, FontWeight.Normal),
    headlineMedium = estilo(base.headlineMedium, 28, FontWeight.Normal),
    headlineSmall = estilo(base.headlineSmall, 24, FontWeight.Normal),
    titleLarge = estilo(base.titleLarge, 22, FontWeight.Medium),
    titleMedium = estilo(base.titleMedium, 16, FontWeight.Medium),
    titleSmall = estilo(base.titleSmall, 15, FontWeight.Medium),
    bodyLarge = estilo(base.bodyLarge, 16, FontWeight.Normal),
    bodyMedium = estilo(base.bodyMedium, 14, FontWeight.Normal),
    bodySmall = estilo(base.bodySmall, 13, FontWeight.Normal),
    labelLarge = estilo(base.labelLarge, 14, FontWeight.SemiBold),
    labelMedium = estilo(base.labelMedium, 13, FontWeight.SemiBold),
    labelSmall = estilo(base.labelSmall, 11, FontWeight.SemiBold),
)

/** Regular 12: notas al pie y el estado de cada red. */
val Typography.nota: TextStyle
    get() = bodySmall.copy(fontSize = 12.sp)

/** SemiBold 20: título de una hoja inferior. */
val Typography.tituloHoja: TextStyle
    get() = titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

/** SemiBold 11 con 0,5 entre letras: el título de cada bloque de tipos de ataque ("LO QUE DETECTÓ LA IA"). */
val Typography.tituloBloque: TextStyle
    get() = labelSmall.copy(letterSpacing = 0.5.sp)

/** Medium 13: opciones para elegir (con sonido, alta, media...) y el resumen de una hoja. */
val Typography.opcion: TextStyle
    get() = bodySmall.copy(fontWeight = FontWeight.Medium)

/** Medium 14: el valor de una fila de datos ("HTTPS · TLS 1.3"). */
val Typography.valor: TextStyle
    get() = bodyMedium.copy(fontWeight = FontWeight.Medium)

/** SemiBold 12: el nombre de cada pestaña de la barra inferior, "Pegar" y la etiqueta del semáforo. */
val Typography.etiquetaBarra: TextStyle
    get() = labelMedium.copy(fontSize = 12.sp)

/** Regular 15: los pasos de las hojas de ayuda y los textos del primer inicio. */
val Typography.cuerpo: TextStyle
    get() = bodyMedium.copy(fontSize = 15.sp)

/** SemiBold 18: "Necesitás el código de pareo". */
val Typography.encabezado: TextStyle
    get() = titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

/** SemiBold 22: el título de cada paso del tutorial. */
val Typography.tituloPaso: TextStyle
    get() = titleLarge.copy(fontWeight = FontWeight.SemiBold)

/** SemiBold 26: "Bienvenido a Zonda". */
val Typography.tituloBienvenida: TextStyle
    get() = titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

/** Medium 12: "o ingresalo a mano", el encabezado de un aviso y el nombre de cada contador. */
val Typography.rotulo: TextStyle
    get() = bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)

/** Regular 17: el ejemplo de un campo vacío ("zonda-empresa.duckdns.org"). */
val Typography.ejemploCampo: TextStyle
    get() = bodyLarge.copy(fontSize = 17.sp)

/** SemiBold 24: el número de cada contador del semáforo. */
val Typography.numeroContador: TextStyle
    get() = headlineSmall.copy(fontWeight = FontWeight.SemiBold)
