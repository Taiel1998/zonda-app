package ar.edu.uade.zonda.ui.alertas

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.FiltroServicio
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.OpcionesDeFiltro
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.components.BotonesDeHoja
import ar.edu.uade.zonda.ui.components.ChipOpcion
import ar.edu.uade.zonda.ui.resumen.ChipFiltro
import ar.edu.uade.zonda.ui.components.GrupoDeChips
import ar.edu.uade.zonda.ui.components.HojaDeCasillas
import ar.edu.uade.zonda.ui.components.HojaDeOpciones
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.components.Opcion
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.nombreEnFiltro
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloHoja
import ar.edu.uade.zonda.ui.tituloEnHoja

private enum class HojaFiltro { ESTADO, SEVERIDAD, PERIODO, MAS }

/**
 * Los chips de la lista (Figma 2-3): el de estado muestra el elegido y va siempre verde; Severidad
 * muestra las elegidas; el período, su valor, verde si no es el de siempre (30 días); y "Más filtros
 * (N)". Cada uno abre su hoja y el filtro se aplica con Aplicar: la lista cambia al instante porque
 * es otra consulta a la base.
 */
@Composable
fun FiltrosAlertas(
    filtro: FiltroAlertas,
    opciones: OpcionesDeFiltro,
    alCambiar: (FiltroAlertas) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hoja by remember { mutableStateOf<HojaFiltro?>(null) }
    val masFiltros = filtro.cantidadMasFiltros

    // 30 de alto aunque estén todos elegidos (los elegidos miden 28): la lista no salta al filtrar.
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Lo pone "Ver" del aviso de Estado con varias revisiones no aplicadas (cambio 7); la cruz lo saca.
        if (filtro.soloNoAplicadas) {
            val noAplicadas = stringResource(R.string.filtro_no_aplicadas)
            ChipFiltro(
                texto = noAplicadas,
                descripcionQuitar = stringResource(R.string.resumen_quitar_filtro, noAplicadas),
                alQuitar = { alCambiar(filtro.copy(soloNoAplicadas = false)) },
            )
        }
        ChipOpcion(
            texto = stringResource(filtro.estado.etiqueta()),
            elegido = true,
            alTocar = { hoja = HojaFiltro.ESTADO },
            rol = Role.Button,
        )
        ChipOpcion(
            texto = textoDeSeveridades(filtro.severidades),
            elegido = filtro.severidades.isNotEmpty(),
            alTocar = { hoja = HojaFiltro.SEVERIDAD },
            rol = Role.Button,
        )
        ChipOpcion(
            texto = stringResource(filtro.periodo.etiqueta()),
            elegido = filtro.periodo != FiltroAlertas.TODAS.periodo,
            alTocar = { hoja = HojaFiltro.PERIODO },
            rol = Role.Button,
        )
        ChipOpcion(
            texto = if (masFiltros == 0) stringResource(R.string.filtro_mas) else stringResource(R.string.filtro_mas_con_cantidad, masFiltros),
            elegido = masFiltros > 0,
            alTocar = { hoja = HojaFiltro.MAS },
            rol = Role.Button,
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
        HojaFiltro.SEVERIDAD -> HojaDeCasillas(
            titulo = stringResource(R.string.filtro_severidad_titulo),
            opciones = Severidad.entries.map { Opcion(it, stringResource(it.etiqueta())) },
            // Sin filtro están todas marcadas, como en el Figma; marcarlas todas (o ninguna) es no filtrar.
            marcadas = filtro.severidades.ifEmpty { Severidad.entries.toSet() },
            alCerrar = { hoja = null },
            alAplicar = { marcadas ->
                alCambiar(filtro.copy(severidades = if (marcadas.size == Severidad.entries.size) emptySet() else marcadas))
                hoja = null
            },
        )
        HojaFiltro.PERIODO -> HojaDeOpciones(
            titulo = stringResource(R.string.filtro_periodo_titulo),
            opciones = Periodo.DE_LA_LISTA.map { periodo ->
                Opcion(
                    valor = periodo,
                    titulo = stringResource(periodo.tituloEnHoja()),
                    detalle = if (periodo == Periodo.ULTIMOS_30_DIAS) stringResource(R.string.filtro_periodo_30d_detalle) else null,
                )
            },
            seleccionada = filtro.periodo,
            alCerrar = { hoja = null },
            alAplicar = { alCambiar(filtro.copy(periodo = it)); hoja = null },
        )
        HojaFiltro.MAS -> HojaMasFiltros(
            filtro = filtro,
            opciones = opciones,
            alCerrar = { hoja = null },
            alAplicar = { alCambiar(it); hoja = null },
        )
        null -> Unit
    }
}

/** "Severidad" sin filtro; si no, las elegidas: "Alta", "Alta, Media". */
@Composable
private fun textoDeSeveridades(severidades: Set<Severidad>): String =
    if (severidades.isEmpty()) {
        stringResource(R.string.filtro_severidad)
    } else {
        Severidad.entries.filter { it in severidades }.map { stringResource(it.etiqueta()) }.joinToString(", ")
    }

/**
 * Más filtros (Figma 51-2202): servicio, equipo destino y método que la detectó, uno por grupo; tocar
 * el elegido lo quita. Los servicios y los equipos salen de las alertas guardadas, que los informa el
 * servidor; los servicios que no tienen chip propio entran en "Otros".
 */
@Composable
private fun HojaMasFiltros(
    filtro: FiltroAlertas,
    opciones: OpcionesDeFiltro,
    alCerrar: () -> Unit,
    alAplicar: (FiltroAlertas) -> Unit,
) {
    HojaInferior(alCerrar = alCerrar) {
        ContenidoMasFiltros(filtro, opciones, alAplicar)
    }
}

/** El contenido de Más filtros, aparte para dibujarlo en las pruebas sin la ventana de la hoja. */
@Composable
fun ContenidoMasFiltros(
    filtro: FiltroAlertas,
    opciones: OpcionesDeFiltro,
    alAplicar: (FiltroAlertas) -> Unit,
) {
    var servicio by remember(filtro) { mutableStateOf(filtro.servicio) }
    var destino by remember(filtro) { mutableStateOf(filtro.destinoIp) }
    var metodo by remember(filtro) { mutableStateOf(filtro.metodo) }
    val otros = FiltroServicio.Otros(opciones.serviciosConChip.toSet())

    Text(text = stringResource(R.string.filtro_mas_titulo), style = MaterialTheme.typography.tituloHoja)
    if (opciones.servicios.isNotEmpty()) {
        GrupoDeChips(titulo = stringResource(R.string.filtro_servicio)) {
            opciones.serviciosConChip.forEach { nombre ->
                val este = FiltroServicio.Uno(nombre)
                ChipOpcion(nombre.uppercase(), elegido = servicio == este, alTocar = { servicio = if (servicio == este) null else este })
            }
            if (opciones.hayOtrosServicios) {
                ChipOpcion(
                    texto = stringResource(R.string.filtro_otros),
                    elegido = servicio is FiltroServicio.Otros,
                    alTocar = { servicio = if (servicio is FiltroServicio.Otros) null else otros },
                )
            }
        }
    }
    if (opciones.destinos.isNotEmpty()) {
        GrupoDeChips(titulo = stringResource(R.string.filtro_destino)) {
            opciones.destinos.forEach { equipo ->
                ChipOpcion(equipo.etiqueta, elegido = destino == equipo.ip, alTocar = { destino = if (destino == equipo.ip) null else equipo.ip })
            }
        }
    }
    GrupoDeChips(titulo = stringResource(R.string.filtro_metodo)) {
        MetodoDeteccion.entries.forEach { valor ->
            ChipOpcion(stringResource(valor.nombreEnFiltro()), elegido = metodo == valor, alTocar = { metodo = if (metodo == valor) null else valor })
        }
    }
    Text(
        text = stringResource(R.string.filtro_mas_nota),
        style = MaterialTheme.typography.nota,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BotonesDeHoja(
        // "Otros" se arma con los chips de ahora: si llegaron servicios nuevos, quedan bien repartidos.
        alAplicar = { alAplicar(filtro.copy(servicio = if (servicio is FiltroServicio.Otros) otros else servicio, destinoIp = destino, metodo = metodo)) },
        alLimpiar = {
            servicio = null
            destino = null
            metodo = null
        },
    )
}
