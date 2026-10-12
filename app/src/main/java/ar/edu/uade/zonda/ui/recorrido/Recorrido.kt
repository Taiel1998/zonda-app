package ar.edu.uade.zonda.ui.recorrido

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.ui.navigation.Rutas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Los once pasos del recorrido por las pantallas principales (RF05). Cada paso se muestra sobre su
 * pestaña. Los del detalle se explican desde Alertas, que es de donde se abre.
 */
enum class PasoRecorrido(val ruta: String, @param:StringRes val titulo: Int, @param:StringRes val texto: Int) {
    SEMAFORO(Rutas.ESTADO, R.string.recorrido_1_titulo, R.string.recorrido_1_texto),
    CONTADORES(Rutas.ESTADO, R.string.recorrido_2_titulo, R.string.recorrido_2_texto),
    ACTUALIZADO(Rutas.ESTADO, R.string.recorrido_3_titulo, R.string.recorrido_3_texto),
    RESUMEN(Rutas.ESTADO, R.string.recorrido_4_titulo, R.string.recorrido_4_texto),
    LISTA(Rutas.ALERTAS, R.string.recorrido_5_titulo, R.string.recorrido_5_texto),
    FILTROS(Rutas.ALERTAS, R.string.recorrido_6_titulo, R.string.recorrido_6_texto),
    PENDIENTE(Rutas.ALERTAS, R.string.recorrido_7_titulo, R.string.recorrido_7_texto),
    DETALLE(Rutas.ALERTAS, R.string.recorrido_8_titulo, R.string.recorrido_8_texto),
    REVISAR(Rutas.ALERTAS, R.string.recorrido_9_titulo, R.string.recorrido_9_texto),
    AJUSTES(Rutas.AJUSTES, R.string.recorrido_10_titulo, R.string.recorrido_10_texto),
    SIN_CONEXION(Rutas.ESTADO, R.string.recorrido_11_titulo, R.string.recorrido_11_texto),
}

/**
 * Muestra el recorrido una sola vez: después del primer inicio, con una red pareada y mientras no se
 * haya terminado u omitido (queda marcado en DataStore).
 */
class RecorridoViewModel(
    private val preferencias: PreferenciasRepository,
    redes: RedesRepository,
) : ViewModel() {

    private val indice = MutableStateFlow(0)

    /** El paso a mostrar, o null si el recorrido no corresponde. */
    val paso: StateFlow<PasoRecorrido?> = combine(
        preferencias.observarRecorridoHecho(),
        preferencias.observarPrimerInicioHecho(),
        redes.observarRedActiva(),
        indice,
    ) { hecho, primerInicioHecho, red, i ->
        if (hecho || !primerInicioHecho || red == null) null else PasoRecorrido.entries[i]
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun siguiente() {
        if (indice.value < PasoRecorrido.entries.lastIndex) indice.value++ else terminar()
    }

    /** Terminar u omitir: no vuelve a aparecer. */
    fun terminar() {
        viewModelScope.launch { preferencias.marcarRecorridoHecho() }
    }
}

/** La tarjeta del recorrido sobre la pantalla, con un fondo oscuro que evita tocar lo de atrás. */
@Composable
fun TarjetaRecorrido(paso: PasoRecorrido, alSiguiente: () -> Unit, alOmitir: () -> Unit, modifier: Modifier = Modifier) {
    val ultimo = paso == PasoRecorrido.entries.last()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.recorrido_paso, paso.ordinal + 1, PasoRecorrido.entries.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = stringResource(paso.titulo), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(paso.texto), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!ultimo) TextButton(onClick = alOmitir) { Text(stringResource(R.string.recorrido_omitir)) }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = alSiguiente) {
                        Text(stringResource(if (ultimo) R.string.recorrido_terminar else R.string.recorrido_siguiente))
                    }
                }
            }
        }
    }
}
