package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Instant
import java.time.ZoneId

// Estados compartidos por las pantallas de datos (Figma, figura 19): banner de sin conexión, estado
// vacío, estado de error y esqueleto de carga.

/**
 * Banner ámbar bajo la barra superior con la nube tachada: "Sin conexión · mostrando datos de las
 * 02:14" (informe, 14.2). Algunas pantallas cambian el texto, por ejemplo el Detalle pendiente.
 */
@Composable
fun BannerSinConexion(
    datosDe: Instant?,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
    texto: String? = null,
) {
    Surface(color = Tonos.Media.fondo, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Outlined.CloudOff, contentDescription = null, tint = Tonos.Media.texto, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = texto ?: if (datosDe == null) {
                    stringResource(R.string.sin_conexion_banner_sin_datos)
                } else {
                    stringResource(R.string.sin_conexion_banner, Formato.hora(datosDe, zona))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Tonos.Media.texto,
            )
        }
    }
}

/** 401: el token venció o fue revocado. La caché sigue a la vista; hay que parear de nuevo (RNF12). */
@Composable
fun BannerAccesoVencido(alConectar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = Tonos.Alta.fondo, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, tint = Tonos.Alta.texto, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.acceso_vencido_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = Tonos.Alta.texto,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = alConectar) { Text(stringResource(R.string.acceso_vencido_conectar)) }
        }
    }
}

/** Círculo claro, título, detalle y, si hay, una acción de recuperación (Figma, figura 19). */
@Composable
fun EstadoVacio(
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier,
    accion: String? = null,
    alAccionar: () -> Unit = {},
) {
    EstadoConCirculo(
        colorCirculo = MaterialTheme.colorScheme.surfaceVariant,
        titulo = titulo,
        detalle = detalle,
        accion = accion,
        alAccionar = alAccionar,
        modifier = modifier,
    )
}

/** "No se pudo cargar" con Reintentar: el mismo esquema que el vacío, con el círculo rosa. */
@Composable
fun EstadoError(
    titulo: String,
    detalle: String,
    alReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EstadoConCirculo(
        colorCirculo = MaterialTheme.colorScheme.errorContainer,
        titulo = titulo,
        detalle = detalle,
        accion = stringResource(R.string.error_reintentar),
        alAccionar = alReintentar,
        modifier = modifier,
    )
}

@Composable
private fun EstadoConCirculo(
    colorCirculo: Color,
    titulo: String,
    detalle: String,
    accion: String?,
    alAccionar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colorCirculo, CircleShape),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (accion != null) {
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = alAccionar) { Text(accion) }
        }
    }
}

/** Bloques gris cálido mientras llega la primera lectura de la base (nunca más de un instante). */
@Composable
fun EsqueletoLista(modifier: Modifier = Modifier, cantidad: Int = 4) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(cantidad) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EstadosPreview() {
    ZondaTheme {
        Column {
            BannerSinConexion(datosDe = null)
            EstadoVacio(
                titulo = stringResource(R.string.vacio_con_filtro_titulo),
                detalle = stringResource(R.string.vacio_con_filtro_detalle),
                accion = stringResource(R.string.filtro_quitar),
                modifier = Modifier.height(260.dp),
            )
        }
    }
}
