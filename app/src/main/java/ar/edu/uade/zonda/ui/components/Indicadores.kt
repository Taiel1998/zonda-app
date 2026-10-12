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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.EnvioConflicto
import ar.edu.uade.zonda.ui.theme.EnvioError
import ar.edu.uade.zonda.ui.theme.EnvioPendiente
import ar.edu.uade.zonda.ui.theme.EstadoFalsoPositivo
import ar.edu.uade.zonda.ui.theme.EstadoFalsoPositivoFondo
import ar.edu.uade.zonda.ui.theme.EstadoRevisada
import ar.edu.uade.zonda.ui.theme.EstadoRevisadaFondo
import ar.edu.uade.zonda.ui.theme.EstadoSinRevisar
import ar.edu.uade.zonda.ui.theme.EstadoSinRevisarFondo
import ar.edu.uade.zonda.ui.theme.SeveridadAlta
import ar.edu.uade.zonda.ui.theme.SeveridadAltaFondo
import ar.edu.uade.zonda.ui.theme.SeveridadBaja
import ar.edu.uade.zonda.ui.theme.SeveridadBajaFondo
import ar.edu.uade.zonda.ui.theme.SeveridadMedia
import ar.edu.uade.zonda.ui.theme.SeveridadMediaFondo
import ar.edu.uade.zonda.ui.theme.VeredictoAnomalo
import ar.edu.uade.zonda.ui.theme.VeredictoAnomaloFondo
import ar.edu.uade.zonda.ui.theme.VeredictoNormal
import ar.edu.uade.zonda.ui.theme.VeredictoNormalFondo

// Indicadores de la página Componentes de Figma: severidad, estado de revisión, envío pendiente,
// método de detección y veredicto. El color nunca va solo: siempre con texto (RNF09).

fun Severidad.color(): Color = when (this) {
    Severidad.ALTA -> SeveridadAlta
    Severidad.MEDIA -> SeveridadMedia
    Severidad.BAJA -> SeveridadBaja
}

fun Severidad.fondo(): Color = when (this) {
    Severidad.ALTA -> SeveridadAltaFondo
    Severidad.MEDIA -> SeveridadMediaFondo
    Severidad.BAJA -> SeveridadBajaFondo
}

/** Etiqueta chica con fondo de color y texto en mayúsculas, opcionalmente con un punto de color. */
@Composable
fun Etiqueta(
    texto: String,
    colorTexto: Color,
    colorFondo: Color,
    modifier: Modifier = Modifier,
    conPunto: Boolean = false,
) {
    Surface(color = colorFondo, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (conPunto) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(colorTexto, CircleShape),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = texto.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colorTexto,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun IndicadorSeveridad(severidad: Severidad, modifier: Modifier = Modifier) {
    Etiqueta(
        texto = stringResource(severidad.etiqueta()),
        colorTexto = severidad.color(),
        colorFondo = severidad.fondo(),
        modifier = modifier,
        conPunto = true,
    )
}

@Composable
fun IndicadorEstadoRevision(estado: EstadoRevision, modifier: Modifier = Modifier) {
    val (color, fondo) = when (estado) {
        EstadoRevision.SIN_REVISAR -> EstadoSinRevisar to EstadoSinRevisarFondo
        EstadoRevision.ATAQUE_REAL -> EstadoRevisada to EstadoRevisadaFondo
        EstadoRevision.FALSO_POSITIVO -> EstadoFalsoPositivo to EstadoFalsoPositivoFondo
    }
    Etiqueta(texto = stringResource(estado.etiqueta()), colorTexto = color, colorFondo = fondo, modifier = modifier)
}

/** "pendiente", "no enviada" o "revisada por otra persona", con su ícono (informe, figura 24). */
@Composable
fun IndicadorEnvio(envio: EstadoEnvio, modifier: Modifier = Modifier) {
    val (color, icono) = when (envio) {
        EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIANDO -> EnvioPendiente to Icons.Filled.Refresh
        EstadoEnvio.ERROR -> EnvioError to Icons.Filled.Warning
        EstadoEnvio.CONFLICTO -> EnvioConflicto to Icons.Filled.Info
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icono, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = stringResource(envio.etiqueta()), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Etiqueta gris con el método que detectó la alerta: IA, Umbrales o Reglas. */
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
        Etiqueta(stringResource(R.string.veredicto_anomalo), VeredictoAnomalo, VeredictoAnomaloFondo, modifier)
    } else {
        Etiqueta(stringResource(R.string.veredicto_normal), VeredictoNormal, VeredictoNormalFondo, modifier)
    }
}
