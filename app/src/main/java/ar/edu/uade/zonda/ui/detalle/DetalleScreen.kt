package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BannerSinConexion
import ar.edu.uade.zonda.ui.components.EstadoError
import ar.edu.uade.zonda.ui.components.EstadoVacio
import ar.edu.uade.zonda.ui.components.IndicadorEnvio
import ar.edu.uade.zonda.ui.components.IndicadorEstadoRevision
import ar.edu.uade.zonda.ui.components.IndicadorSeveridad
import ar.edu.uade.zonda.ui.components.IndicadorVeredicto
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.nombreEnFrase
import ar.edu.uade.zonda.ui.theme.EstadoRevisada
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Pantalla Detalle (RF03): los datos del flujo, el veredicto de cada método con el dato que lo
 * justifica y, si está revisada, la revisión con su historial. Se abre desde Estado o Alertas y
 * desde el enlace del aviso de Telegram. La acción Revisar (RF04) se completa en la fase 6.
 */
@Composable
fun DetalleScreen(
    alertaId: String,
    alVolver: () -> Unit,
    viewModel: DetalleViewModel = viewModelDeZonda {
        DetalleViewModel(it.obtenerDetalleAlerta, it.redes, it.conectividad, alertaId)
    },
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val textoPronto = stringResource(R.string.detalle_revision_pronto)

    DetalleContenido(
        estado = estado,
        alVolver = alVolver,
        alReintentar = viewModel::reintentar,
        alRevisar = { alcance.launch { snackbar.showSnackbar(textoPronto) } },
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleContenido(
    estado: DetalleUiState,
    alVolver: () -> Unit,
    alReintentar: () -> Unit,
    alRevisar: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detalle_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
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
            when (estado) {
                DetalleUiState.Cargando -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.detalle_cargando), textAlign = TextAlign.Center)
                }
                DetalleUiState.SinRed -> EstadoVacio(
                    titulo = stringResource(R.string.vacio_sin_red_titulo),
                    detalle = stringResource(R.string.vacio_sin_red_detalle),
                    icono = Icons.Filled.Info,
                )
                is DetalleUiState.NoDisponible -> {
                    if (estado.sinConexion) BannerSinConexion(datosDe = null, zona = zona)
                    EstadoError(
                        titulo = stringResource(R.string.detalle_no_disponible_titulo),
                        detalle = if (estado.sinConexion) {
                            stringResource(R.string.detalle_no_disponible_sin_conexion)
                        } else {
                            stringResource(estado.error?.mensaje() ?: R.string.error_generico)
                        },
                        alReintentar = alReintentar,
                    )
                }
                is DetalleUiState.Contenido -> {
                    if (estado.sinConexion) BannerSinConexion(datosDe = estado.datosDe, zona = zona)
                    CuerpoDetalle(alerta = estado.alerta, alRevisar = alRevisar, zona = zona)
                }
            }
        }
    }
}

@Composable
private fun CuerpoDetalle(alerta: Alerta, alRevisar: () -> Unit, zona: ZoneId) {
    val metodos = alerta.metodos.sorted().map { stringResource(it.nombreEnFrase()) }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IndicadorSeveridad(alerta.severidad)
            Spacer(modifier = Modifier.width(6.dp))
            IndicadorEstadoRevision(alerta.estadoRevision)
            alerta.envio?.let { envio ->
                Spacer(modifier = Modifier.width(10.dp))
                IndicadorEnvio(envio)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "${alerta.origen.etiqueta} → ${alerta.destino.etiqueta}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = listOf(
                Formato.servicioYPuerto(alerta.servicio, alerta.puerto),
                Formato.horaConSegundos(alerta.inicio, zona),
                stringResource(R.string.detalle_detectada_por, enumerar(metodos)),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Seccion(titulo = stringResource(R.string.detalle_datos_del_flujo)) {
            FilaDato(stringResource(R.string.detalle_origen), Formato.ipYNombre(alerta.origen))
            FilaDato(stringResource(R.string.detalle_destino), Formato.ipYNombre(alerta.destino))
            FilaDato(stringResource(R.string.detalle_servicio), Formato.servicioYPuerto(alerta.servicio, alerta.puerto))
            FilaDato(stringResource(R.string.detalle_inicio), Formato.horaConSegundos(alerta.inicio, zona))
            FilaDato(stringResource(R.string.detalle_duracion), Formato.duracion(alerta.duracion))
            FilaDato(stringResource(R.string.detalle_conexiones_60s), alerta.veredictos.umbrales.contexto60s.toString())
            FilaDato(stringResource(R.string.detalle_flujos), alerta.cantidadFlujos.toString())
            if (alerta.puertosDistintos > 1) {
                FilaDato(stringResource(R.string.detalle_puertos_distintos), alerta.puertosDistintos.toString())
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Seccion(titulo = stringResource(R.string.detalle_veredictos)) {
            val ia = alerta.veredictos.ia
            FilaVeredicto(
                titulo = stringResource(R.string.detalle_modelo_ia),
                detalle = stringResource(R.string.detalle_ia_detalle, Formato.numero(ia.errorReconstruccion), Formato.numero(ia.umbral)),
                anomalo = ia.anomalo,
            )
            val umbrales = alerta.veredictos.umbrales
            FilaVeredicto(
                titulo = stringResource(R.string.detalle_umbrales),
                detalle = listOfNotNull(
                    stringResource(R.string.detalle_umbrales_detalle, umbrales.contexto60s, umbrales.umbralServicio),
                    if (umbrales.servicioSinLineaBase) stringResource(R.string.detalle_umbrales_sin_linea_base) else null,
                ).joinToString(" · "),
                anomalo = umbrales.anomalo,
            )
            val reglas = alerta.veredictos.reglas
            FilaVeredicto(
                titulo = stringResource(R.string.detalle_reglas),
                detalle = if (reglas.enCatalogo) {
                    stringResource(R.string.detalle_reglas_en_catalogo)
                } else {
                    stringResource(R.string.detalle_reglas_fuera_de_catalogo, reglas.parDestino)
                },
                anomalo = reglas.anomalo,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val revision = alerta.revisionActual
        if (revision != null && !alerta.sinRevisar) {
            Seccion(titulo = stringResource(R.string.detalle_revision)) {
                FilaDato(
                    etiqueta = stringResource(R.string.detalle_resultado),
                    valor = stringResource(revision.resultado.etiqueta()),
                    colorValor = if (revision.resultado == ResultadoRevision.ATAQUE_REAL) MaterialTheme.colorScheme.error else EstadoRevisada,
                )
                FilaDato(stringResource(R.string.detalle_motivo), stringResource(revision.motivo.etiqueta()))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "“${revision.comentario}”", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = autorYFecha(revision, zona), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alerta.historial.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.detalle_historial), style = MaterialTheme.typography.titleSmall)
                    alerta.historial.dropLast(1).asReversed().forEach { anterior ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${stringResource(anterior.resultado.etiqueta())} · ${stringResource(anterior.motivo.etiqueta())} · ${autorYFecha(anterior, zona)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = alRevisar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.detalle_cambiar_revision))
            }
        } else {
            Text(
                text = stringResource(R.string.detalle_revisar_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = alRevisar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.detalle_revisar))
            }
        }
    }
}

/** "Celular de Martín · 27/09 02:12 · enviada" */
@Composable
private fun autorYFecha(revision: Revision, zona: ZoneId): String = listOf(
    revision.autor ?: stringResource(R.string.detalle_este_telefono),
    Formato.fechaYHora(revision.fecha, zona),
    if (revision.origen == OrigenRevision.SERVIDOR) stringResource(R.string.detalle_enviada) else stringResource(R.string.detalle_pendiente_de_envio),
).joinToString(" · ")

/** "IA", "IA y umbrales", "IA, umbrales y reglas". */
private fun enumerar(partes: List<String>): String = when (partes.size) {
    0 -> ""
    1 -> partes.single()
    else -> partes.dropLast(1).joinToString(", ") + " y " + partes.last()
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            contenido()
        }
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String, colorValor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            color = colorValor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilaVeredicto(titulo: String, detalle: String, anomalo: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
            Text(text = detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        IndicadorVeredicto(anomalo = anomalo)
    }
}
