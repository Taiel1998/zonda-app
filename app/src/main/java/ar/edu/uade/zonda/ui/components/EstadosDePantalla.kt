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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.theme.SeveridadMedia
import ar.edu.uade.zonda.ui.theme.SeveridadMediaFondo
import ar.edu.uade.zonda.ui.theme.VerdeZondaClaro
import java.time.Instant
import java.time.ZoneId

// Estados compartidos por las pantallas de datos (plan, sección 4.4; Figma, figura 19):
// banner de sin conexión, estado vacío, estado de error y esqueleto de carga.

/** Banner persistente bajo la barra superior: "Sin conexión · mostrando datos de las 02:14" (informe, 14.2). */
@Composable
fun BannerSinConexion(
    datosDe: Instant?,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
) {
    Surface(color = SeveridadMediaFondo, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = SeveridadMedia, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (datosDe == null) {
                    stringResource(R.string.sin_conexion_banner_sin_datos)
                } else {
                    stringResource(R.string.sin_conexion_banner, Formato.hora(datosDe, zona))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SeveridadMedia,
            )
        }
    }
}

/** 401: el token venció o fue revocado. La caché sigue a la vista; hay que parear de nuevo (RNF12). */
@Composable
fun BannerAccesoVencido(alConectar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.acceso_vencido_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = alConectar) { Text(stringResource(R.string.acceso_vencido_conectar)) }
        }
    }
}

/** Ícono en un círculo, título, detalle y, si hay, una acción de recuperación. */
@Composable
fun EstadoVacio(
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier,
    icono: ImageVector = Icons.Filled.CheckCircle,
    accion: String? = null,
    alAccionar: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(VerdeZondaClaro, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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

/** "No se pudo cargar" con Reintentar (Figma, figura 19). */
@Composable
fun EstadoError(
    titulo: String,
    detalle: String,
    alReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(onClick = alReintentar) { Text(stringResource(R.string.error_reintentar)) }
    }
}

/** Tarjetas grises mientras llega la primera lectura de la base (nunca más de un instante). */
@Composable
fun EsqueletoLista(modifier: Modifier = Modifier, cantidad: Int = 4) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(cantidad) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            )
        }
    }
}
