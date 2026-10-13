package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Una columna que ocupa toda la pantalla y se desplaza si no entra. Un `Spacer(Modifier.weight(1f))`
 * empuja lo que sigue hasta abajo (los botones de la bienvenida y del tutorial, como en el Figma) y,
 * con la letra agrandada (RNF09), la pantalla se desplaza en lugar de cortarse.
 */
@Composable
fun ColumnaDePantalla(
    modifier: Modifier = Modifier,
    relleno: PaddingValues = PaddingValues(16.dp),
    espacio: Dp = 16.dp,
    alineacion: Alignment.Horizontal = Alignment.Start,
    alineacionVertical: Alignment.Vertical = Alignment.Top,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(relleno),
            verticalArrangement = Arrangement.spacedBy(espacio, alineacionVertical),
            horizontalAlignment = alineacion,
            content = contenido,
        )
    }
}
