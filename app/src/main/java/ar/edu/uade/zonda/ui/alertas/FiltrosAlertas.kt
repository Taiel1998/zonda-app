package ar.edu.uade.zonda.ui.alertas

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.FiltroServicio
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.components.HojaDeOpciones
import ar.edu.uade.zonda.ui.components.Opcion
import ar.edu.uade.zonda.ui.etiqueta

private enum class HojaFiltro { ESTADO, SEVERIDAD, PERIODO, MAS }

/**
 * Los chips de filtro de la pantalla Alertas (RF02; Figma, figura 11): Estado, Severidad y Período
 * a la vista, y Más filtros con servicio, equipo destino y método. Cada chip abre una hoja inferior;
 * el filtro se aplica con Aplicar y la lista cambia al instante, porque es otra consulta a la base.
 */
@Composable
fun FiltrosAlertas(
    filtro: FiltroAlertas,
    catalogo: Catalogo,
    alCambiar: (FiltroAlertas) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hoja by remember { mutableStateOf<HojaFiltro?>(null) }
    val masActivos = filtro.cantidadMasFiltros

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipFiltro(
            texto = stringResource(filtro.estado.etiquetaDeFiltro()),
            seleccionado = true,
            alTocar = { hoja = HojaFiltro.ESTADO },
        )
        ChipFiltro(
            texto = filtro.severidades.singleOrNull()?.let { stringResource(it.etiqueta()) } ?: stringResource(R.string.filtro_severidad),
            seleccionado = filtro.severidades.isNotEmpty(),
            alTocar = { hoja = HojaFiltro.SEVERIDAD },
        )
        ChipFiltro(
            texto = stringResource(filtro.periodo.etiqueta()),
            seleccionado = filtro.periodo != FiltroAlertas.TODAS.periodo,
            alTocar = { hoja = HojaFiltro.PERIODO },
        )
        ChipFiltro(
            texto = if (masActivos == 0) stringResource(R.string.filtro_mas) else stringResource(R.string.filtro_mas_con_cantidad, masActivos),
            seleccionado = masActivos > 0,
            alTocar = { hoja = HojaFiltro.MAS },
        )
    }

    when (hoja) {
        HojaFiltro.ESTADO -> HojaDeOpciones(
            titulo = stringResource(R.string.filtro_estado_titulo),
            opciones = listOf(
                Opcion(FiltroEstado.SIN_REVISAR, stringResource(R.string.filtro_estado_sin_revisar), stringResource(R.string.filtro_estado_sin_revisar_detalle)),
                Opcion(FiltroEstado.REVISADAS, stringResource(R.string.filtro_estado_revisadas)),
                Opcion(FiltroEstado.FALSOS_POSITIVOS, stringResource(R.string.filtro_estado_falsos_positivos)),
                Opcion(FiltroEstado.TODAS, stringResource(R.string.filtro_estado_todas), stringResource(R.string.filtro_estado_todas_detalle)),
            ),
            seleccionada = filtro.estado,
            alCerrar = { hoja = null },
            alAplicar = { alCambiar(filtro.copy(estado = it)); hoja = null },
        )
        HojaFiltro.SEVERIDAD -> HojaDeOpciones(
            titulo = stringResource(R.string.filtro_severidad_titulo),
            opciones = Severidad.entries.map { Opcion<Severidad?>(it, stringResource(it.etiqueta())) } +
                Opcion(null, stringResource(R.string.filtro_severidad_todas)),
            seleccionada = filtro.severidades.singleOrNull(),
            alCerrar = { hoja = null },
            alAplicar = { alCambiar(filtro.copy(severidades = setOfNotNull(it))); hoja = null },
        )
        HojaFiltro.PERIODO -> HojaDeOpciones(
            titulo = stringResource(R.string.filtro_periodo_titulo),
            opciones = listOf(
                Opcion(Periodo.ULTIMAS_24_HORAS, stringResource(R.string.filtro_periodo_24h), stringResource(R.string.filtro_periodo_24h_detalle)),
                Opcion(Periodo.ULTIMOS_7_DIAS, stringResource(R.string.filtro_periodo_7d)),
                Opcion(Periodo.ULTIMOS_30_DIAS, stringResource(R.string.filtro_periodo_30d)),
            ),
            seleccionada = filtro.periodo,
            alCerrar = { hoja = null },
            alAplicar = { alCambiar(filtro.copy(periodo = it)); hoja = null },
        )
        HojaFiltro.MAS -> HojaMasFiltros(
            filtro = filtro,
            catalogo = catalogo,
            alCerrar = { hoja = null },
            alAplicar = { alCambiar(it); hoja = null },
        )
        null -> Unit
    }
}

/** El texto del chip de Estado muestra el valor elegido ("Revisadas"), no el nombre del filtro. */
private fun FiltroEstado.etiquetaDeFiltro(): Int = when (this) {
    FiltroEstado.SIN_REVISAR -> R.string.filtro_estado_sin_revisar
    FiltroEstado.REVISADAS -> R.string.filtro_estado_revisadas
    FiltroEstado.FALSOS_POSITIVOS -> R.string.filtro_estado_falsos_positivos
    FiltroEstado.TODAS -> R.string.filtro_estado_todas
}

@Composable
private fun ChipFiltro(texto: String, seleccionado: Boolean, alTocar: () -> Unit) {
    FilterChip(selected = seleccionado, onClick = alTocar, label = { Text(texto) })
}

/**
 * Más filtros: servicio, equipo destino y método, cada uno con sus chips y "Todos". Los servicios
 * y los nombres de equipo salen del catálogo que entrega la API: no hay nada que configurar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HojaMasFiltros(
    filtro: FiltroAlertas,
    catalogo: Catalogo,
    alCerrar: () -> Unit,
    alAplicar: (FiltroAlertas) -> Unit,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var servicio by remember(filtro) { mutableStateOf((filtro.servicio as? FiltroServicio.Uno)?.nombre) }
    var destino by remember(filtro) { mutableStateOf(filtro.destinoIp) }
    var metodo by remember(filtro) { mutableStateOf(filtro.metodo) }

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estadoHoja) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(text = stringResource(R.string.filtro_mas_titulo), style = MaterialTheme.typography.titleLarge)

            GrupoDeChips(titulo = stringResource(R.string.filtro_servicio)) {
                ChipDeGrupo(stringResource(R.string.filtro_todos), servicio == null) { servicio = null }
                catalogo.servicios.forEach { nombre ->
                    ChipDeGrupo(nombre.uppercase(), servicio == nombre) { servicio = nombre }
                }
            }
            GrupoDeChips(titulo = stringResource(R.string.filtro_destino)) {
                ChipDeGrupo(stringResource(R.string.filtro_todos), destino == null) { destino = null }
                catalogo.equipos.entries.sortedBy { it.value }.forEach { (ip, nombre) ->
                    ChipDeGrupo(nombre, destino == ip) { destino = ip }
                }
            }
            GrupoDeChips(titulo = stringResource(R.string.filtro_metodo)) {
                ChipDeGrupo(stringResource(R.string.filtro_todos), metodo == null) { metodo = null }
                MetodoDeteccion.entries.forEach { valor ->
                    ChipDeGrupo(stringResource(valor.etiqueta()), metodo == valor) { metodo = valor }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { servicio = null; destino = null; metodo = null }) {
                    Text(stringResource(R.string.filtro_limpiar))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { alAplicar(filtro.copy(servicio = servicio?.let(FiltroServicio::Uno), destinoIp = destino, metodo = metodo)) }) {
                    Text(stringResource(R.string.filtro_aplicar))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrupoDeChips(titulo: String, contenido: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
}

@Composable
private fun ChipDeGrupo(texto: String, seleccionado: Boolean, alTocar: () -> Unit) {
    FilterChip(selected = seleccionado, onClick = alTocar, label = { Text(texto) })
}
