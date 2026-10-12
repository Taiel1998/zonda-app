package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.Tono
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.ZondaTheme

// Indicadores del Figma: severidad, estado de revisión, envío pendiente, método de detección y
// veredicto. El color nunca va solo: siempre con texto (RNF09).

fun Severidad.tono(): Tono = when (this) {
    Severidad.ALTA -> Tonos.Alta
    Severidad.MEDIA -> Tonos.Media
    Severidad.BAJA -> Tonos.Baja
}

fun Severidad.color(): Color = tono().texto

fun Severidad.fondo(): Color = tono().fondo

/** Sin revisar en azul, revisada en verde y falso positivo en gris, como en el Figma. */
fun EstadoRevision.tono(): Tono = when (this) {
    EstadoRevision.SIN_REVISAR -> Tonos.Info
    EstadoRevision.ATAQUE_REAL -> Tonos.Baja
    EstadoRevision.FALSO_POSITIVO -> Tonos.Neutro
}

/** Etiqueta chica con fondo de color y texto en mayúsculas, opcionalmente con un punto de color. */
@Composable
fun Etiqueta(
    texto: String,
    tono: Tono,
    modifier: Modifier = Modifier,
    conPunto: Boolean = false,
) {
    Surface(color = tono.fondo, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (conPunto) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(tono.punto, CircleShape),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = texto.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = tono.texto,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun IndicadorSeveridad(severidad: Severidad, modifier: Modifier = Modifier) {
    Etiqueta(
        texto = stringResource(severidad.etiqueta()),
        tono = severidad.tono(),
        modifier = modifier,
        conPunto = true,
    )
}

@Composable
fun IndicadorEstadoRevision(estado: EstadoRevision, modifier: Modifier = Modifier) {
    Etiqueta(texto = stringResource(estado.etiqueta()), tono = estado.tono(), modifier = modifier)
}

/**
 * Reloj y "pendiente" (con la hora, si se pasa), "no enviada" o "revisada por otra persona"
 * (informe, figura 24).
 */
@Composable
fun IndicadorEnvio(envio: EstadoEnvio, modifier: Modifier = Modifier, hora: String? = null) {
    val (color, icono) = when (envio) {
        EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIANDO -> Tonos.Info.texto to Icons.Outlined.Schedule
        EstadoEnvio.ERROR -> Tonos.Alta.texto to Icons.Outlined.ErrorOutline
        EstadoEnvio.CONFLICTO -> Tonos.Media.texto to Icons.Outlined.Info
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icono, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = stringResource(envio.etiqueta()), style = MaterialTheme.typography.labelMedium, color = color)
        if (hora != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = hora, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Etiqueta gris cálido con el método que detectó la alerta: IA, Umbrales o Reglas. */
@Composable
fun EtiquetaMetodo(metodo: MetodoDeteccion, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(metodo.etiqueta()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** ANÓMALO o NORMAL, el veredicto de cada método en el detalle (RF03). */
@Composable
fun IndicadorVeredicto(anomalo: Boolean, modifier: Modifier = Modifier) {
    if (anomalo) {
        Etiqueta(stringResource(R.string.veredicto_anomalo), Tonos.Alta, modifier)
    } else {
        Etiqueta(stringResource(R.string.veredicto_normal), Tonos.Baja, modifier)
    }
}

@Preview(showBackground = true)
@Composable
private fun IndicadoresPreview() {
    ZondaTheme {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IndicadorSeveridad(Severidad.ALTA)
            Spacer(modifier = Modifier.width(6.dp))
            IndicadorSeveridad(Severidad.MEDIA)
            Spacer(modifier = Modifier.width(6.dp))
            IndicadorSeveridad(Severidad.BAJA)
            Spacer(modifier = Modifier.width(6.dp))
            IndicadorEstadoRevision(EstadoRevision.SIN_REVISAR)
            Spacer(modifier = Modifier.width(6.dp))
            IndicadorEnvio(EstadoEnvio.PENDIENTE, hora = "02:09")
        }
    }
}
