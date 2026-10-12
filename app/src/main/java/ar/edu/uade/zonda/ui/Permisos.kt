package ar.edu.uade.zonda.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

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
