package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.ui.theme.Tonos

// Los botones del Figma: 41 dp de alto (12 arriba y abajo, 24 a los costados), esquinas de 20 y
// texto SemiBold 14. Cambia solo el color: verde lleno, verde tonal, rojo tonal, rojo lleno y con
// borde.
//
// Material reserva 48 dp de alto en el diseño para el área táctil y le suma 7 al botón de 41: los
// espacios entre piezas quedaban más grandes que en el Figma. Por eso cada botón ocupa su alto visible
// ([SinReservaTactil]) y el área táctil de 48 dp la da la búsqueda de toque cercano de Compose, que
// acepta un toque hasta 48 dp alrededor de lo que se puede tocar (decisión 19).

private val FormaBoton = RoundedCornerShape(20.dp)

/** El botón ocupa en el diseño lo que se ve; el toque cercano completa los 48 dp. */
@Composable
fun SinReservaTactil(contenido: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = contenido)
}
private val RellenoBoton = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/**
 * Para dos botones que comparten una fila: el texto va centrado igual que en el Figma, pero con menos
 * margen a los costados entra en una línea ("Borrar datos locales").
 */
val RellenoBotonAngosto = PaddingValues(horizontal = 8.dp, vertical = 12.dp)

@Composable
private fun ContenidoBoton(texto: String, icono: ImageVector?, cargando: Boolean = false) {
    if (cargando) {
        CircularProgressIndicator(strokeWidth = 2.dp, color = LocalContentColor.current, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
    } else if (icono != null) {
        Icon(imageVector = icono, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = texto, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun BotonLleno(
    texto: String,
    alTocar: () -> Unit,
    colores: ButtonColors,
    modifier: Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
    relleno: PaddingValues = RellenoBoton,
    cargando: Boolean = false,
) {
    SinReservaTactil {
        Button(
            onClick = alTocar,
            enabled = habilitado,
            shape = FormaBoton,
            colors = colores,
            contentPadding = relleno,
            modifier = modifier,
        ) {
            ContenidoBoton(texto, icono, cargando)
        }
    }
}

/** La acción principal de la pantalla: verde, ancha y abajo, en la zona del pulgar (informe, figura 8). */
@Composable
fun BotonPrincipal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
    /** Sin el ancho completo, el botón mide lo que su texto ("Siguiente" del recorrido, Figma 107-4860). */
    anchoCompleto: Boolean = true,
) {
    // Deshabilitado como el Figma ("Conectar" sin datos): gris cálido con el texto gris.
    val colores = ButtonDefaults.buttonColors(
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = Tonos.Neutro.punto,
    )
    BotonLleno(texto, alTocar, colores, if (anchoCompleto) modifier.fillMaxWidth() else modifier, habilitado, icono)
}

/** Verde claro con texto verde: acciones secundarias ("Sincronizar ahora", "Conectar Telegram"). */
@Composable
fun BotonTonal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    relleno: PaddingValues = RellenoBoton,
) {
    BotonLleno(
        texto = texto,
        alTocar = alTocar,
        colores = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
        habilitado = habilitado,
        icono = icono,
        relleno = relleno,
        cargando = cargando,
    )
}

/** Rosa con texto rojo para lo que borra o revoca ("Borrar datos locales", "Desvincular Telegram"). */
@Composable
fun BotonTonalPeligro(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    relleno: PaddingValues = RellenoBoton,
    habilitado: Boolean = true,
) {
    BotonLleno(
        texto = texto,
        alTocar = alTocar,
        colores = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
        ),
        modifier = modifier,
        habilitado = habilitado,
        relleno = relleno,
    )
}

/** Rojo lleno: la confirmación de algo que no se puede deshacer ("Borrar datos", "Quitar dispositivo"). */
@Composable
fun BotonPeligro(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    relleno: PaddingValues = RellenoBoton,
) {
    BotonLleno(
        texto = texto,
        alTocar = alTocar,
        colores = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier,
        relleno = relleno,
    )
}

/**
 * Con borde gris y texto verde: la salida de una hoja ("Cancelar", "Entendido") o la segunda acción de
 * una pantalla ("Ya conozco Zonda"). En el escáner, oscuro, cambian [colorTexto] y [colorBorde].
 */
@Composable
fun BotonContorno(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    relleno: PaddingValues = RellenoBoton,
    habilitado: Boolean = true,
    colorTexto: Color = MaterialTheme.colorScheme.primary,
    colorBorde: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    SinReservaTactil {
        OutlinedButton(
            onClick = alTocar,
            enabled = habilitado,
            shape = FormaBoton,
            border = BorderStroke(1.dp, colorBorde),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorTexto, disabledContentColor = Tonos.Neutro.punto),
            contentPadding = relleno,
            modifier = modifier,
        ) {
            ContenidoBoton(texto, icono = null)
        }
    }
}

/** Un enlace: texto verde SemiBold sin fondo ("Cambiar la severidad de cada tipo de ataque", "Corregir la dirección"). */
@Composable
fun BotonEnlace(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    relleno: PaddingValues = RellenoBoton,
    colorTexto: Color = MaterialTheme.colorScheme.primary,
) {
    SinReservaTactil {
        TextButton(onClick = alTocar, shape = FormaBoton, contentPadding = relleno, modifier = modifier) {
            Text(text = texto, style = MaterialTheme.typography.labelLarge, color = colorTexto)
        }
    }
}
