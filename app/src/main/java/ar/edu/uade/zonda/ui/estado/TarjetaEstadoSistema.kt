package ar.edu.uade.zonda.ui.estado

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.semaforo
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.AyudaGlobito
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.theme.GrisAviso
import ar.edu.uade.zonda.ui.theme.Tono
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.Instant

/**
 * La tarjeta "Estado del sistema" (devolución del docente: que el análisis en vivo esté monitoreado
 * desde la app). Una fila por pieza, el sensor de red, el análisis en vivo y el servidor de alertas, cada
 * una con su punto de color, su "?" y, a la derecha, su semáforo de dos luces (decisión 48): verde si
 * funciona, rojo si se detuvo, no responde o no se sabe, y la de la derecha en ámbar si el sensor no llega
 * a ver todo el tráfico. Sin palabras en la pantalla: la luz se lee por su lugar y su color, y TalkBack
 * dice "Sensor de red: Funciona". Debajo de cada pieza va el dato que la respalda: el tráfico que vio el
 * sensor, cuándo revisó el análisis ([ahora] avanza cada segundo, así se ve contar y, pasados los 2
 * minutos, el análisis pasa a rojo) y cuándo respondió el servidor. Sin botón de reinicio: el teléfono
 * solo mira.
 */
@Composable
fun TarjetaEstadoSistema(sistema: EstadoSistema, ahora: () -> Instant, modifier: Modifier = Modifier) {
    val momento = ahora()
    Panel(modifier = modifier, espacio = 10.dp) {
        Text(text = stringResource(R.string.estado_sistema_titulo), style = MaterialTheme.typography.titleSmall)
        FilaPieza(R.string.estado_sistema_sensor, R.string.estado_sistema_ayuda_sensor, sistema.sensor, detalleDelSensor(sistema, momento))
        FilaPieza(R.string.estado_sistema_analisis, R.string.estado_sistema_ayuda_analisis, sistema.analisis, detalleDelAnalisis(sistema, momento))
        FilaPieza(R.string.estado_sistema_servidor, R.string.estado_sistema_ayuda_servidor, sistema.servidor, detalleDelServidor(sistema, momento))
    }
}

/**
 * "● Sensor de red (?) ······ [● ●]" y debajo su dato. TalkBack lee la fila entera ("Sensor de red:
 * Funciona"); el "?" queda aparte, como su propio botón ("Qué es Sensor de red").
 */
@Composable
private fun FilaPieza(@StringRes nombre: Int, @StringRes ayuda: Int, estado: EstadoPieza, detalle: String) {
    val semaforo = estado.semaforo
    val pieza = stringResource(nombre)
    val descripcion = stringResource(R.string.estado_sistema_descripcion, pieza, stringResource(palabraDe(estado)))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(tonoDe(semaforo).punto, CircleShape),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = pieza, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics {})
                AyudaGlobito(termino = pieza, ayuda = stringResource(ayuda))
            }
            SemaforoDePieza(semaforo)
        }
        // Debajo del nombre, alineado con él: 10 del punto y 10 de separación.
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}

/**
 * El semáforo de una pieza: una caja oscura con dos luces, roja a la izquierda y verde a la derecha, en los
 * dos temas. Se prende la que corresponde y la otra queda apagada, con su color oscuro; si el sensor ve
 * parcialmente, la de la derecha se prende en ámbar.
 */
@Composable
private fun SemaforoDePieza(semaforo: Semaforo) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clearAndSetSemantics {}
            .background(GrisAviso, RoundedCornerShape(50))
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Luz(if (semaforo == Semaforo.ROJO || semaforo == Semaforo.SIN_DATOS) LuzRoja else LuzRojaApagada)
        Luz(
            when (semaforo) {
                Semaforo.VERDE -> LuzVerde
                Semaforo.AMARILLO -> LuzAmbar
                Semaforo.ROJO, Semaforo.SIN_DATOS -> LuzVerdeApagada
            },
        )
    }
}

@Composable
private fun Luz(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(color, CircleShape),
    )
}

private val LuzRoja = Color(0xFFE24B4A)
private val LuzRojaApagada = Color(0xFF6B2C2B)
private val LuzVerde = Color(0xFF58C765)
private val LuzVerdeApagada = Color(0xFF2E4A32)
private val LuzAmbar = Color(0xFFEF9F27)

@Composable
private fun tonoDe(semaforo: Semaforo): Tono = when (semaforo) {
    Semaforo.VERDE -> Tonos.Baja
    Semaforo.AMARILLO -> Tonos.Media
    Semaforo.ROJO, Semaforo.SIN_DATOS -> Tonos.Alta
}

/** La palabra de cada estado: ya no se ve, pero TalkBack la dice ("Sensor de red: Viendo parcialmente"). */
private fun palabraDe(estado: EstadoPieza): Int = when (estado) {
    EstadoPieza.FUNCIONA -> R.string.estado_sistema_funciona
    EstadoPieza.PARCIAL -> R.string.estado_sistema_parcial
    EstadoPieza.DETENIDO -> R.string.estado_sistema_detenido
    EstadoPieza.SIN_DATOS -> R.string.estado_sistema_sin_datos
    EstadoPieza.NO_RESPONDE -> R.string.estado_sistema_no_responde
}

/**
 * Lo que vio el sensor: "Ve todo el tráfico · último tráfico hace 4 min", "No llegó a ver el 5,0 % del
 * tráfico (prueba simulada)" o, si no hubo tráfico para medir (Zeek mide cada 15 minutos sobre las
 * conexiones TCP), "Sin tráfico para medir pérdidas". Sin el análisis no hay noticias del sensor.
 */
@Composable
private fun detalleDelSensor(sistema: EstadoSistema, ahora: Instant): String {
    when (sistema.sensor) {
        EstadoPieza.SIN_DATOS, EstadoPieza.NO_RESPONDE -> return stringResource(R.string.estado_sistema_sensor_sin_noticias)
        EstadoPieza.DETENIDO -> return stringResource(R.string.estado_sistema_sensor_zeek_detenido)
        EstadoPieza.FUNCIONA, EstadoPieza.PARCIAL -> Unit
    }
    val perdida = sistema.perdidaCaptura
    val loQueVio = when {
        perdida == null -> null
        sistema.sinTraficoParaMedir -> stringResource(R.string.estado_sistema_sensor_sin_trafico)
        perdida == 0.0 -> stringResource(R.string.estado_sistema_sensor_ve_todo)
        else -> stringResource(R.string.estado_sistema_sensor_no_vio, Formato.numero(perdida, 1))
    }?.let { texto -> if (sistema.perdidaSimulada) stringResource(R.string.estado_sistema_sensor_simulado, texto) else texto }
    val ultimoTrafico = sistema.ultimoFlujo?.let { Formato.haceCuanto(it, ahora) }
    return when {
        loQueVio != null && ultimoTrafico != null -> stringResource(R.string.estado_sistema_sensor_y_ultimo, loQueVio, ultimoTrafico)
        loQueVio != null -> loQueVio
        ultimoTrafico != null -> stringResource(R.string.estado_sistema_sensor_ultimo, ultimoTrafico)
        else -> stringResource(R.string.estado_sistema_sensor_escuchando)
    }
}

/** Cuándo revisó el tráfico por última vez: "Revisó el tráfico hace 15 s" o "Sin señales desde hace 2 min 31 s". */
@Composable
private fun detalleDelAnalisis(sistema: EstadoSistema, ahora: Instant): String {
    val corrida = sistema.ultimaCorrida ?: return stringResource(R.string.estado_sistema_analisis_nunca)
    val hace = Formato.duracion(Duration.between(corrida, ahora))
    return when (sistema.analisis) {
        EstadoPieza.FUNCIONA -> stringResource(R.string.estado_sistema_analisis_reviso, hace)
        EstadoPieza.DETENIDO -> stringResource(R.string.estado_sistema_analisis_sin_senales, hace)
        else -> stringResource(R.string.estado_sistema_analisis_ultima_senal, hace)
    }
}

/** Cuándo contestó el servidor la última consulta de la app (cada 20 s), o que no contesta. */
@Composable
private fun detalleDelServidor(sistema: EstadoSistema, ahora: Instant): String {
    if (sistema.servidor == EstadoPieza.NO_RESPONDE) return stringResource(R.string.estado_sistema_servidor_no_contesta)
    val segundos = Duration.between(sistema.consultadoEn, ahora)
    if (segundos.seconds < 3) return stringResource(R.string.estado_sistema_servidor_recien)
    return stringResource(R.string.estado_sistema_servidor_respondio, Formato.duracion(segundos))
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun TarjetaEstadoSistemaPreview() {
    val ahora = Instant.parse("2026-10-24T21:30:00Z")
    ZondaTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TarjetaEstadoSistema(
                sistema = EstadoSistema(
                    sensor = EstadoPieza.FUNCIONA,
                    analisis = EstadoPieza.FUNCIONA,
                    servidor = EstadoPieza.FUNCIONA,
                    ultimaCorrida = ahora.minusSeconds(12),
                    ultimoFlujo = ahora.minusSeconds(240),
                    consultadoEn = ahora.minusSeconds(7),
                    perdidaCaptura = 0.0,
                    perdidaMedidaEn = ahora.minusSeconds(360),
                ),
                ahora = { ahora },
            )
            TarjetaEstadoSistema(
                sistema = EstadoSistema(
                    sensor = EstadoPieza.PARCIAL,
                    analisis = EstadoPieza.FUNCIONA,
                    servidor = EstadoPieza.FUNCIONA,
                    ultimaCorrida = ahora.minusSeconds(7),
                    ultimoFlujo = ahora.minusSeconds(20),
                    consultadoEn = ahora,
                    perdidaCaptura = 3.2,
                    perdidaMedidaEn = ahora.minusSeconds(120),
                ),
                ahora = { ahora },
            )
            TarjetaEstadoSistema(
                sistema = EstadoSistema(
                    sensor = EstadoPieza.SIN_DATOS,
                    analisis = EstadoPieza.DETENIDO,
                    servidor = EstadoPieza.FUNCIONA,
                    ultimaCorrida = ahora.minusSeconds(150),
                    ultimoFlujo = null,
                    consultadoEn = ahora,
                ),
                ahora = { ahora },
            )
        }
    }
}
