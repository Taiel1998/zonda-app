package ar.edu.uade.zonda.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.descripcion
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.nombreCorto
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloBloque
import ar.edu.uade.zonda.ui.theme.tituloHoja
import java.util.Locale

// Los dos bloques de tipos de ataque (cambios 8 y 14): lo que detectó la IA y lo que no. Van con los
// mismos nombres y en el mismo orden en la pantalla de severidades, en "Por tipo de ataque" del
// Resumen y en la hoja "Qué es cada tipo".

private val LOCALE: Locale = Locale.forLanguageTag("es-AR")

/** "LO QUE DETECTÓ LA IA" (Figma 443-2683): SemiBold 11 gris, en mayúsculas y con 0,5 entre letras. */
@Composable
fun TituloDeBloque(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto.uppercase(LOCALE),
        style = MaterialTheme.typography.tituloBloque,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/** El punto de 8 con el color de la severidad que eligió la red para un tipo; TalkBack lee "Severidad alta". */
@Composable
fun PuntoDeSeveridad(severidad: Severidad, modifier: Modifier = Modifier) {
    val descripcion = stringResource(R.string.tipos_punto, stringResource(severidad.etiqueta()).lowercase(LOCALE))
    Box(
        modifier = modifier
            .size(8.dp)
            .background(severidad.tono().punto, CircleShape)
            .semantics { contentDescription = descripcion },
    )
}

/**
 * "Qué es cada tipo" (cambios 8 y 14; Figma 443-2683 y 443-3488): los dos bloques con su frase, una
 * oración por tipo con el punto de la severidad elegida y de dónde se cambia. Se abre desde el ⓘ de
 * "Por tipo de ataque" del Resumen y desde el de la pantalla de severidades.
 */
@Composable
fun HojaTipos(severidades: SeveridadPorTipo, alCerrar: () -> Unit) {
    HojaInferior(alCerrar = alCerrar) {
        ContenidoHojaTipos(severidades, alCerrar)
    }
}

/** El contenido de [HojaTipos], aparte para dibujarlo en las pruebas sin la ventana de la hoja. Va con 10 entre piezas, como el Figma. */
@Composable
fun ContenidoHojaTipos(severidades: SeveridadPorTipo, alCerrar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(R.string.tipos_titulo), style = MaterialTheme.typography.tituloHoja, modifier = Modifier.semantics { heading() })
        BloqueDeLaHoja(R.string.tipos_bloque_ia, R.string.tipos_bloque_ia_frase, TipoAtaque.DETECTADOS_POR_LA_IA, severidades)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BloqueDeLaHoja(R.string.tipos_bloque_sin_ia, R.string.tipos_bloque_sin_ia_frase, TipoAtaque.NO_DETECTADOS_POR_LA_IA, severidades)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(text = stringResource(R.string.tipos_pie), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EntendidoDeHoja(alCerrar)
    }
}

/** El título del bloque, su frase Regular 13 gris y un renglón por tipo. */
@Composable
private fun BloqueDeLaHoja(@StringRes titulo: Int, @StringRes frase: Int, tipos: List<TipoAtaque>, severidades: SeveridadPorTipo) {
    TituloDeBloque(stringResource(titulo))
    Text(text = stringResource(frase), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    tipos.forEach { tipo -> TipoDeLaHoja(tipo, severidades.severidadDe(tipo)) }
}

/** "● Fuerza bruta. Muchos intentos de contraseña…": el nombre SemiBold en el color del texto y el punto centrado en el primer renglón. */
@Composable
private fun TipoDeLaHoja(tipo: TipoAtaque, severidad: Severidad) {
    val estilo = MaterialTheme.typography.bodySmall
    val nombre = stringResource(tipo.nombreCorto())
    val descripcion = stringResource(tipo.descripcion())
    val colorNombre = MaterialTheme.colorScheme.onSurface
    // El "Auto" del Figma: un renglón mide 1,21 veces la letra (16 con la de 13), y crece con la del sistema.
    val renglon = with(LocalDensity.current) { (estilo.fontSize * 1.21f).toDp() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(8.dp).height(renglon)) {
            PuntoDeSeveridad(severidad)
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colorNombre)) { append("$nombre.") }
                append(" ")
                append(descripcion)
            },
            style = estilo,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
