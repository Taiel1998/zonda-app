package ar.edu.uade.zonda.ui.primerinicio

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.abrirAjustesDeLaApp
import ar.edu.uade.zonda.ui.components.SelectorDeAvisos
import ar.edu.uade.zonda.ui.tienePermiso

/**
 * Preferencias de avisos del primer inicio (RF05; Figma, figura 9): cómo avisa cada severidad y el
 * permiso de notificaciones. Sin el permiso la app sigue funcionando y explica cómo activarlo (RNF06).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvisosScreen(
    avisos: PreferenciasAvisos,
    alCambiar: (Severidad, ModoAviso) -> Unit,
    alContinuar: () -> Unit,
    alVolver: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inicio_antes_de_empezar)) },
                navigationIcon = {
                    if (alVolver != null) {
                        IconButton(onClick = alVolver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                        }
                    }
                },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(text = stringResource(R.string.avisos_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = stringResource(R.string.avisos_titulo), style = MaterialTheme.typography.titleSmall)
                    Text(text = stringResource(R.string.avisos_texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectorDeAvisos(avisos = avisos, alCambiar = alCambiar)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TarjetaNotificaciones()
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = alContinuar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.avisos_continuar))
            }
        }
    }
}

/**
 * El permiso de notificaciones: pedirlo, y si se rechazó, explicar cómo activarlo desde los ajustes
 * (RNF06). Existe desde Android 13; antes no hace falta. Lo usan el primer inicio y Ajustes.
 */
@Composable
fun TarjetaNotificaciones(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val contexto = LocalContext.current
    var permitido by remember { mutableStateOf(tienePermiso(contexto, Manifest.permission.POST_NOTIFICATIONS)) }
    var rechazado by remember { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permitido = concedido
        rechazado = !concedido
    }
    // Al volver de los ajustes del sistema, el permiso pudo haber cambiado.
    LifecycleResumeEffect(Unit) {
        permitido = tienePermiso(contexto, Manifest.permission.POST_NOTIFICATIONS)
        onPauseOrDispose { }
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = stringResource(R.string.notificaciones_titulo), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(R.string.notificaciones_texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            when {
                permitido -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.notificaciones_permitidas), style = MaterialTheme.typography.bodyMedium)
                }
                rechazado -> Column {
                    Text(text = stringResource(R.string.notificaciones_rechazadas), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { abrirAjustesDeLaApp(contexto) }) { Text(stringResource(R.string.abrir_ajustes)) }
                }
                else -> FilledTonalButton(onClick = { lanzador.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.notificaciones_permitir))
                }
            }
        }
    }
}
