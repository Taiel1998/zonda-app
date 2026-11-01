package ar.edu.uade.zonda.ui.primerinicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.ColumnaDePantalla
import ar.edu.uade.zonda.ui.components.HojaCondiciones
import ar.edu.uade.zonda.ui.components.LogoZonda
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.cuerpo
import ar.edu.uade.zonda.ui.theme.tituloBienvenida

/**
 * Bienvenida (RF05; Figma 50-780 y 35-213). Para empezar hay que aceptar las condiciones, que se
 * abren en una hoja desde el único enlace (Figma 50-830). "Ya conozco Zonda" saltea el tutorial y las
 * preferencias y va al pareo. Los dos botones se habilitan recién al aceptar.
 */
@Composable
fun BienvenidaScreen(
    alEmpezar: () -> Unit,
    alOmitirIntroduccion: () -> Unit,
) {
    var acepto by rememberSaveable { mutableStateOf(false) }
    var verCondiciones by rememberSaveable { mutableStateOf(false) }

    ColumnaDePantalla(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Figma 35-217: 24 libres arriba del logo, que es de 96.
        Spacer(modifier = Modifier.height(24.dp))
        LogoZonda(tamanio = 96.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(R.string.bienvenida_titulo),
            style = MaterialTheme.typography.tituloBienvenida,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.bienvenida_texto),
            style = MaterialTheme.typography.cuerpo,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Caracteristica(Icons.Filled.Notifications, stringResource(R.string.bienvenida_avisos_titulo), stringResource(R.string.bienvenida_avisos_texto))
            Caracteristica(Icons.Filled.VerifiedUser, stringResource(R.string.bienvenida_detalle_titulo), stringResource(R.string.bienvenida_detalle_texto))
            Caracteristica(Icons.Outlined.CloudOff, stringResource(R.string.bienvenida_sin_red_titulo), stringResource(R.string.bienvenida_sin_red_texto))
        }
        Spacer(modifier = Modifier.weight(1f))
        AceptoCondiciones(acepto = acepto, alCambiar = { acepto = it }, alVerCondiciones = { verCondiciones = true })
        BotonPrincipal(texto = stringResource(R.string.bienvenida_empezar), alTocar = alEmpezar, habilitado = acepto)
        BotonContorno(
            texto = stringResource(R.string.bienvenida_omitir),
            alTocar = alOmitirIntroduccion,
            habilitado = acepto,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (verCondiciones) {
        HojaCondiciones(alCerrar = { verCondiciones = false })
    }
}

/** Una fila de la bienvenida: ícono verde de 22 en un cuadrado verde claro de 40, título y detalle. */
@Composable
private fun Caracteristica(icono: ImageVector, titulo: String, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleSmall)
            Text(text = texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * La casilla de 20 con esquinas de 4 y "Leí y acepto las condiciones de uso y privacidad", con el
 * enlace subrayado. Toda la fila marca la casilla; el enlace abre la hoja.
 */
@Composable
private fun AceptoCondiciones(acepto: Boolean, alCambiar: (Boolean) -> Unit, alVerCondiciones: () -> Unit) {
    val verde = MaterialTheme.colorScheme.primary
    val texto = buildAnnotatedString {
        append(stringResource(R.string.bienvenida_acepto))
        append(" ")
        withLink(
            LinkAnnotation.Clickable(
                tag = "condiciones",
                styles = TextLinkStyles(SpanStyle(color = verde, textDecoration = TextDecoration.Underline)),
            ) { alVerCondiciones() },
        ) { append(stringResource(R.string.bienvenida_condiciones_enlace)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = acepto, onValueChange = alCambiar, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (acepto) verde else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(20.dp)
                .then(if (acepto) Modifier else Modifier.border(2.dp, Tonos.Neutro.punto, RoundedCornerShape(4.dp))),
        ) {
            if (acepto) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(text = texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
