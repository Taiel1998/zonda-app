package ar.edu.uade.zonda.ui.estado

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BannerAccesoVencido
import ar.edu.uade.zonda.ui.components.BannerSinConexion
import ar.edu.uade.zonda.ui.components.BarraEstado
import ar.edu.uade.zonda.ui.components.EstadoVacio
import ar.edu.uade.zonda.ui.components.FilaContadores
import ar.edu.uade.zonda.ui.components.LeyendaSemaforo
import ar.edu.uade.zonda.ui.components.MarcaSemaforo
import ar.edu.uade.zonda.ui.components.Semaforo
import ar.edu.uade.zonda.ui.components.TarjetaAlerta
import ar.edu.uade.zonda.ui.components.TarjetaDescarga
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.navigation.AlRetocarPestania
import ar.edu.uade.zonda.ui.navigation.Rutas
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.objetivoDelRecorrido
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import ar.edu.uade.zonda.ui.theme.encabezado
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla Estado (RF01; Figma 1-3): el semáforo de la red activa, los contadores de alertas sin revisar
 * por severidad, que filtran la lista de abajo, y la hora de los datos. Es la primera pantalla al abrir
 * la app con una red pareada. Arriba están el Resumen y sincronizar.
 */
@Composable
fun EstadoScreen(
    alAbrirResumen: () -> Unit,
    alAbrirDetalle: (alertaId: String) -> Unit,
    alVerTodas: () -> Unit,
    alConectar: () -> Unit,
    alVerNoAplicadas: () -> Unit = {},
    alVerAsignadas: () -> Unit = {},
    viewModel: EstadoViewModel = viewModelDeZonda {
        EstadoViewModel(it.obtenerEstadoRed, it.observarAlertas, it.sincronizarAlertas, it.redes, it.alertas, it.conectividad, it.accionesPendientes)
    },
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ahora by relojDeLaPantalla()
    EstadoContenido(
        estado = estado,
        acciones = AccionesEstado(
            alAbrirResumen = alAbrirResumen,
            alSincronizar = viewModel::sincronizar,
            alTocarContador = viewModel::tocarContador,
            alAbrirDetalle = alAbrirDetalle,
            alVerTodas = alVerTodas,
            alConectar = alConectar,
            alAvisoMostrado = viewModel::avisoMostrado,
            alVerNoAplicadas = alVerNoAplicadas,
            alVerAsignadas = alVerAsignadas,
        ),
        ahora = ahora,
    )
}

/** Lo que se puede hacer en Estado. Cada acción tiene un valor por defecto para las pruebas. */
data class AccionesEstado(
    val alAbrirResumen: () -> Unit = {},
    val alSincronizar: () -> Unit = {},
    val alTocarContador: (Severidad) -> Unit = {},
    val alAbrirDetalle: (alertaId: String) -> Unit = {},
    /** "Ver todas →": la pestaña Alertas con todas las alertas (Figma: de 1-3 a 2-3). */
    val alVerTodas: () -> Unit = {},
    /** Configurar conexión (sin red) o conectar de nuevo (acceso vencido): la pantalla Conexión. */
    val alConectar: () -> Unit = {},
    val alAvisoMostrado: () -> Unit = {},
    /** "Ver" del aviso con varias revisiones no aplicadas: Alertas con esas (cambio 7). Con una, se abre su detalle. */
    val alVerNoAplicadas: () -> Unit = {},
    /** "Ver" del aviso de alertas asignadas: Alertas con "Asignadas a mí" (decisión 42; Figma 468-2018). */
    val alVerAsignadas: () -> Unit = {},
)

/**
 * La pantalla sin ViewModel: recibe el estado y devuelve las acciones, así se prueba con Compose sobre
 * Robolectric. [ahora] es la hora con la que se calcula "hace 5 min".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstadoContenido(
    estado: EstadoUiState,
    acciones: AccionesEstado = AccionesEstado(),
    ahora: Instant = Instant.now(),
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var sincronizado by remember { mutableStateOf<AvisoEstado.Sincronizado?>(null) }

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        // El aviso se consume enseguida; el inferior se muestra en otro alcance para que consumirlo no lo corte.
        acciones.alAvisoMostrado()
        when (aviso) {
            is AvisoEstado.Sincronizado -> sincronizado = aviso
            is AvisoEstado.Error -> alcance.launch {
                val resultado = snackbar.showSnackbar(
                    message = contexto.getString(aviso.error.mensaje()),
                    actionLabel = contexto.getString(R.string.error_reintentar),
                    duration = SnackbarDuration.Short,
                )
                if (resultado == SnackbarResult.ActionPerformed) acciones.alSincronizar()
            }
        }
    }

    val contenido = estado.contenido
    // Sin datos y sin red, la barra va sin el ícono de sincronizar, como el cuadro 1-222: el botón de abajo
    // es el que sincroniza o lleva a Conexión.
    val conSincronizar = contenido !is ContenidoEstado.SinDatos && contenido !is ContenidoEstado.SinRed
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BarraEstado(
                alAbrirResumen = acciones.alAbrirResumen,
                alSincronizar = if (conSincronizar) acciones.alSincronizar else null,
                sincronizando = estado.sincronizando,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            if (estado.accesoVencido) BannerAccesoVencido(alConectar = acciones.alConectar)
            if (estado.sinConexion) BannerSinConexion(datosDe = estado.datosDe, zona = zona)
            val lista = rememberLazyListState()
            AlRetocarPestania(Rutas.ESTADO) { lista.animateScrollToItem(0) }
            if (contenido is ContenidoEstado.Cargando || contenido is ContenidoEstado.SinRed) {
                CuerpoEstado(estado, acciones, ahora, zona, lista)
            } else {
                // Deslizar hacia abajo también sincroniza (informe, 14.2). El círculo de deslizar se ve solo
                // si la persona deslizó: con el ícono de la barra gira el ícono, sin tapar el semáforo.
                var porDeslizar by remember { mutableStateOf(false) }
                LaunchedEffect(estado.sincronizando) { if (!estado.sincronizando) porDeslizar = false }
                PullToRefreshBox(
                    isRefreshing = porDeslizar && estado.sincronizando,
                    onRefresh = {
                        porDeslizar = true
                        acciones.alSincronizar()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CuerpoEstado(estado, acciones, ahora, zona, lista)
                }
            }
        }
    }

    sincronizado?.let { aviso ->
        AvisoSincronizado(aviso = aviso, zona = zona, alCerrar = { sincronizado = null })
    }
}

/** Figma 1-3: 16 de margen, 16 entre cada pieza y 24 al final de la lista. */
@Composable
private fun CuerpoEstado(estado: EstadoUiState, acciones: AccionesEstado, ahora: Instant, zona: ZoneId, lista: LazyListState) {
    LazyColumn(
        state = lista,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val contenido = estado.contenido) {
            ContenidoEstado.Cargando -> item { EsqueletoEstado() }
            is ContenidoEstado.Descargando -> {
                item { TarjetaDescarga(descargadas = contenido.descargadas, total = contenido.total) }
                item { EsqueletoEstado() }
            }
            ContenidoEstado.SinRed -> {
                item { SemaforoSinDatos() }
                item {
                    EstadoVacio(
                        titulo = stringResource(R.string.estado_conecta_titulo),
                        detalle = stringResource(R.string.estado_conecta_detalle),
                        accion = stringResource(R.string.estado_conecta_accion),
                        alAccionar = acciones.alConectar,
                    )
                }
            }
            ContenidoEstado.SinDatos -> {
                item { SemaforoSinDatos() }
                item {
                    EstadoVacio(
                        titulo = stringResource(R.string.estado_conecta_titulo),
                        detalle = stringResource(R.string.estado_sin_datos_detalle),
                        accion = stringResource(R.string.accion_sincronizar),
                        alAccionar = acciones.alSincronizar,
                    )
                }
            }
            is ContenidoEstado.Datos -> datosDeLaRed(contenido, estado, acciones, ahora, zona)
        }
    }
}

/**
 * El contenido con datos (Figma 1-3, 1-87, 1-178 y 99-122 a 99-442): semáforo, la ayuda de los
 * contadores, los contadores, el encabezado de la lista y las tarjetas; con la red tranquila, al final
 * la leyenda de los tres colores (informe, 11.6).
 */
private fun LazyListScope.datosDeLaRed(
    datos: ContenidoEstado.Datos,
    estado: EstadoUiState,
    acciones: AccionesEstado,
    ahora: Instant,
    zona: ZoneId,
) {
    val elegida = estado.severidadElegida
    val noAplicadas = estado.revisionesNoAplicadas
    if (noAplicadas.isNotEmpty()) {
        item {
            AvisoRevisionesNoAplicadas(
                cantidad = noAplicadas.size,
                alVer = { noAplicadas.singleOrNull()?.let(acciones.alAbrirDetalle) ?: acciones.alVerNoAplicadas() },
            )
        }
    }
    val asignadas = estado.asignadasAMi
    if (asignadas.isNotEmpty()) {
        item { AvisoAsignadas(asignadas, estado.contexto.miId, acciones.alVerAsignadas) }
    }
    item { SemaforoDeLaRed(datos, estado, ahora, zona, acciones.alTocarContador) }
    item {
        Text(
            text = stringResource(ayudaDeLosContadores(elegida)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        FilaContadores(
            altas = datos.sinRevisar.altas,
            medias = datos.sinRevisar.medias,
            bajas = datos.sinRevisar.bajas,
            elegida = elegida,
            alTocar = acciones.alTocarContador,
            modifier = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.CONTADORES),
        )
    }
    item {
        // "Ver todas" no está con la red tranquila y sin filtro (Figma 1-178): la leyenda ocupa ese lugar.
        val conVerTodas = elegida != null || datos.semaforo != Semaforo.VERDE
        EncabezadoLista(
            titulo = stringResource(tituloDeLaLista(elegida, vacia = datos.alertas.isEmpty())),
            alVerTodas = if (conVerTodas) acciones.alVerTodas else null,
        )
    }
    if (datos.alertas.isEmpty()) {
        item {
            Text(
                text = stringResource(notaDeLaListaVacia(elegida)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(datos.alertas, key = { it.id }) { alerta ->
            TarjetaAlerta(alerta = alerta, alTocar = acciones.alAbrirDetalle, zona = zona, contexto = estado.contexto)
        }
    }
    if (datos.semaforo == Semaforo.VERDE) item { LeyendaSemaforo() }
}

/**
 * El semáforo con los textos del Figma (cambio 11): "3 alertas altas sin revisar" y "Última alta sin
 * revisar: hace 5 min · actualizado 02:14" con el 3 en el círculo (1-3); verde con bajas pendientes,
 * "Sin urgencias" con cuántas bajas faltan (1-178); verde sin nada pendiente, "Tranquila" con la tilde
 * (443-24); sin conexión, "Último estado conocido" (1-87). Queda marcado para el paso 1 del recorrido.
 */
@Composable
private fun SemaforoDeLaRed(
    datos: ContenidoEstado.Datos,
    estado: EstadoUiState,
    ahora: Instant,
    zona: ZoneId,
    alTocarContador: (Severidad) -> Unit = {},
) {
    val actualizado = estado.datosDe?.let { Formato.horaDeLosDatos(it, ahora, zona) }.orEmpty()
    // Corrección 9: tocar el recuadro hace lo mismo que el contador de su color; otro toque saca el filtro.
    @Composable
    fun descripcion(severidad: Severidad): String = stringResource(
        when {
            estado.severidadElegida == severidad -> R.string.semaforo_ver_todas
            severidad == Severidad.ALTA -> R.string.semaforo_ver_altas
            severidad == Severidad.MEDIA -> R.string.semaforo_ver_medias
            else -> R.string.semaforo_ver_bajas
        },
    )
    val ultimoConocido = stringResource(R.string.semaforo_ultimo_conocido)
    val recorrido = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.SEMAFORO)
    when (datos.semaforo) {
        Semaforo.ROJO, Semaforo.AMARILLO -> {
            val rojo = datos.semaforo == Semaforo.ROJO
            val cantidad = if (rojo) datos.sinRevisar.altas else datos.sinRevisar.medias
            Semaforo(
                tono = if (rojo) Tonos.Alta else Tonos.Media,
                etiqueta = stringResource(R.string.semaforo_atencion),
                titulo = pluralStringResource(
                    if (rojo) R.plurals.semaforo_altas_sin_revisar else R.plurals.semaforo_medias_sin_revisar,
                    cantidad,
                    cantidad,
                ),
                detalle = detalleDelSemaforo(datos, estado, ahora, actualizado, if (rojo) R.string.semaforo_ultima_alta else R.string.semaforo_ultima_media),
                marca = MarcaSemaforo.Numero(cantidad),
                modifier = recorrido,
                alTocar = { alTocarContador(if (rojo) Severidad.ALTA else Severidad.MEDIA) },
                descripcionAccion = descripcion(if (rojo) Severidad.ALTA else Severidad.MEDIA),
            )
        }
        Semaforo.VERDE -> {
            val bajas = datos.sinRevisar.bajas
            if (bajas > 0) {
                Semaforo(
                    tono = Tonos.Baja,
                    etiqueta = stringResource(R.string.semaforo_sin_urgencias),
                    titulo = pluralStringResource(R.plurals.semaforo_bajas_sin_revisar, bajas, bajas),
                    detalle = detalleDelSemaforo(datos, estado, ahora, actualizado, R.string.semaforo_ultima_baja),
                    marca = MarcaSemaforo.Numero(bajas),
                    modifier = recorrido,
                    alTocar = { alTocarContador(Severidad.BAJA) },
                    descripcionAccion = descripcion(Severidad.BAJA),
                )
            } else {
                Semaforo(
                    tono = Tonos.Baja,
                    etiqueta = stringResource(R.string.semaforo_tranquila),
                    titulo = stringResource(R.string.semaforo_tranquila_titulo),
                    detalle = if (estado.sinConexion) ultimoConocido else stringResource(R.string.semaforo_tranquila_detalle, actualizado),
                    marca = MarcaSemaforo.Tilde,
                    modifier = recorrido,
                )
            }
        }
        Semaforo.SIN_DATOS -> SemaforoSinDatos()
    }
}

/** La línea de abajo: cuándo llegó la última sin revisar del nivel que pinta el color ([ultima] es su texto). */
@Composable
private fun detalleDelSemaforo(datos: ContenidoEstado.Datos, estado: EstadoUiState, ahora: Instant, actualizado: String, @StringRes ultima: Int): String {
    val ultimaDeteccion = datos.ultimaDeteccion
    return when {
        estado.sinConexion -> stringResource(R.string.semaforo_ultimo_conocido)
        ultimaDeteccion != null -> stringResource(ultima, Formato.haceCuanto(ultimaDeteccion, ahora), actualizado)
        else -> stringResource(R.string.semaforo_actualizado, actualizado)
    }
}

/**
 * El aviso azul de las revisiones que no se aplicaron (cambio 7; Figma 443-113 y 443-189): otra persona
 * revisó esas alertas antes que la revisión de este teléfono llegara al servidor. Título SemiBold 14 y
 * texto Regular 13 en el azul de información, "Ver" a la derecha; todo el aviso se toca.
 */
@Composable
private fun AvisoRevisionesNoAplicadas(cantidad: Int, alVer: () -> Unit) {
    AvisoAzul(
        titulo = pluralStringResource(R.plurals.estado_no_aplicadas_titulo, cantidad, cantidad),
        texto = stringResource(if (cantidad == 1) R.string.estado_no_aplicada_texto else R.string.estado_no_aplicadas_texto),
        alVer = alVer,
    )
}

/** El aviso azul de Estado: título SemiBold 14, texto Regular 13 y "Ver" a la derecha; se toca entero. */
@Composable
private fun AvisoAzul(titulo: String, texto: String, alVer: () -> Unit) {
    Surface(
        onClick = alVer,
        color = Tonos.Info.fondo,
        contentColor = Tonos.Info.texto,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(text = titulo, style = MaterialTheme.typography.labelLarge)
                Text(text = texto, style = MaterialTheme.typography.bodySmall)
            }
            Text(text = stringResource(R.string.estado_no_aplicadas_ver), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * El aviso azul de las alertas asignadas a vos sin revisar (decisión 42; Figma 468-2144): "1 alerta
 * asignada a vos" / "Te la asignó Taiel." y "Ver", que abre Alertas con "Asignadas a mí". Con varias
 * nombra a quienes las asignaron, y "vos" al final si también te asignaste alguna: "Te las asignaron
 * Sofia y vos." (sin el "vos" parecía que Sofia te había asignado todas).
 */
@Composable
private fun AvisoAsignadas(asignadas: List<Alerta>, miId: String?, alVer: () -> Unit) {
    val cantidad = asignadas.size
    val asignaciones = asignadas.mapNotNull { it.asignacion }
    val porOtros = asignaciones.filter { it.porId != miId }.map { it.por }.distinct()
    val vos = stringResource(R.string.estado_asignadas_vos)
    val quienes = if (asignaciones.any { it.porId == miId }) porOtros + vos else porOtros
    val texto = when {
        porOtros.isEmpty() -> stringResource(if (cantidad == 1) R.string.estado_te_la_asignaste else R.string.estado_te_las_asignaste)
        cantidad == 1 -> stringResource(R.string.estado_asignada_por, porOtros.single())
        quienes.size == 1 -> stringResource(R.string.estado_asignadas_por, quienes.single())
        else -> stringResource(R.string.estado_asignadas_por_varios, quienes.dropLast(1).joinToString(", ") + " y " + quienes.last())
    }
    AvisoAzul(
        titulo = pluralStringResource(R.plurals.estado_asignadas_titulo, cantidad, cantidad),
        texto = texto,
        alVer = alVer,
    )
}

/** Gris y nunca verde mientras la red no se sincronizó (RF01, criterio 3; Figma 1-222). */
@Composable
private fun SemaforoSinDatos() {
    Semaforo(
        tono = Tonos.Neutro,
        etiqueta = stringResource(R.string.semaforo_sin_datos),
        titulo = stringResource(R.string.semaforo_sin_datos_titulo),
        detalle = stringResource(R.string.semaforo_sin_datos_detalle),
        modifier = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.SEMAFORO),
    )
}

/**
 * "Sin revisar" en SemiBold 14 gris y, a la derecha, "Ver todas →" en SemiBold 13 verde (Figma 1-41).
 * El enlace mide lo que su texto; el toque cercano de Compose le da los 48 dp (decisión 19).
 */
@Composable
private fun EncabezadoLista(titulo: String, alVerTodas: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (alVerTodas != null) {
            val descripcion = stringResource(R.string.estado_ver_todas_descripcion)
            Text(
                text = stringResource(R.string.estado_ver_todas),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = alVerTodas)
                    .semantics {
                        contentDescription = descripcion
                        role = Role.Button
                    },
            )
        }
    }
}

private fun ayudaDeLosContadores(elegida: Severidad?): Int = when (elegida) {
    null -> R.string.estado_ayuda_contadores
    Severidad.ALTA -> R.string.estado_ayuda_altas
    Severidad.MEDIA -> R.string.estado_ayuda_medias
    Severidad.BAJA -> R.string.estado_ayuda_bajas
}

/** "Sin revisar", "Solo altas sin revisar" o, si no queda ninguna de esa severidad, "Sin alertas bajas sin revisar". */
private fun tituloDeLaLista(elegida: Severidad?, vacia: Boolean): Int = when (elegida) {
    null -> R.string.estado_lista_sin_revisar
    Severidad.ALTA -> if (vacia) R.string.estado_lista_sin_altas else R.string.estado_lista_altas
    Severidad.MEDIA -> if (vacia) R.string.estado_lista_sin_medias else R.string.estado_lista_medias
    Severidad.BAJA -> if (vacia) R.string.estado_lista_sin_bajas else R.string.estado_lista_bajas
}

/** La nota de la lista vacía; la de las bajas es la del Figma (99-569). */
private fun notaDeLaListaVacia(elegida: Severidad?): Int = when (elegida) {
    null -> R.string.estado_nota_vacia
    Severidad.ALTA -> R.string.estado_nota_sin_altas
    Severidad.MEDIA -> R.string.estado_nota_sin_medias
    Severidad.BAJA -> R.string.estado_nota_sin_bajas
}

/**
 * Bloques gris cálido mientras llega la primera lectura de la base (Figma 1-256): el semáforo de 118,
 * los tres contadores de 64, el encabezado de 160 por 18 y tres tarjetas de 100, con 16 entre piezas.
 */
@Composable
private fun EsqueletoEstado() {
    val color = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(color, RoundedCornerShape(16.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .background(color, RoundedCornerShape(12.dp)),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 18.dp)
                .background(color, RoundedCornerShape(6.dp)),
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(color, RoundedCornerShape(12.dp)),
            )
        }
    }
}

/** Cuánto queda a la vista el aviso de sincronizado, como en el prototipo del Figma. */
const val DURACION_AVISO_SINCRONIZADO_MS = 2_000L

/**
 * El aviso de sincronizado del Figma (99-2): una tarjeta blanca de 300 en el medio de la pantalla, con
 * esquinas de 20 y sombra, el tilde verde en un círculo de 52, "Sincronizado" en SemiBold 18, la fecha y
 * la hora en Regular 14 y debajo si llegaron alertas nuevas, con 24 de margen y 6 entre piezas. Se va
 * sola a los 2 segundos o con un toque, y TalkBack la lee al aparecer.
 */
@Composable
private fun AvisoSincronizado(aviso: AvisoEstado.Sincronizado, zona: ZoneId, alCerrar: () -> Unit) {
    LaunchedEffect(aviso) {
        delay(DURACION_AVISO_SINCRONIZADO_MS)
        alCerrar()
    }
    val barraDeEstado = WindowInsets.statusBars.getTop(LocalDensity.current)
    val posicion = remember(barraDeEstado) { EnElMedioDeLaPantalla(barraDeEstado) }
    Popup(popupPositionProvider = posicion, onDismissRequest = alCerrar, properties = PopupProperties(focusable = false)) {
        Surface(
            onClick = alCerrar,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(300.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(text = stringResource(R.string.estado_sincronizado_titulo), style = MaterialTheme.typography.encabezado)
                Text(
                    text = stringResource(R.string.estado_sincronizado_fecha, Formato.dia(aviso.hora, zona), Formato.hora(aviso.hora, zona)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (aviso.nuevas == 0) {
                        stringResource(R.string.estado_sincronizado_al_dia)
                    } else {
                        pluralStringResource(R.plurals.estado_sincronizado_nuevas, aviso.nuevas, aviso.nuevas)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * El aviso va en el medio de la pantalla, como el Figma, y no del lugar de Estado, que deja afuera la
 * barra inferior. [windowSize] es lo visible, sin las barras del sistema, pero la posición se cuenta
 * desde arriba de todo: por eso se suma la barra de estado (con la app de borde a borde).
 */
private class EnElMedioDeLaPantalla(private val barraDeEstado: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset((windowSize.width - popupContentSize.width) / 2, barraDeEstado + (windowSize.height - popupContentSize.height) / 2)
}

/** La hora de ahora, que avanza cada 30 segundos: así "Última alta sin revisar: hace 5 min" no se queda quieta. */
@Composable
private fun relojDeLaPantalla(): State<Instant> = produceState(initialValue = Instant.now()) {
    while (true) {
        delay(30_000)
        value = Instant.now()
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun EstadoContenidoPreview() {
    ZondaTheme {
        EstadoContenido(
            estado = EstadoUiState(
                contenido = ContenidoEstado.Datos(
                    semaforo = Semaforo.ROJO,
                    sinRevisar = ContadoresSinRevisar(altas = 3, medias = 1, bajas = 0),
                    ultimaDeteccion = Instant.parse("2026-09-27T05:09:00Z"),
                    alertas = emptyList(),
                ),
                datosDe = Instant.parse("2026-09-27T05:14:00Z"),
            ),
            ahora = Instant.parse("2026-09-27T05:14:00Z"),
        )
    }
}
