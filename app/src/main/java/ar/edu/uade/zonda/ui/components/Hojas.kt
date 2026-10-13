package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.tituloHoja

/** Un botón de una hoja: su texto y qué hace. */
data class AccionDeHoja(val texto: String, val alTocar: () -> Unit)

/**
 * La hoja inferior del Figma (51-1451 y 66-3906): blanca, esquinas de arriba de 20, el asa gris de
 * 32 por 4 y adentro 24 a los costados y abajo, con 12 entre piezas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaInferior(
    alCerrar: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AsaDeHoja() },
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = contenido,
        )
    }
}

/** El asa de la hoja: 16 desde el borde y 12 hasta el título. */
@Composable
private fun AsaDeHoja() {
    Box(
        modifier = Modifier
            .padding(top = 16.dp, bottom = 12.dp)
            .size(width = 32.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
    )
}

/** Título y texto de una hoja. */
@Composable
private fun EncabezadoDeHoja(titulo: String, texto: String) {
    Text(text = titulo, style = MaterialTheme.typography.tituloHoja)
    Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Hoja de confirmación (Figma: borrar datos locales y quitar este dispositivo). Las confirmaciones
 * quedan abajo, en la zona del pulgar (informe, figura 8): "Cancelar" con borde y la acción de
 * peligro en rojo. El [resumen] dice sobre qué se actúa ("Celular Martín · Oficina central · 213
 * alertas guardadas").
 */
@Composable
fun HojaConfirmacion(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit,
    resumen: String? = null,
) {
    HojaInferior(alCerrar = alCerrar) {
        EncabezadoDeHoja(titulo, texto)
        if (resumen != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = resumen, style = MaterialTheme.typography.opcion, modifier = Modifier.padding(12.dp))
            }
        }
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotonContorno(texto = stringResource(R.string.accion_cancelar), alTocar = alCerrar, modifier = Modifier.weight(1f), relleno = RellenoBotonAngosto)
            BotonPeligro(texto = textoConfirmar, alTocar = alConfirmar, modifier = Modifier.weight(1f), relleno = RellenoBotonAngosto)
        }
    }
}

/**
 * Hoja de ayuda con pasos numerados (Figma: "Estar en la red de la empresa" y "¿Dónde encuentro el
 * código?"). Con una acción secundaria, "Entendido" queda con borde y la otra llena.
 */
@Composable
fun HojaAyuda(
    titulo: String,
    introduccion: String,
    pasos: List<String>,
    alCerrar: () -> Unit,
    accion: AccionDeHoja? = null,
) {
    HojaInferior(alCerrar = alCerrar) {
        EncabezadoDeHoja(titulo, introduccion)
        pasos.forEachIndexed { indice, paso ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumeroDePaso(indice + 1)
                Text(text = paso, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (accion == null) {
                BotonPrincipal(texto = stringResource(R.string.accion_entendido), alTocar = alCerrar)
            } else {
                BotonContorno(texto = stringResource(R.string.accion_entendido), alTocar = alCerrar, modifier = Modifier.weight(1f), relleno = RellenoBotonAngosto)
                BotonPrincipal(texto = accion.texto, alTocar = accion.alTocar, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Número de paso en un círculo verde claro. */
@Composable
fun NumeroDePaso(numero: Int, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = numero.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
