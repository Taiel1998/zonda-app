package ar.edu.uade.zonda.ui.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.EsqueletoLista
import ar.edu.uade.zonda.ui.components.EstadoVacio
import ar.edu.uade.zonda.ui.components.TarjetaAlerta
import ar.edu.uade.zonda.ui.theme.nota
import java.time.ZoneId

/**
 * "Ver estas alertas" del Resumen (cambio 16; Figma 463-1669): una lista propia encima del Resumen,
 * "← 9 alertas", con la línea "Del Resumen: sábado y domingo · fuerza bruta" y las mismas tarjetas de
 * Alertas, de la más nueva a la más vieja. Lee el ViewModel del Resumen, así que la flecha vuelve al
 * Resumen con los filtros como estaban. No tiene filtros propios: se arman antes, en la hoja.
 */
@Composable
fun AlertasDelResumenScreen(
    viewModel: ResumenViewModel,
    alAbrirDetalle: (alertaId: String) -> Unit,
    alVolver: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AlertasDelResumenContenido(estado = estado, alAbrirDetalle = alAbrirDetalle, alVolver = alVolver)
}

@Composable
fun AlertasDelResumenContenido(
    estado: ResumenUiState,
    alAbrirDetalle: (alertaId: String) -> Unit,
    alVolver: () -> Unit,
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val resumen = estado.resumen
    val alertas = resumen?.alertas.orEmpty()
    val recursos = LocalContext.current.resources
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BarraSuperior(
                titulo = pluralStringResource(R.plurals.resumen_lista_titulo, alertas.size, alertas.size),
                alVolver = alVolver,
            )
        },
    ) { relleno ->
        if (resumen == null) {
            EsqueletoLista(modifier = Modifier.padding(relleno).padding(16.dp), cantidad = 4)
            return@Scaffold
        }
        // Figma 463-1669: 16 de margen, la línea Regular 12 gris y 16 entre tarjetas.
        LazyColumn(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = lineaDeLaLista(resumen, recursos, estado.altas),
                    style = MaterialTheme.typography.nota,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (alertas.isEmpty()) {
                item {
                    EstadoVacio(
                        titulo = stringResource(R.string.resumen_lista_vacia),
                        detalle = stringResource(R.string.resumen_lista_vacia_detalle),
                    )
                }
            }
            items(alertas, key = { it.id }) { alerta ->
                TarjetaAlerta(alerta = alerta, alTocar = alAbrirDetalle, zona = zona)
            }
        }
    }
}
