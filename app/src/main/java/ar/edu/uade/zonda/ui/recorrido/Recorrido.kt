package ar.edu.uade.zonda.ui.recorrido

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.zonda
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** La pantalla en la que se muestra cada paso; el recorrido navega solo hasta ella. */
enum class PantallaRecorrido { ESTADO, ALERTAS, DETALLE, RESUMEN, AJUSTES }

/**
 * Los once pasos del recorrido (RF05; Figma 107-3321 a 107-4731), cada uno con su pantalla y lo que
 * resalta. Los dos del Resumen lo abren encima de Estado, que es desde donde se abre.
 */
enum class PasoRecorrido(
    val pantalla: PantallaRecorrido,
    val objetivo: ObjetivoRecorrido,
    @param:StringRes val titulo: Int,
    @param:StringRes val texto: Int,
) {
    SEMAFORO(PantallaRecorrido.ESTADO, ObjetivoRecorrido.SEMAFORO, R.string.recorrido_1_titulo, R.string.recorrido_1_texto),
    CONTADORES(PantallaRecorrido.ESTADO, ObjetivoRecorrido.CONTADORES, R.string.recorrido_2_titulo, R.string.recorrido_2_texto),
    SINCRONIZAR(PantallaRecorrido.ESTADO, ObjetivoRecorrido.SINCRONIZAR, R.string.recorrido_3_titulo, R.string.recorrido_3_texto),
    RESUMEN(PantallaRecorrido.ESTADO, ObjetivoRecorrido.ICONO_RESUMEN, R.string.recorrido_4_titulo, R.string.recorrido_4_texto),
    ALERTAS(PantallaRecorrido.ESTADO, ObjetivoRecorrido.PESTANIA_ALERTAS, R.string.recorrido_5_titulo, R.string.recorrido_5_texto),
    FILTROS(PantallaRecorrido.ALERTAS, ObjetivoRecorrido.CHIPS, R.string.recorrido_6_titulo, R.string.recorrido_6_texto),
    TARJETA(PantallaRecorrido.ALERTAS, ObjetivoRecorrido.PRIMERA_TARJETA, R.string.recorrido_7_titulo, R.string.recorrido_7_texto),
    REVISAR(PantallaRecorrido.DETALLE, ObjetivoRecorrido.BOTON_REVISAR, R.string.recorrido_8_titulo, R.string.recorrido_8_texto),
    CIFRAS(PantallaRecorrido.RESUMEN, ObjetivoRecorrido.CIFRAS_RESUMEN, R.string.recorrido_9_titulo, R.string.recorrido_9_texto),
    PERIODO(PantallaRecorrido.RESUMEN, ObjetivoRecorrido.PERIODO_RESUMEN, R.string.recorrido_10_titulo, R.string.recorrido_10_texto),
    AJUSTES(PantallaRecorrido.AJUSTES, ObjetivoRecorrido.TARJETA_AVISOS, R.string.recorrido_11_titulo, R.string.recorrido_11_texto),
}

/**
 * Muestra el recorrido una sola vez: después del primer inicio, con una red pareada y mientras no se
 * haya terminado u omitido (queda marcado en DataStore). [alertaDelDetalle] es la alerta que abre el
 * paso del detalle: la primera de la lista, la misma que resalta el paso anterior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorridoViewModel(
    private val preferencias: PreferenciasRepository,
    redes: RedesRepository,
    alertas: AlertasRepository? = null,
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

    val alertaDelDetalle: StateFlow<String?> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null || alertas == null) flowOf(null) else alertas.observarAlertas(red.id, FiltroAlertas.TODAS).map { it.firstOrNull()?.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun siguiente() {
        if (indice.value < PasoRecorrido.entries.lastIndex) indice.value++ else terminar()
    }

    /** Terminar u omitir: no vuelve a aparecer. */
    fun terminar() {
        viewModelScope.launch { preferencias.marcarRecorridoHecho() }
    }
}

/** El borde del resaltado (Figma 107-4852): verde claro de 3, por dentro, con esquinas de 14. */
private val ColorResaltado = Color(0xFF99E3C7)
private val GrosorResaltado = 3.dp
private val RadioResaltado = 14.dp

/** El resaltado queda 5 por fuera del elemento. */
private val MargenResaltado = 5.dp

/** De la orilla del elemento a la tarjeta: los 5 del resaltado y 11 más. */
private val SeparacionTarjeta = 16.dp


/**
 * La capa del recorrido sobre toda la app: el velo negro al 50 % con el elemento del paso a la vista,
 * el borde verde y la tarjeta al lado, abajo si entra y si no arriba. Si el elemento no está en la
 * pantalla (sin datos todavía no hay contadores) queda la tarjeta sola, en el medio. Lo de atrás no se
 * puede tocar.
 */
@Composable
fun CapaRecorrido(
    paso: PasoRecorrido,
    registro: RegistroRecorrido,
    alSiguiente: () -> Unit,
    alOmitir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Al terminar u omitir el recorrido no queda ningún objetivo pedido: si quedaba el último, cada vez que
    // esa pantalla se abría se desplazaba sola hasta él (Avisos en Ajustes, o Revisar en el detalle).
    DisposableEffect(paso) {
        registro.actual = paso.objetivo
        onDispose { registro.actual = null }
    }
    val objetivo = registro.posicionDe(paso.objetivo)
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ETIQUETA_CAPA)
            // Se queda con todos los toques que no sean de la tarjeta.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hueco = objetivo?.inflate(MargenResaltado.toPx())
            val radio = RadioResaltado.toPx()
            val velo = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (hueco != null) addRoundRect(RoundRect(hueco, CornerRadius(radio)))
            }
            drawPath(velo, Color.Black.copy(alpha = 0.5f))
            if (hueco != null) {
                val grosor = GrosorResaltado.toPx()
                val borde = hueco.deflate(grosor / 2)
                drawRoundRect(
                    color = ColorResaltado,
                    topLeft = borde.topLeft,
                    size = borde.size,
                    cornerRadius = CornerRadius(radio - grosor / 2),
                    style = Stroke(grosor),
                )
            }
        }
        UbicacionDeTarjeta(objetivo = objetivo) {
            TarjetaRecorrido(paso = paso, alSiguiente = alSiguiente, alOmitir = alOmitir)
        }
    }
}

/** Pone la tarjeta debajo del elemento si entra, si no arriba; sin elemento, en el medio. */
@Composable
private fun UbicacionDeTarjeta(objetivo: Rect?, contenido: @Composable () -> Unit) {
    val barras = WindowInsets.systemBars
    Layout(content = contenido, modifier = Modifier.fillMaxSize()) { medibles, restricciones ->
        val tarjeta = medibles.single().measure(restricciones.copy(minWidth = 0, minHeight = 0))
        val alto = restricciones.maxHeight
        val arriba = barras.getTop(this) + 16.dp.roundToPx()
        val abajo = alto - barras.getBottom(this) - 16.dp.roundToPx()
        val separacion = SeparacionTarjeta.roundToPx()
        val y = when {
            objetivo == null -> (alto - tarjeta.height) / 2
            objetivo.bottom + separacion + tarjeta.height <= abajo -> objetivo.bottom.toInt() + separacion
            else -> (objetivo.top.toInt() - separacion - tarjeta.height).coerceAtLeast(arriba)
        }
        layout(restricciones.maxWidth, alto) { tarjeta.place(0, y) }
    }
}

/**
 * La tarjeta del Figma (107-4853): blanca, esquinas de 16, 16 de margen y 8 entre piezas; "PASO 6
 * DE 11" en SemiBold 11 verde, el título en SemiBold 17, el texto en Regular 14 gris y abajo Omitir y
 * Siguiente (en el último, Listo).
 */
@Composable
fun TarjetaRecorrido(paso: PasoRecorrido, alSiguiente: () -> Unit, alOmitir: () -> Unit, modifier: Modifier = Modifier) {
    val ultimo = paso == PasoRecorrido.entries.last()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.recorrido_paso, paso.ordinal + 1, PasoRecorrido.entries.size).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // Encabezado, y se anuncia al tocar "Siguiente": la tarjeta cambia de lugar y de texto.
            Text(
                text = stringResource(paso.titulo),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Text(text = stringResource(paso.texto), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (paso == PasoRecorrido.SEMAFORO) ColoresDelSemaforo()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BotonEnlace(texto = stringResource(R.string.recorrido_omitir), alTocar = alOmitir)
                BotonPrincipal(
                    texto = stringResource(if (ultimo) R.string.recorrido_listo else R.string.recorrido_siguiente),
                    alTocar = alSiguiente,
                    anchoCompleto = false,
                )
            }
        }
    }
}

/** Figma 107-3464: qué quiere decir cada color del semáforo, con su punto de 12 y 8 hasta el texto. */
@Composable
private fun ColoresDelSemaforo() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Tonos.Alta.punto to R.string.recorrido_1_rojo,
            Tonos.Media.punto to R.string.recorrido_1_amarillo,
            Tonos.Baja.punto to R.string.recorrido_1_verde,
        ).forEach { (color, texto) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(12.dp)
                        .background(color, CircleShape),
                )
                Text(text = stringResource(texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.zonda.textoLista)
            }
        }
    }
}

/** Para encontrar la capa en las pruebas. */
const val ETIQUETA_CAPA = "capa_recorrido"
