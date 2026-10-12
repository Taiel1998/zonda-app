package ar.edu.uade.zonda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.edu.uade.zonda.ui.navigation.ZondaNavHost
import ar.edu.uade.zonda.ui.theme.ZondaTheme

/**
 * Única Activity de la app. Toda la navegación entre pantallas la resuelve Navigation Compose
 * dentro de [ZondaNavHost]; la Activity solo arma el tema y el contenido.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZondaTheme {
                ZondaNavHost()
            }
        }
    }
}
