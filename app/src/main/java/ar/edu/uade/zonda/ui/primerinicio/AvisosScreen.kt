package ar.edu.uade.zonda.ui.primerinicio

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.abrirAjustesDeLaApp
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.BotonTonal
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.SelectorDeAvisos
import ar.edu.uade.zonda.ui.components.TituloSeccion
import ar.edu.uade.zonda.ui.tienePermiso

/**
 * Preferencias de avisos del primer inicio (RF05; Figma 35-392): cómo avisa cada severidad por
 * Telegram y el permiso de notificaciones. Sin flecha de volver, como en el Figma: se avanza con
 * "Continuar". Sin el permiso la app sigue funcionando y explica cómo activarlo (RNF06).
 */
@Composable
fun AvisosScreen(
    avisos: PreferenciasAvisos,
    alCambiar: (Severidad, ModoAviso) -> Unit,
    alContinuar: () -> Unit,
) {
    Scaffold(topBar = { BarraSuperior(titulo = stringResource(R.string.inicio_antes_de_empezar)) }) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.avisos_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Panel {
                TituloSeccion(stringResource(R.string.avisos_titulo))
                Text(
                    text = stringResource(R.string.avisos_texto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectorDeAvisos(avisos = avisos, alCambiar = alCambiar)
            }
            PanelNotificaciones()
            BotonPrincipal(texto = stringResource(R.string.avisos_continuar), alTocar = alContinuar)
        }
    }
}

/**
 * "Notificaciones en el teléfono" (Figma 35-392): el botón tonal con la campana pide el permiso, que
 * existe desde Android 13. En Android 12 o anterior no hay nada que pedir y se muestra cómo están las
 * notificaciones. Si se rechazó, explica cómo activarlas desde los ajustes (RNF06).
 */
@Composable
private fun PanelNotificaciones() {
    val contexto = LocalContext.current
    val conPermiso = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    fun permitidas(): Boolean =
        if (conPermiso) {
            tienePermiso(contexto, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(contexto).areNotificationsEnabled()
        }
    var permitido by remember { mutableStateOf(permitidas()) }
    var rechazado by remember { mutableStateOf(!conPermiso && !permitido) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permitido = concedido
        rechazado = !concedido
    }
    // Al volver de los ajustes del sistema, el permiso pudo haber cambiado.
    LifecycleResumeEffect(Unit) {
        permitido = permitidas()
        if (permitido) rechazado = false
        onPauseOrDispose { }
    }

    Panel {
        TituloSeccion(stringResource(R.string.notificaciones_titulo))
        Text(
            text = stringResource(R.string.notificaciones_texto),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            permitido -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(text = stringResource(R.string.notificaciones_permitidas), style = MaterialTheme.typography.bodySmall)
            }
            rechazado -> {
                Text(text = stringResource(R.string.notificaciones_rechazadas), style = MaterialTheme.typography.bodySmall)
                BotonContorno(texto = stringResource(R.string.abrir_ajustes), alTocar = { abrirAjustesDeLaApp(contexto) })
            }
            else -> BotonTonal(
                texto = stringResource(R.string.notificaciones_permitir),
                icono = Icons.Filled.Notifications,
                alTocar = { lanzador.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
    }
}
