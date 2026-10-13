package ar.edu.uade.zonda.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

// Permisos de Android (cámara y notificaciones). La app sigue funcionando sin ellos y explica cómo
// activarlos (RNF06): si la persona los rechazó, el único camino es la pantalla de ajustes de la app.

fun tienePermiso(contexto: Context, permiso: String): Boolean =
    ContextCompat.checkSelfPermission(contexto, permiso) == PackageManager.PERMISSION_GRANTED

/** Abre los ajustes del sistema de Zonda, donde se activan los permisos rechazados. */
fun abrirAjustesDeLaApp(contexto: Context) {
    val intento = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(intento)
}

/** Abre directo los ajustes de notificaciones de Zonda en el sistema (Figma 4-130). */
fun abrirAjustesDeNotificaciones(contexto: Context) {
    val intento = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(intento)
}

enum class EstadoPermiso { CONCEDIDO, SIN_PEDIR, RECHAZADO }

/** El estado del permiso de notificaciones y cómo pedirlo. */
class PermisoNotificaciones(val estado: EstadoPermiso, val pedir: () -> Unit)

/**
 * El permiso de notificaciones de Android 13 o más nuevo. En versiones anteriores no hay permiso que
 * pedir: cuenta solo si la persona apagó las notificaciones de Zonda desde el sistema. Se vuelve a
 * leer al volver de los ajustes del sistema.
 */
@Composable
fun rememberPermisoNotificaciones(alResponder: (concedido: Boolean) -> Unit = {}): PermisoNotificaciones {
    val contexto = LocalContext.current
    var pedido by rememberSaveable { mutableStateOf(false) }
    var estado by remember { mutableStateOf(leerPermisoNotificaciones(contexto, pedido)) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        pedido = true
        estado = leerPermisoNotificaciones(contexto, pedido = true)
        alResponder(concedido)
    }
    LifecycleResumeEffect(Unit) {
        estado = leerPermisoNotificaciones(contexto, pedido)
        onPauseOrDispose { }
    }
    return PermisoNotificaciones(estado) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && estado == EstadoPermiso.SIN_PEDIR) {
            lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            alResponder(estado == EstadoPermiso.CONCEDIDO)
        }
    }
}

private fun leerPermisoNotificaciones(contexto: Context, pedido: Boolean): EstadoPermiso {
    if (NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return EstadoPermiso.CONCEDIDO
    val sePuedePedir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !tienePermiso(contexto, Manifest.permission.POST_NOTIFICATIONS) && !pedido
    return if (sePuedePedir) EstadoPermiso.SIN_PEDIR else EstadoPermiso.RECHAZADO
}
