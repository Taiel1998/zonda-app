package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.HojaConfirmacion

/**
 * Lo que hace la flecha de una pantalla que se guarda con "Guardar" (Avisos por severidad y Severidad por
 * tipo de ataque): sin cambios sale directo y, con cambios, pregunta "¿Salir sin guardar?" antes de
 * descartarlos, como el tablero (decisión 57). Antes la flecha los descartaba sin avisar, y un interruptor
 * cambiado se lee como "ya está".
 *
 * Devuelve la acción para la flecha y para el botón Atrás del teléfono; [alSalir] descarta el borrador y
 * vuelve.
 */
@Composable
fun salidaConCambios(hayCambios: Boolean, alSalir: () -> Unit): () -> Unit {
    var preguntando by rememberSaveable { mutableStateOf(false) }
    if (preguntando) {
        HojaConfirmacion(
            titulo = stringResource(R.string.salir_sin_guardar_titulo),
            texto = stringResource(R.string.salir_sin_guardar_texto),
            textoConfirmar = stringResource(R.string.salir_sin_guardar_confirmar),
            alConfirmar = {
                preguntando = false
                alSalir()
            },
            alCerrar = { preguntando = false },
        )
    }
    return { if (hayCambios) preguntando = true else alSalir() }
}
