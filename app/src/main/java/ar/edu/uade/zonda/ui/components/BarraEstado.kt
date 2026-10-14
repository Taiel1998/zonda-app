package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.objetivoDelRecorrido
import ar.edu.uade.zonda.ui.theme.ZondaTheme

/**
 * La barra de Estado (Figma 1-3): el logo de 28 a 16 del borde, "Zonda" en Medium 22 a 12 del logo y,
 * a la derecha, el ícono del Resumen y el de sincronizar, con 12 entre los dos y 16 hasta el borde.
 *
 * Es una pieza de la pantalla Estado, que escribe Taiel; mientras tanto la usa la Estado provisoria
 * para abrir el Resumen. El de sincronizar aparece solo si se le pasa [alSincronizar]. Los dos íconos
 * quedan marcados para los pasos 3 y 4 del recorrido.
 */
@Composable
fun BarraEstado(
    alAbrirResumen: () -> Unit,
    modifier: Modifier = Modifier,
    alSincronizar: (() -> Unit)? = null,
    sincronizando: Boolean = false,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(AltoBarraSuperior)
                .padding(start = 16.dp, end = 4.dp),
        ) {
            LogoZonda(tamanio = 28.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            // Los botones son de 48 para el toque, pero los íconos van a 36 uno del otro, como el Figma.
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = alAbrirResumen) {
                    Icon(
                        imageVector = IconoResumen,
                        contentDescription = stringResource(R.string.resumen_abrir),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.ICONO_RESUMEN),
                    )
                }
                if (alSincronizar != null) {
                    IconButton(onClick = alSincronizar, enabled = !sincronizando) {
                        if (sincronizando) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = stringResource(R.string.accion_sincronizar),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.SINCRONIZAR),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * El ícono del Resumen del Figma (1-3): tres barras con las puntas redondeadas apoyadas abajo, la del
 * medio la más alta y la de la izquierda la más baja.
 */
val IconoResumen: ImageVector by lazy {
    ImageVector.Builder(name = "Resumen", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            path(fill = SolidColor(Color.Black)) {
                barra(izquierda = 2.75f, arriba = 12f, derecha = 7.25f, abajo = 20.75f)
                barra(izquierda = 9.75f, arriba = 6f, derecha = 14.25f, abajo = 20.75f)
                barra(izquierda = 16.75f, arriba = 9f, derecha = 21.25f, abajo = 20.75f)
            }
        }
        .build()
}

/** Un rectángulo con las esquinas redondeadas de 1. */
private fun PathBuilder.barra(izquierda: Float, arriba: Float, derecha: Float, abajo: Float, radio: Float = 1f) {
    moveTo(izquierda + radio, arriba)
    lineTo(derecha - radio, arriba)
    arcToRelative(radio, radio, 0f, false, true, radio, radio)
    lineTo(derecha, abajo - radio)
    arcToRelative(radio, radio, 0f, false, true, -radio, radio)
    lineTo(izquierda + radio, abajo)
    arcToRelative(radio, radio, 0f, false, true, -radio, -radio)
    lineTo(izquierda, arriba + radio)
    arcToRelative(radio, radio, 0f, false, true, radio, -radio)
    close()
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun BarraEstadoPreview() {
    ZondaTheme {
        BarraEstado(alAbrirResumen = {}, alSincronizar = {})
    }
}
