package ar.edu.uade.zonda.ui.primerinicio

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.LogoZonda
import ar.edu.uade.zonda.ui.theme.VerdeZondaClaro

/**
 * Bienvenida (RF05; Figma, figura 9). Para empezar hay que aceptar las condiciones, que se abren
 * desde un único enlace. "Ya conozco Zonda" saltea el tutorial y las preferencias y va al pareo.
 */
@Composable
fun BienvenidaScreen(
    alVerCondiciones: () -> Unit,
    alEmpezar: () -> Unit,
    alOmitirIntroduccion: () -> Unit,
) {
    var acepto by rememberSaveable { mutableStateOf(false) }
    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                LogoZonda()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.bienvenida_titulo), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.bienvenida_texto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Caracteristica(Icons.Filled.Notifications, stringResource(R.string.bienvenida_avisos_titulo), stringResource(R.string.bienvenida_avisos_texto))
                Caracteristica(Icons.Filled.Info, stringResource(R.string.bienvenida_detalle_titulo), stringResource(R.string.bienvenida_detalle_texto))
                Caracteristica(Icons.Filled.CheckCircle, stringResource(R.string.bienvenida_sin_red_titulo), stringResource(R.string.bienvenida_sin_red_texto))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acepto, onCheckedChange = { acepto = it })
                val color = MaterialTheme.colorScheme.primary
                val texto = buildAnnotatedString {
                    append(stringResource(R.string.bienvenida_acepto))
                    append(" ")
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "condiciones",
                            styles = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
                        ) { alVerCondiciones() },
                    ) { append(stringResource(R.string.bienvenida_condiciones_enlace)) }
                }
                Text(text = texto, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = alEmpezar, enabled = acepto, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bienvenida_empezar))
            }
            TextButton(onClick = alOmitirIntroduccion, enabled = acepto, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.bienvenida_omitir))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Caracteristica(icono: ImageVector, titulo: String, texto: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(VerdeZondaClaro, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = titulo, style = MaterialTheme.typography.titleSmall)
            Text(text = texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Condiciones de uso y privacidad, el único enlace de la bienvenida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CondicionesScreen(alVolver: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.condiciones_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                    }
                },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.condiciones_texto), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
