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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.encabezado
import ar.edu.uade.zonda.ui.theme.opcion
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
                style = MaterialTheme.typography.opcion,
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

/** Círculo claro, título, detalle y, si hay, una acción de recuperación (Figma 2-218). */
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

/** "No se pudo cargar" con Reintentar: el mismo esquema que el vacío, con el círculo rosa (Figma 2-255). */
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
    // Figma 2-248: 24 a los costados, 40 arriba y abajo, 8 entre piezas y el botón 8 más abajo.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colorCirculo, CircleShape),
        )
        Text(text = titulo, style = MaterialTheme.typography.encabezado, textAlign = TextAlign.Center)
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (accion != null) {
            BotonTonal(texto = accion, alTocar = alAccionar, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * Bloques gris cálido mientras llega la primera lectura de la base (Figma 2-292): la línea de resumen
 * de 220 por 14 y tarjetas de 100 de alto, con 16 entre piezas.
 */
@Composable
fun EsqueletoLista(modifier: Modifier = Modifier, cantidad: Int = 6) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = 14.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
        )
        repeat(cantidad) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
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
                accion = stringResource(R.string.filtro_limpiar_filtros),
                modifier = Modifier.height(260.dp),
            )
        }
    }
}
