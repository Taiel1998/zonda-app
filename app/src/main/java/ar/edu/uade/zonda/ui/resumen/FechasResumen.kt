package ar.edu.uade.zonda.ui.resumen

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.RangoResumen
import ar.edu.uade.zonda.domain.model.Tramo
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.tituloHoja
import ar.edu.uade.zonda.ui.theme.zonda
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val LOCALE: Locale = Locale.forLanguageTag("es-AR")

/**
 * Lo que se va eligiendo en "Elegí las fechas" (cambio 19): Desde y Hasta con sus horas, y si el
 * próximo día que se toque es el último. Se toca el primer día y después el último; para un día solo,
 * ese día y "Mostrar". Las horas quedan como estaban al cambiar los días.
 */
data class BorradorFechas(
    val desde: LocalDateTime,
    val hasta: LocalDateTime,
    val esperandoUltimo: Boolean = false,
) {
    val rango: RangoResumen
        get() = RangoResumen(desde, hasta)

    fun tocarDia(dia: LocalDate): BorradorFechas {
        val horaDesde = desde.toLocalTime()
        val horaHasta = hasta.toLocalTime()
        return when {
            !esperandoUltimo -> BorradorFechas(dia.atTime(horaDesde), dia.atTime(horaHasta), esperandoUltimo = true)
            // Si el segundo toque es antes del primero, ese pasa a ser el primer día.
            dia.isBefore(desde.toLocalDate()) -> BorradorFechas(dia.atTime(horaDesde), desde.toLocalDate().atTime(horaHasta))
            else -> copy(hasta = dia.atTime(horaHasta), esperandoUltimo = false)
        }
    }

    fun conHoraDesde(hora: LocalTime) = copy(desde = desde.toLocalDate().atTime(hora))

    fun conHoraHasta(hora: LocalTime) = copy(hasta = hasta.toLocalDate().atTime(hora))

    companion object {
        fun de(rango: RangoResumen) = BorradorFechas(rango.desde, rango.hasta)
    }
}

/** Qué hora se está eligiendo en el reloj. */
private enum class HoraEnElReloj { DESDE, HASTA }

/**
 * La hoja "Elegí las fechas" (cambio 19; Figma 463-2027): Desde y Hasta con su día y su hora, los
 * meses completos que tocan los 30 días que guarda el teléfono (los días sin datos en gris) y
 * "Mostrar N alertas", que dice cuántas quedan con los demás filtros. "Cancelar" la cierra sin cambiar
 * nada. La hora se elige con el reloj de Material 3 ([RelojDeLaHora]).
 */
@Composable
fun HojaFechas(
    rango: RangoResumen,
    hoy: LocalDate,
    cuantas: (RangoResumen) -> Int,
    alMostrar: (RangoResumen) -> Unit,
    alCancelar: () -> Unit,
) {
    HojaInferior(alCerrar = alCancelar) {
        ContenidoHojaFechas(rango, hoy, cuantas, alMostrar, alCancelar)
    }
}

/** El contenido de la hoja, aparte para dibujarlo en las pruebas sin la ventana de la hoja. */
@Composable
fun ColumnScope.ContenidoHojaFechas(
    rango: RangoResumen,
    hoy: LocalDate,
    cuantas: (RangoResumen) -> Int,
    alMostrar: (RangoResumen) -> Unit,
    alCancelar: () -> Unit,
) {
    var borrador by remember(rango) { mutableStateOf(BorradorFechas.de(rango)) }
    var reloj by rememberSaveable { mutableStateOf<HoraEnElReloj?>(null) }
    val guardados = PeriodoResumen.guardados(hoy)
    val elegido = borrador.rango

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(R.string.resumen_fechas_titulo), style = MaterialTheme.typography.tituloHoja, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.accion_cancelar),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = alCancelar),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CampoDeFecha(
            etiqueta = stringResource(R.string.resumen_fechas_desde),
            momento = borrador.desde,
            alTocarHora = { reloj = HoraEnElReloj.DESDE },
            modifier = Modifier.weight(1f),
        )
        Text(text = "→", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CampoDeFecha(
            etiqueta = stringResource(R.string.resumen_fechas_hasta),
            momento = borrador.hasta,
            alTocarHora = { reloj = HoraEnElReloj.HASTA },
            modifier = Modifier.weight(1f),
        )
    }
    if (!elegido.valido && !borrador.esperandoUltimo) {
        Text(text = stringResource(R.string.resumen_fechas_error), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.error)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        text = stringResource(R.string.resumen_fechas_guardados, Formato.diaYMes(guardados.desde), Formato.diaYMes(guardados.hasta)),
        style = MaterialTheme.typography.nota,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    mesesQueTocan(guardados).forEach { mes ->
        MesDelCalendario(mes = mes, guardados = guardados, elegido = elegido, alTocar = { borrador = borrador.tocarDia(it) })
    }
    val quedan = if (elegido.valido) cuantas(elegido) else 0
    BotonPrincipal(
        texto = pluralStringResource(R.plurals.resumen_mostrar_alertas, quedan, quedan),
        alTocar = { alMostrar(elegido) },
        habilitado = elegido.valido,
    )

    when (reloj) {
        HoraEnElReloj.DESDE -> RelojDeLaHora(
            titulo = stringResource(R.string.resumen_fechas_hora_inicio),
            inicial = borrador.desde.toLocalTime(),
            alAceptar = { hora ->
                borrador = borrador.conHoraDesde(hora)
                reloj = null
            },
            alCancelar = { reloj = null },
        )
        HoraEnElReloj.HASTA -> RelojDeLaHora(
            titulo = stringResource(R.string.resumen_fechas_hora_fin),
            inicial = borrador.hasta.toLocalTime(),
            alAceptar = { hora ->
                borrador = borrador.conHoraHasta(hora)
                reloj = null
            },
            alCancelar = { reloj = null },
        )
        null -> Unit
    }
}

/** Los meses que tocan los días guardados, completos (Taiel los prefirió a la grilla seguida de 30 días). */
fun mesesQueTocan(guardados: Tramo): List<YearMonth> =
    generateSequence(YearMonth.from(guardados.desde)) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(YearMonth.from(guardados.hasta)) }
        .toList()

/**
 * "Desde" o "Hasta" (Figma 463-2027): el borde gris con esquinas de 12, la etiqueta Regular 11 gris y
 * debajo el día Medium 15 ("jue 24/09") con la hora en su recuadro gris SemiBold 13, que abre el reloj.
 */
@Composable
private fun CampoDeFecha(etiqueta: String, momento: LocalDateTime, alTocarHora: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = etiqueta, style = estiloRotuloChicoResumen, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Formato.diaDeLaSemanaCorto(momento.toLocalDate()) + " " + Formato.diaYMes(momento.toLocalDate()),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            val cambiarHora = stringResource(R.string.resumen_fechas_cambiar_hora)
            Text(
                text = Formato.horaDelDia(momento.toLocalTime()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClickLabel = cambiarHora, role = Role.Button, onClick = alTocarHora)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Un mes completo (Figma 463-2027): el nombre SemiBold 15, los días de la semana de lunes a domingo
 * Regular 11 gris y las semanas de 36 de alto con 4 entre ellas. Los días que el teléfono no guarda
 * van en gris y no se tocan; el primero y el último elegidos, en el círculo verde de 36, y los del
 * medio sobre el verde claro.
 */
@Composable
private fun MesDelCalendario(mes: YearMonth, guardados: Tramo, elegido: RangoResumen, alTocar: (LocalDate) -> Unit) {
    val primero = mes.atDay(1)
    // Lugares vacíos antes del 1: la semana empieza el lunes.
    val antes = primero.dayOfWeek.value - DayOfWeek.MONDAY.value
    val celdas: List<LocalDate?> = List(antes) { null } + (1..mes.lengthOfMonth()).map { mes.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = mes.month.getDisplayName(TextStyle.FULL_STANDALONE, LOCALE).replaceFirstChar { it.titlecase(LOCALE) } + " " + mes.year,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Row {
            DayOfWeek.entries.forEach { dia ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).height(20.dp)) {
                    Text(
                        text = dia.getDisplayName(TextStyle.SHORT, LOCALE).take(2).lowercase(LOCALE),
                        style = estiloRotuloChicoResumen,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        celdas.chunked(7).forEach { semana ->
            Row {
                semana.forEach { dia ->
                    Box(modifier = Modifier.weight(1f).height(36.dp)) {
                        if (dia != null) CeldaDelDia(dia, guardados, elegido, alTocar)
                    }
                }
                repeat(7 - semana.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CeldaDelDia(dia: LocalDate, guardados: Tramo, elegido: RangoResumen, alTocar: (LocalDate) -> Unit) {
    val colores = MaterialTheme.colorScheme
    val guardado = dia in guardados
    val extremo = dia == elegido.primerDia || dia == elegido.ultimoDia
    val enMedio = !extremo && elegido.valido && dia.isAfter(elegido.primerDia) && dia.isBefore(elegido.ultimoDia)
    val descripcion = Formato.diaDeLaSemana(dia) + " " + Formato.diaYMes(dia)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .then(if (enMedio) Modifier.background(colores.secondaryContainer) else Modifier)
            .then(if (guardado) Modifier.clickable(role = Role.Button) { alTocar(dia) } else Modifier)
            .semantics { contentDescription = descripcion },
    ) {
        if (extremo && guardado) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).background(colores.primary, CircleShape)) {
                Text(text = dia.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colores.onPrimary)
            }
        } else {
            Text(
                text = dia.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !guardado -> MaterialTheme.zonda.diaSinDatos
                    enMedio -> colores.primary
                    else -> colores.onSurface
                },
            )
        }
    }
}

/**
 * El reloj de las alarmas de Android para la hora de "Desde" o "Hasta" (cambio 19; Figma 463-2466):
 * el TimePicker de Material 3 en un diálogo, con los colores de Zonda. Abre en la rueda; respeta si el
 * teléfono usa 24 horas (dos ruedas: 00 a 11 afuera y 12 a 23 adentro) o a. m. y p. m., y el ícono del
 * teclado pasa a escribir la hora, como lo trae el reloj.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelojDeLaHora(titulo: String, inicial: LocalTime, alAceptar: (LocalTime) -> Unit, alCancelar: () -> Unit) {
    val contexto = LocalContext.current
    val estado = rememberTimePickerState(initialHour = inicial.hour, initialMinute = inicial.minute, is24Hour = DateFormat.is24HourFormat(contexto))
    var escribiendo by rememberSaveable { mutableStateOf(false) }
    val colores = MaterialTheme.colorScheme
    val coloresReloj = TimePickerDefaults.colors(
        clockDialColor = MaterialTheme.zonda.ruedaReloj,
        clockDialSelectedContentColor = colores.onPrimary,
        clockDialUnselectedContentColor = colores.onSurface,
        selectorColor = colores.primary,
        containerColor = MaterialTheme.zonda.fondoReloj,
        periodSelectorBorderColor = colores.outline,
        periodSelectorSelectedContainerColor = MaterialTheme.zonda.horaElegidaReloj,
        periodSelectorUnselectedContainerColor = MaterialTheme.zonda.fondoReloj,
        periodSelectorSelectedContentColor = colores.primary,
        periodSelectorUnselectedContentColor = colores.onSurfaceVariant,
        timeSelectorSelectedContainerColor = MaterialTheme.zonda.horaElegidaReloj,
        timeSelectorUnselectedContainerColor = MaterialTheme.zonda.ruedaReloj,
        timeSelectorSelectedContentColor = colores.primary,
        timeSelectorUnselectedContentColor = colores.onSurface,
    )
    Dialog(onDismissRequest = alCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.zonda.fondoReloj, modifier = Modifier.padding(horizontal = 24.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
            ) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.nota,
                    color = colores.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (escribiendo) TimeInput(state = estado, colors = coloresReloj) else TimePicker(state = estado, colors = coloresReloj)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { escribiendo = !escribiendo }) {
                        Icon(
                            painter = painterResource(if (escribiendo) R.drawable.ic_reloj else R.drawable.ic_teclado),
                            contentDescription = stringResource(if (escribiendo) R.string.resumen_fechas_hora_en_reloj else R.string.resumen_fechas_escribir_hora),
                            tint = colores.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = alCancelar) {
                        Text(text = stringResource(R.string.accion_cancelar), style = MaterialTheme.typography.labelLarge, color = colores.primary)
                    }
                    TextButton(onClick = { alAceptar(LocalTime.of(estado.hour, estado.minute)) }) {
                        Text(text = stringResource(R.string.accion_aceptar), style = MaterialTheme.typography.labelLarge, color = colores.primary)
                    }
                }
            }
        }
    }
}

/**
 * El chip "Fechas" de la fila del período (cambio 19; Figma 463-1836). Con fechas elegidas dice los días
 * y, si no son días enteros, el relojito. Si al lado de "Filtros (n)" no entra, dice lo más corto que
 * entra ([textosDelChip]) en lugar de cortarse ("12/10 al 1…").
 */
@Composable
fun ChipDeFechas(fechas: RangoResumen?, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    if (fechas == null) {
        ChipConIcono(texto = stringResource(R.string.resumen_fechas), icono = R.drawable.ic_calendario, elegido = false, alTocar = alTocar, modifier = modifier)
        return
    }
    val recursos = LocalContext.current.resources
    val textos = remember(fechas, recursos) { fechas.textosDelChip(recursos) }
    val medidor = rememberTextMeasurer()
    val estilo = MaterialTheme.typography.opcion
    val densidad = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val elegido = textos.firstOrNull { texto ->
            val sinTexto = with(densidad) { anchoSinTexto(elegido = true, conIconoFinal = texto.conReloj).roundToPx() }
            medidor.measure(texto.texto, estilo, maxLines = 1).size.width + sinTexto <= constraints.maxWidth
        } ?: textos.last()
        ChipConIcono(
            texto = elegido.texto,
            icono = R.drawable.ic_calendario,
            elegido = true,
            alTocar = alTocar,
            iconoFinal = if (elegido.conReloj) R.drawable.ic_reloj else null,
        )
    }
}
