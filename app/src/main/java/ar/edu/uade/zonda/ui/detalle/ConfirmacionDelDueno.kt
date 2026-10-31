package ar.edu.uade.zonda.ui.detalle

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ar.edu.uade.zonda.domain.model.Perfil

/** Cómo terminó el pedido de la huella o el PIN. */
enum class Confirmacion { CONFIRMADA, CANCELADA, SIN_BLOQUEO_DE_PANTALLA }

/**
 * Lo que corta o devuelve tráfico se confirma con la huella del teléfono o, si no tiene, con su PIN o su
 * patrón (decisión 51): así un bloqueo lo hace el dueño del teléfono y no alguien que lo encontró
 * desbloqueado. Lo pide Android con su propio diálogo; la app no ve la huella ni el PIN.
 */
fun interface ConfirmacionDelDueno {
    fun pedir(titulo: String, detalle: String, alTerminar: (Confirmacion) -> Unit)
}

/** La de Android. Sin huella ni bloqueo de pantalla no se puede confirmar, y el bloqueo no sale. */
class ConfirmacionConHuella(private val actividad: FragmentActivity) : ConfirmacionDelDueno {

    override fun pedir(titulo: String, detalle: String, alTerminar: (Confirmacion) -> Unit) {
        if (BiometricManager.from(actividad).canAuthenticate(AUTENTICADORES) != BiometricManager.BIOMETRIC_SUCCESS) {
            alTerminar(Confirmacion.SIN_BLOQUEO_DE_PANTALLA)
            return
        }
        val dialogo = BiometricPrompt(
            actividad,
            ContextCompat.getMainExecutor(actividad),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) = alTerminar(Confirmacion.CONFIRMADA)

                // Cancelar, volver atrás o demasiados intentos. Un dedo que no coincide no llega acá: el diálogo deja probar de nuevo.
                override fun onAuthenticationError(codigo: Int, texto: CharSequence) = alTerminar(Confirmacion.CANCELADA)
            },
        )
        // Con DEVICE_CREDENTIAL el diálogo no lleva botón de cancelar propio: ofrece el PIN.
        dialogo.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(titulo).setSubtitle(detalle).setAllowedAuthenticators(AUTENTICADORES).build())
    }

    private companion object {
        // La huella (o la cara) o, si no hay, el PIN, el patrón o la contraseña del teléfono. Esta combinación
        // anda desde Android 8; con BIOMETRIC_STRONG no anda en Android 9 y 10.
        const val AUTENTICADORES = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}

/** La confirmación de la pantalla; las pruebas ponen una propia. Null: la de Android, con la Activity. */
val LocalConfirmacionDelDueno = staticCompositionLocalOf<ConfirmacionDelDueno?> { null }

/** La Activity de la app, para el diálogo de la huella; Compose puede darla envuelta. */
fun Context.actividad(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

/**
 * Qué acciones del bloqueo piden la huella: las que cambian lo que corta el firewall. Pedir un bloqueo
 * (analista) y rechazar un pedido no cortan nada.
 */
fun pideHuella(accion: AccionBloqueo, perfil: Perfil): Boolean = when (accion) {
    AccionBloqueo.BLOQUEAR -> perfil == Perfil.ADMINISTRADOR
    AccionBloqueo.CAMBIAR_DURACION, AccionBloqueo.DESBLOQUEAR -> true
    AccionBloqueo.RECHAZAR -> false
}

/**
 * Confirma una acción del bloqueo: si la pide, primero la huella. Sin motivo no la pide (la hoja tiene que
 * decir "Escribí el motivo." antes). Sin forma de confirmar (no hay Activity, o el teléfono no tiene ni
 * huella ni PIN) no sale; si la persona cancela el diálogo, no pasa nada y la hoja sigue abierta.
 */
fun confirmarConHuella(
    pide: Boolean,
    confirmacion: ConfirmacionDelDueno?,
    titulo: String,
    detalle: String,
    alConfirmar: () -> Unit,
    alNoPoder: () -> Unit,
) {
    if (!pide) {
        alConfirmar()
        return
    }
    if (confirmacion == null) {
        alNoPoder()
        return
    }
    confirmacion.pedir(titulo, detalle) { resultado ->
        when (resultado) {
            Confirmacion.CONFIRMADA -> alConfirmar()
            Confirmacion.SIN_BLOQUEO_DE_PANTALLA -> alNoPoder()
            Confirmacion.CANCELADA -> Unit
        }
    }
}
