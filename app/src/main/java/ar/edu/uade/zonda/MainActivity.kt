package ar.edu.uade.zonda

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.domain.model.Tema
import ar.edu.uade.zonda.ui.navigation.ZondaNavHost
import ar.edu.uade.zonda.ui.theme.ZondaTheme

/**
 * Única Activity de la app. Toda la navegación entre pantallas la resuelve Navigation Compose
 * dentro de [ZondaNavHost]; la Activity solo arma el tema y el contenido. El tema sale de Ajustes →
 * Apariencia → Tema (decisión 43): de fábrica sigue al teléfono, y lo elegido manda sobre el teléfono. Es
 * una FragmentActivity porque el diálogo de la huella de Android la necesita (decisión 51).
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferencias = remember { (application as ZondaApp).container.preferencias }
            val tema by preferencias.observarTema().collectAsStateWithLifecycle(initialValue = Tema.COMO_EL_TELEFONO)
            val oscuro = tema.esOscuro(telefonoOscuro = isSystemInDarkTheme())
            // Los íconos de las barras del sistema siguen al tema de la app, no al del teléfono.
            DisposableEffect(oscuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro },
                    navigationBarStyle = SystemBarStyle.auto(VELO_CLARO, VELO_OSCURO) { oscuro },
                )
                onDispose {}
            }
            ZondaTheme(oscuro = oscuro) {
                ZondaNavHost()
            }
        }
    }

    private companion object {
        // Los velos de la barra de navegación de tres botones que usa enableEdgeToEdge por defecto.
        val VELO_CLARO = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val VELO_OSCURO = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
