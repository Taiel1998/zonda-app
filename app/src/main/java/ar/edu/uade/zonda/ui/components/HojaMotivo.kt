package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.theme.tituloHoja

/**
 * "¿Por qué la cambiás?" (corrección 3, decisión 40; Figma 468-3463): cada cambio con sus etiquetas, el
 * motivo obligatorio con su ejemplo y la línea que dice dónde queda, y "Cancelar" o "Guardar cambios".
 * Sin motivo no guarda ("Escribí por qué la cambiás."). La usan las severidades y la importancia de un
 * equipo. Cerrarla deslizando es Cancelar.
 */
@Composable
fun HojaMotivo(
    motivo: String,
    alCambiarMotivo: (String) -> Unit,
    conError: Boolean,
    alCancelar: () -> Unit,
    alGuardar: () -> Unit,
    cambios: @Composable ColumnScope.() -> Unit,
) {
    HojaInferior(alCerrar = alCancelar) {
        ContenidoHojaMotivo(motivo, alCambiarMotivo, conError, alCancelar, alGuardar, cambios)
    }
}

/** El contenido de [HojaMotivo], aparte para dibujarlo en las pruebas sin la ventana de la hoja. */
@Composable
fun ColumnScope.ContenidoHojaMotivo(
    motivo: String,
    alCambiarMotivo: (String) -> Unit,
    conError: Boolean,
    alCancelar: () -> Unit,
    alGuardar: () -> Unit,
    cambios: @Composable ColumnScope.() -> Unit,
) {
    Text(text = stringResource(R.string.motivo_titulo), style = MaterialTheme.typography.tituloHoja)
    cambios()
    CampoTexto(
        valor = motivo,
        alCambiar = alCambiarMotivo,
        etiqueta = stringResource(R.string.motivo_campo),
        ejemplo = stringResource(R.string.motivo_ejemplo),
        ayuda = stringResource(R.string.motivo_ayuda),
        error = if (conError) stringResource(R.string.motivo_falta) else null,
        lineasMinimas = 2,
        ayudaSiempre = true,
    )
    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonContorno(texto = stringResource(R.string.revisar_cancelar), alTocar = alCancelar, modifier = Modifier.weight(1f), relleno = RellenoBotonAngosto)
        BotonPrincipal(texto = stringResource(R.string.motivo_guardar), alTocar = alGuardar, modifier = Modifier.weight(1f))
    }
}

/** Un cambio de la hoja: "Fuerza bruta (SSH, FTP)" con la etiqueta de antes, la flecha y la de después (Figma 468-3574). */
@Composable
fun FilaDeCambio(nombre: String, antes: @Composable () -> Unit, despues: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = nombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        antes()
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.motivo_pasa_a),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        despues()
    }
}

/** El cambio de severidad de un tipo: "Fuerza bruta (SSH, FTP)  ALTA → MEDIA". */
@Composable
fun FilaDeCambioDeSeveridad(nombre: String, antes: Severidad, despues: Severidad) {
    FilaDeCambio(nombre = nombre, antes = { IndicadorSeveridad(antes) }, despues = { IndicadorSeveridad(despues) })
}
