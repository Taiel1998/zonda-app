package ar.edu.uade.zonda.ui.recorrido

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Lo que resalta cada paso del recorrido (Figma 107-3321 a 107-4731). Cada pantalla marca sus
 * elementos con [objetivoDelRecorrido]; Estado y el Resumen los marcan cuando existan, y mientras
 * tanto esos pasos muestran la tarjeta sola.
 */
enum class ObjetivoRecorrido {
    // Estado
    SEMAFORO,
    CONTADORES,
    SINCRONIZAR,
    ICONO_RESUMEN,

    // Barra inferior
    PESTANIA_ALERTAS,

    // Alertas
    CHIPS,
    PRIMERA_TARJETA,

    // Detalle
    BOTON_REVISAR,

    // Resumen
    CIFRAS_RESUMEN,
    PERIODO_RESUMEN,

    // Ajustes
    TARJETA_AVISOS,
}

/** Dónde quedó cada objetivo en la pantalla, en coordenadas de la raíz, para la capa del recorrido. */
@Stable
class RegistroRecorrido {

    private val posiciones = mutableStateMapOf<ObjetivoRecorrido, Rect>()

    /** El objetivo del paso a la vista: si quedó fuera de la pantalla, se desplaza hasta él. */
    var actual by mutableStateOf<ObjetivoRecorrido?>(null)

    fun posicionDe(objetivo: ObjetivoRecorrido): Rect? = posiciones[objetivo]

    fun registrar(objetivo: ObjetivoRecorrido, posicion: Rect) {
        if (posiciones[objetivo] != posicion) posiciones[objetivo] = posicion
    }

    fun quitar(objetivo: ObjetivoRecorrido) {
        posiciones.remove(objetivo)
    }
}

/** El registro del recorrido de la app; sin recorrido (en las pruebas de cada pantalla) es null. */
val LocalRegistroRecorrido = staticCompositionLocalOf<RegistroRecorrido?> { null }

/**
 * Marca un elemento para que el recorrido lo resalte. Si es el del paso a la vista y no se ve entero
 * (el botón Revisar en un teléfono chico), la pantalla se desplaza hasta él.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.objetivoDelRecorrido(objetivo: ObjetivoRecorrido): Modifier = composed {
    val registro = LocalRegistroRecorrido.current ?: return@composed Modifier
    val pedido = remember { BringIntoViewRequester() }
    DisposableEffect(registro, objetivo) {
        onDispose { registro.quitar(objetivo) }
    }
    LaunchedEffect(registro.actual) {
        if (registro.actual == objetivo) pedido.bringIntoView()
    }
    Modifier
        .bringIntoViewRequester(pedido)
        .onGloballyPositioned { coordenadas -> registro.registrar(objetivo, coordenadas.boundsInRoot()) }
}
