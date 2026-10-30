package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.ui.theme.ejemploCampo
import ar.edu.uade.zonda.ui.theme.encabezado
import ar.edu.uade.zonda.ui.theme.etiquetaBarra
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloHoja

// Los campos de la pantalla Conexión (Figma 35-432, 49-10 y 49-131). La etiqueta va adentro del
// recuadro: vacío y sin foco, la etiqueta es Regular 14 y debajo va el ejemplo en Regular 17; con
// foco o con texto, la etiqueta baja a Regular 12 y el valor es Regular 16. El borde es gris de 1 y,
// con foco, verde de 2. Adentro hay 16 a los costados y 10 arriba y abajo, más el borde.

private val FormaCampo = RoundedCornerShape(8.dp)

/** Cuántos caracteres tiene el código de pareo, sin el guion. */
const val LARGO_CODIGO = 8

/**
 * La etiqueta de un campo obligatorio con el asterisco rojo al final ("Comentario *", decisión 48); sin
 * [obligatorio], el texto tal cual.
 */
@Composable
fun etiquetaConAsterisco(texto: String, obligatorio: Boolean = true): AnnotatedString {
    val rojo = MaterialTheme.colorScheme.error
    return buildAnnotatedString {
        append(texto)
        if (obligatorio) withStyle(SpanStyle(color = rojo)) { append(" *") }
    }
}

/** "* Obligatorio", debajo de los campos de un formulario que tiene campos obligatorios (decisión 48). */
@Composable
fun LeyendaObligatorio(modifier: Modifier = Modifier) {
    val rojo = MaterialTheme.colorScheme.error
    val obligatorio = stringResource(R.string.campo_obligatorio)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = rojo)) { append("* ") }
            append(obligatorio)
        },
        style = MaterialTheme.typography.nota,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Lo que lee TalkBack del campo: la etiqueta y, si es obligatorio, "obligatorio" (el asterisco no se lee). */
@Composable
private fun descripcionDeCampo(etiqueta: String, obligatorio: Boolean): String =
    if (obligatorio) stringResource(R.string.campo_obligatorio_descripcion, etiqueta) else etiqueta

/** El recuadro de un campo con la etiqueta adentro. [campo] es el texto que se escribe. */
@Composable
private fun RecuadroDeCampo(
    etiqueta: String,
    ejemplo: String,
    vacio: Boolean,
    enfocado: Boolean,
    conError: Boolean,
    obligatorio: Boolean = false,
    campo: @Composable () -> Unit,
) {
    val textoEtiqueta = etiquetaConAsterisco(etiqueta, obligatorio)
    val grosor: Dp = if (enfocado || conError) 2.dp else 1.dp
    val colorBorde = when {
        conError -> MaterialTheme.colorScheme.error
        enfocado -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val mostrarEjemplo = vacio && !enfocado
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(grosor, colorBorde, FormaCampo)
            // El borde va por dentro, como en el Figma: el margen se cuenta desde el borde.
            .padding(horizontal = 16.dp + grosor, vertical = 10.dp + grosor),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (mostrarEjemplo) {
            Text(text = textoEtiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                text = textoEtiqueta,
                style = MaterialTheme.typography.nota,
                color = when {
                    conError -> MaterialTheme.colorScheme.error
                    enfocado -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Box {
            if (mostrarEjemplo) {
                Text(text = ejemplo, style = MaterialTheme.typography.ejemploCampo, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            campo()
        }
    }
}

/** Texto chico debajo de un campo: la ayuda en gris o el error en rojo. */
@Composable
private fun TextoBajoCampo(ayuda: String?, error: String?) {
    when {
        error != null -> Text(text = error, style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.error)
        ayuda != null -> Text(text = ayuda, style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Un campo de texto del Figma ("Dirección del servidor"). [ayuda] se ve mientras el campo tiene el
 * foco ("Es el nombre que figura en el código, sin https://"); [error], siempre que haya.
 */
@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    ejemplo: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    error: String? = null,
    opcionesTeclado: KeyboardOptions = KeyboardOptions.Default,
    accionesTeclado: KeyboardActions = KeyboardActions.Default,
    interaccion: MutableInteractionSource = remember { MutableInteractionSource() },
    /** Con más de una línea, el campo crece con el texto (el comentario de la revisión, Figma 66-2933). */
    lineasMinimas: Int = 1,
    /** La ayuda se ve siempre, no solo con el foco ("Queda en el historial de la alerta."). */
    ayudaSiempre: Boolean = false,
    /** La etiqueta lleva el asterisco rojo de los campos obligatorios (decisión 48). */
    obligatorio: Boolean = false,
) {
    val enfocado by interaccion.collectIsFocusedAsState()
    val descripcion = descripcionDeCampo(etiqueta, obligatorio)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = valor,
            onValueChange = alCambiar,
            singleLine = lineasMinimas == 1,
            minLines = lineasMinimas,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = opcionesTeclado,
            keyboardActions = accionesTeclado,
            interactionSource = interaccion,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = descripcion },
            decorationBox = { campo ->
                RecuadroDeCampo(etiqueta, ejemplo, vacio = valor.isEmpty(), enfocado = enfocado, conError = error != null, obligatorio = obligatorio, campo = campo)
            },
        )
        TextoBajoCampo(ayuda = if (enfocado || ayudaSiempre) ayuda else null, error = error)
    }
}

/**
 * El código de pareo (Figma 49-131): sin foco y vacío es un campo más ("8 caracteres, por ejemplo
 * K7M2-QX9P"); al tocarlo pasa a ocho casillas con el guion en el medio, "Pegar" arriba y la ayuda
 * abajo. [codigo] llega como se escribió; las casillas muestran sus letras y números en mayúsculas.
 */
@Composable
fun CampoCodigo(
    codigo: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    ejemplo: String,
    ayuda: String,
    textoPegar: String,
    alPegar: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    opcionesTeclado: KeyboardOptions = KeyboardOptions.Default,
    accionesTeclado: KeyboardActions = KeyboardActions.Default,
    interaccion: MutableInteractionSource = remember { MutableInteractionSource() },
    /** La etiqueta lleva el asterisco rojo de los campos obligatorios (decisión 48). */
    obligatorio: Boolean = false,
) {
    val enfocado by interaccion.collectIsFocusedAsState()
    val casillas = enfocado || codigo.isNotEmpty()
    val visibles = DatosPareo.caracteresDeCodigo(codigo)
    val descripcion = descripcionDeCampo(etiqueta, obligatorio)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (casillas) 8.dp else 4.dp)) {
        if (casillas) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = etiquetaConAsterisco(etiqueta, obligatorio),
                    style = MaterialTheme.typography.nota,
                    color = when {
                        error != null -> MaterialTheme.colorScheme.error
                        enfocado -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                ChipPegar(texto = textoPegar, alTocar = alPegar)
            }
        }
        BasicTextField(
            value = codigo,
            onValueChange = alCambiar,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(if (casillas) Color.Transparent else MaterialTheme.colorScheme.primary),
            keyboardOptions = opcionesTeclado,
            keyboardActions = accionesTeclado,
            interactionSource = interaccion,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = descripcion },
            decorationBox = { campo ->
                if (casillas) {
                    Box {
                        CasillasDeCodigo(codigo = visibles, activa = if (enfocado) minOf(visibles.length, LARGO_CODIGO - 1) else null, conError = error != null)
                        // El texto real queda debajo, invisible: las casillas lo muestran.
                        Box(modifier = Modifier.matchParentSize().alpha(0f)) { campo() }
                    }
                } else {
                    RecuadroDeCampo(etiqueta, ejemplo, vacio = true, enfocado = false, conError = error != null, obligatorio = obligatorio, campo = campo)
                }
            },
        )
        if (casillas) TextoBajoCampo(ayuda = ayuda, error = error) else TextoBajoCampo(ayuda = null, error = error)
    }
}

/** "Pegar": el chip verde claro arriba de las casillas. */
@Composable
private fun ChipPegar(texto: String, alTocar: () -> Unit) {
    SinReservaTactil {
        Surface(onClick = alTocar, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                text = texto,
                style = MaterialTheme.typography.etiquetaBarra,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Las ocho casillas de 38 por 46, con 6 entre ellas y el guion en el medio. En un teléfono angosto se
 * achican para entrar. [activa] es la casilla que sigue (borde verde de 2).
 */
@Composable
private fun CasillasDeCodigo(codigo: String, activa: Int?, conError: Boolean) {
    Row(
        modifier = Modifier
            .widthIn(max = 361.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (indice in 0 until LARGO_CODIGO) {
            if (indice == LARGO_CODIGO / 2) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.encabezado.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Casilla(caracter = codigo.getOrNull(indice), activa = indice == activa, conError = conError)
        }
    }
}

@Composable
private fun RowScope.Casilla(caracter: Char?, activa: Boolean, conError: Boolean) {
    val (grosor, color) = when {
        activa -> 2.dp to MaterialTheme.colorScheme.primary
        conError -> 2.dp to MaterialTheme.colorScheme.error
        else -> 1.dp to MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .background(MaterialTheme.colorScheme.surface, FormaCampo)
            .border(grosor, color, FormaCampo),
        contentAlignment = Alignment.Center,
    ) {
        if (caracter != null) {
            Text(text = caracter.toString(), style = MaterialTheme.typography.tituloHoja, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
