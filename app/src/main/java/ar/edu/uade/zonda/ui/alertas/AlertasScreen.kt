package ar.edu.uade.zonda.ui.alertas

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BannerSinConexion
import ar.edu.uade.zonda.ui.components.EsqueletoLista
import ar.edu.uade.zonda.ui.components.EstadoVacio
import ar.edu.uade.zonda.ui.components.TarjetaAlerta
import ar.edu.uade.zonda.ui.enResumen
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.ZoneId

/**
 * Pantalla Alertas (RF02): la lista de la red activa con los chips de filtro, la línea de resumen,
 * el banner de sin conexión y la sincronización con el ícono o deslizando hacia abajo.
 */
@Composable
fun AlertasScreen(
    alAbrirDetalle: (alertaId: String) -> Unit,
    viewModel: AlertasViewModel = viewModelDeZonda {
        AlertasViewModel(it.observarAlertas, it.sincronizarAlertas, it.redes, it.conectividad)
    },
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AlertasContenido(
        estado = estado,
        alAbrirDetalle = alAbrirDetalle,
        alCambiarFiltro = viewModel::aplicarFiltro,
        alSincronizar = { viewModel.sincronizar() },
        alQuitarFiltros = viewModel::quitarFiltros,
        alAvisoMostrado = viewModel::avisoMostrado,
    )
}

/** La pantalla sin ViewModel: recibe el estado y devuelve las acciones. Así se prueba sin Android real. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasContenido(
    estado: AlertasUiState,
    alAbrirDetalle: (alertaId: String) -> Unit,
    alCambiarFiltro: (FiltroAlertas) -> Unit,
    alSincronizar: () -> Unit,
    alQuitarFiltros: () -> Unit,
    alAvisoMostrado: () -> Unit = {},
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        snackbar.showSnackbar(textoDelAviso(contexto, aviso))
        alAvisoMostrado()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.destino_alertas)) },
                actions = {
                    if (estado.sincronizando) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = alSincronizar, enabled = estado.contenido !is ContenidoAlertas.SinRed) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = stringResource(R.string.accion_sincronizar))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            if (estado.sinConexion) BannerSinConexion(datosDe = estado.datosDe, zona = zona)
            FiltrosAlertas(
                filtro = estado.filtro,
                catalogo = estado.catalogo,
                alCambiar = alCambiarFiltro,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            when (val contenido = estado.contenido) {
                ContenidoAlertas.Cargando -> EsqueletoLista()
                ContenidoAlertas.SinRed -> EstadoVacio(
                    titulo = stringResource(R.string.vacio_sin_red_titulo),
                    detalle = stringResource(R.string.vacio_sin_red_detalle),
                    icono = Icons.Filled.Info,
                )
                is ContenidoAlertas.Vacio -> if (contenido.conFiltro) {
                    EstadoVacio(
                        titulo = stringResource(R.string.vacio_con_filtro_titulo),
                        detalle = stringResource(R.string.vacio_con_filtro_detalle),
                        accion = stringResource(R.string.filtro_quitar),
                        alAccionar = alQuitarFiltros,
                    )
                } else {
                    EstadoVacio(
                        titulo = stringResource(R.string.vacio_sin_alertas_titulo),
                        detalle = stringResource(R.string.vacio_sin_alertas_detalle),
                        accion = stringResource(R.string.accion_sincronizar),
                        alAccionar = alSincronizar,
                    )
                }
                is ContenidoAlertas.Lista -> {
                    Text(
                        text = resumen(contenido, estado, zona),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    PullToRefreshBox(
                        isRefreshing = estado.sincronizando,
                        onRefresh = alSincronizar,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(contenido.alertas, key = { it.id }) { alerta ->
                                TarjetaAlerta(alerta = alerta, alTocar = alAbrirDetalle, zona = zona)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "6 alertas · 4 sin revisar · últimas 24 h · actualizado 02:14" */
@Composable
private fun resumen(lista: ContenidoAlertas.Lista, estado: AlertasUiState, zona: ZoneId): String {
    val partes = mutableListOf(
        pluralStringResource(R.plurals.alertas_cantidad, lista.alertas.size, lista.alertas.size),
        stringResource(R.string.alertas_sin_revisar, lista.sinRevisar),
    )
    estado.filtro.periodo.enResumen()?.let { partes += stringResource(it) }
    partes += estado.datosDe?.let { stringResource(R.string.alertas_actualizado, Formato.hora(it, zona)) }
        ?: stringResource(R.string.alertas_sin_sincronizar)
    return partes.joinToString(" · ")
}

private fun textoDelAviso(contexto: Context, aviso: AvisoAlertas): String = when (aviso) {
    is AvisoAlertas.Sincronizado -> when {
        aviso.nuevas == 0 -> contexto.getString(R.string.aviso_sincronizado_sin_novedades)
        aviso.altasNuevas > 0 -> contexto.getString(R.string.aviso_sincronizado_con_altas, aviso.nuevas, aviso.altasNuevas)
        else -> contexto.resources.getQuantityString(R.plurals.aviso_sincronizado_nuevas, aviso.nuevas, aviso.nuevas)
    }
    is AvisoAlertas.Error -> contexto.getString(aviso.error.mensaje())
}
