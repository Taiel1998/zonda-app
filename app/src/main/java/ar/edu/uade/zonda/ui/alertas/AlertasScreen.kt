package ar.edu.uade.zonda.ui.alertas

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.FiltroResponsable
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.resumen.textosDelChip
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BannerAccesoVencido
import ar.edu.uade.zonda.ui.components.BannerSinConexion
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.EsqueletoLista
import ar.edu.uade.zonda.ui.components.EstadoError
import ar.edu.uade.zonda.ui.components.EstadoVacio
import ar.edu.uade.zonda.ui.components.TarjetaAlerta
import ar.edu.uade.zonda.ui.enResumen
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.navigation.AlRetocarPestania
import ar.edu.uade.zonda.ui.navigation.Rutas
import ar.edu.uade.zonda.ui.nombreEnFiltro
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.objetivoDelRecorrido
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.ZoneId

/**
 * Pantalla Alertas (RF02; Figma 2-3): la lista de la red activa con los chips de filtro, la línea de
 * resumen, el banner de sin conexión y la sincronización con el ícono o deslizando hacia abajo.
 */
@Composable
fun AlertasScreen(
    alAbrirDetalle: (alertaId: String) -> Unit,
    alConectarDeNuevo: () -> Unit,
    filtroPedido: FiltroAlertas? = null,
    viewModel: AlertasViewModel = alertasViewModel(),
) {
    // La notificación abre la lista ya filtrada en lo que avisó (Alta · Sin revisar).
    LaunchedEffect(filtroPedido) { filtroPedido?.let(viewModel::aplicarFiltro) }
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AlertasContenido(
        estado = estado,
        alAbrirDetalle = alAbrirDetalle,
        alCambiarFiltro = viewModel::aplicarFiltro,
        alSincronizar = { viewModel.sincronizar() },
        alQuitarFiltros = viewModel::quitarFiltros,
        alAvisoMostrado = viewModel::avisoMostrado,
        alConectarDeNuevo = alConectarDeNuevo,
    )
}

/** El ViewModel de Alertas de la entrada de la pila; la navegación lo usa para el "Ver todas" de Estado. */
@Composable
fun alertasViewModel(): AlertasViewModel = viewModelDeZonda {
    AlertasViewModel(it.observarAlertas, it.sincronizarAlertas, it.redes, it.alertas, it.conectividad)
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
    alConectarDeNuevo: () -> Unit = {},
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        snackbar.showSnackbar(textoDelAviso(contexto, aviso, zona))
        alAvisoMostrado()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BarraSuperior(
                titulo = stringResource(R.string.destino_alertas),
                acciones = {
                    if (estado.sincronizando) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        // Figma 33-52: el ícono de sincronizar de 24, gris.
                        IconButton(onClick = alSincronizar, enabled = estado.contenido !is ContenidoAlertas.SinRed) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = stringResource(R.string.accion_sincronizar),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        // Figma 2-3: 16 de margen y 16 entre los chips, el resumen y cada tarjeta.
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            if (estado.accesoVencido) BannerAccesoVencido(alConectar = alConectarDeNuevo)
            if (estado.sinConexion) BannerSinConexion(datosDe = estado.datosDe, zona = zona)
            // El recorrido resalta la fila de chips sin los 16 de cada costado, como el paso 6 del Figma.
            Box(modifier = Modifier.padding(top = 16.dp)) {
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 16.dp)
                        .objetivoDelRecorrido(ObjetivoRecorrido.CHIPS),
                )
                FiltrosAlertas(
                    filtro = estado.filtro,
                    opciones = estado.opciones,
                    personas = estado.contexto.personas,
                    miId = estado.contexto.miId,
                    iniciosParaFechas = estado.iniciosSinFechas,
                    alCambiar = alCambiarFiltro,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val debajoDeLosChips = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            when (val contenido = estado.contenido) {
                ContenidoAlertas.Cargando -> EsqueletoLista(modifier = debajoDeLosChips)
                ContenidoAlertas.SinRed -> EstadoVacio(
                    titulo = stringResource(R.string.vacio_sin_red_titulo),
                    detalle = stringResource(R.string.vacio_sin_red_detalle),
                    modifier = debajoDeLosChips,
                )
                ContenidoAlertas.Error -> EstadoError(
                    titulo = stringResource(R.string.error_carga_titulo),
                    detalle = estado.datosDe?.let { stringResource(R.string.error_carga_detalle_con_datos, Formato.hora(it, zona)) }
                        ?: stringResource(R.string.error_carga_detalle),
                    alReintentar = alSincronizar,
                    modifier = debajoDeLosChips,
                )
                is ContenidoAlertas.Vacio -> if (contenido.conFiltro) {
                    EstadoVacio(
                        titulo = stringResource(R.string.vacio_con_filtro_titulo),
                        detalle = stringResource(R.string.vacio_con_filtro_detalle),
                        accion = stringResource(R.string.filtro_limpiar_filtros),
                        alAccionar = alQuitarFiltros,
                        modifier = debajoDeLosChips,
                    )
                } else {
                    EstadoVacio(
                        titulo = stringResource(R.string.vacio_sin_alertas_titulo),
                        detalle = stringResource(R.string.vacio_sin_alertas_detalle),
                        accion = stringResource(R.string.accion_sincronizar),
                        alAccionar = alSincronizar,
                        modifier = debajoDeLosChips,
                    )
                }
                is ContenidoAlertas.Lista -> {
                    Text(
                        text = resumen(contenido, estado, zona),
                        style = MaterialTheme.typography.nota,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = debajoDeLosChips,
                    )
                    PullToRefreshBox(
                        isRefreshing = estado.sincronizando,
                        onRefresh = alSincronizar,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val lista = rememberLazyListState()
                        AlRetocarPestania(Rutas.ALERTAS) { lista.animateScrollToItem(0) }
                        LazyColumn(
                            state = lista,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(contenido.alertas, key = { _, alerta -> alerta.id }) { indice, alerta ->
                                TarjetaAlerta(
                                    alerta = alerta,
                                    alTocar = alAbrirDetalle,
                                    zona = zona,
                                    contexto = estado.contexto,
                                    modifier = if (indice == 0) Modifier.objetivoDelRecorrido(ObjetivoRecorrido.PRIMERA_TARJETA) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * La línea de resumen del Figma: "6 alertas · 4 sin revisar · últimos 30 días · actualizado 02:14".
 * Con filtros dice qué se filtró y no la hora ("3 alertas altas sin revisar · últimas 24 h", "2
 * revisadas · últimas 24 h", "3 alertas · 2 sin revisar · SSH · últimas 24 h"). Sin conexión, la
 * hora es "datos de las 02:14".
 */
@Composable
private fun resumen(lista: ContenidoAlertas.Lista, estado: AlertasUiState, zona: ZoneId): String {
    val filtro = estado.filtro
    val total = lista.alertas.size
    val severidades = Severidad.entries.filter { it in filtro.severidades }
    val partes = mutableListOf<String>()
    when (filtro.estado) {
        FiltroEstado.SIN_REVISAR -> partes += if (severidades.isEmpty()) {
            pluralStringResource(R.plurals.resumen_sin_revisar, total, total)
        } else {
            pluralStringResource(R.plurals.resumen_sin_revisar_de_severidad, total, total, adjetivos(severidades, total))
        }
        FiltroEstado.REVISADAS -> partes += pluralStringResource(R.plurals.resumen_revisadas, total, total)
        FiltroEstado.FALSOS_POSITIVOS -> partes += pluralStringResource(R.plurals.resumen_falsos_positivos, total, total)
        FiltroEstado.TODAS -> {
            partes += pluralStringResource(R.plurals.alertas_cantidad, total, total)
            // Con el responsable, la línea dice de quién son (Figma 468-1621: "2 alertas · asignadas a mí").
            if (filtro.responsable == null) partes += stringResource(R.string.alertas_sin_revisar, lista.sinRevisar)
        }
    }
    when (val responsable = filtro.responsable) {
        is FiltroResponsable.AsignadasAMi -> partes += stringResource(R.string.resumen_asignadas_a_mi)
        FiltroResponsable.SinAsignar -> partes += stringResource(R.string.resumen_sin_asignar)
        is FiltroResponsable.De -> partes += stringResource(
            R.string.resumen_asignadas_a,
            estado.contexto.personas.firstOrNull { it.id == responsable.personaId }?.nombreVisible.orEmpty(),
        )
        null -> Unit
    }
    if (severidades.isNotEmpty() && filtro.estado != FiltroEstado.SIN_REVISAR) partes += adjetivos(severidades, cantidad = 2)
    // Servicios, equipos y métodos van de a varios (decisión 47): "SSH, FTP · srv-sueldos".
    val servicios = filtro.servicios.map(Formato::servicio) +
        listOfNotNull(filtro.otrosServicios?.let { stringResource(R.string.resumen_otros_servicios) })
    if (servicios.isNotEmpty()) partes += servicios.joinToString(", ")
    if (filtro.destinos.isNotEmpty()) {
        partes += filtro.destinos.joinToString(", ") { ip -> estado.opciones.destinos.firstOrNull { it.ip == ip }?.etiqueta ?: ip }
    }
    if (filtro.metodos.isNotEmpty()) partes += filtro.metodos.sorted().map { stringResource(it.nombreEnFiltro()) }.joinToString(", ")
    val fechas = filtro.fechas
    partes += if (fechas != null) {
        fechas.textosDelChip(LocalContext.current.resources).first().texto
    } else {
        stringResource(filtro.periodo.enResumen() ?: R.string.periodo_ultimos_30d)
    }
    // La hora de los datos va solo sin filtros, como en el Figma: con filtros la línea ya es larga.
    if (!filtro.activo) {
        partes += when {
            estado.datosDe == null -> stringResource(R.string.alertas_sin_sincronizar)
            estado.sinConexion -> stringResource(R.string.alertas_datos_de, Formato.hora(estado.datosDe, zona))
            else -> stringResource(R.string.alertas_actualizado, Formato.hora(estado.datosDe, zona))
        }
    }
    return partes.joinToString(" · ")
}

/** "altas", "altas y medias": las severidades como adjetivo, en singular o plural según [cantidad]. */
@Composable
private fun adjetivos(severidades: List<Severidad>, cantidad: Int): String {
    val palabras = severidades.map { severidad ->
        when (severidad) {
            Severidad.ALTA -> pluralStringResource(R.plurals.adjetivo_alta, cantidad)
            Severidad.MEDIA -> pluralStringResource(R.plurals.adjetivo_media, cantidad)
            Severidad.BAJA -> pluralStringResource(R.plurals.adjetivo_baja, cantidad)
        }
    }
    return if (palabras.size == 1) palabras.single() else palabras.dropLast(1).joinToString(", ") + " y " + palabras.last()
}

private fun textoDelAviso(contexto: Context, aviso: AvisoAlertas, zona: ZoneId): String = when (aviso) {
    is AvisoAlertas.Sincronizado -> when {
        // Figma: "Sincronizado · dom 27/09 · 02:15" al tocar sincronizar (RF01, criterio 5).
        aviso.manual -> contexto.getString(R.string.aviso_sincronizado_hora, Formato.dia(aviso.hora, zona), Formato.hora(aviso.hora, zona))
        aviso.altasNuevas > 0 -> contexto.resources.getQuantityString(R.plurals.aviso_sincronizado_con_altas, aviso.nuevas, aviso.nuevas, aviso.altasNuevas)
        else -> contexto.resources.getQuantityString(R.plurals.aviso_sincronizado_nuevas, aviso.nuevas, aviso.nuevas)
    }
    is AvisoAlertas.Error -> contexto.getString(aviso.error.mensaje())
}
