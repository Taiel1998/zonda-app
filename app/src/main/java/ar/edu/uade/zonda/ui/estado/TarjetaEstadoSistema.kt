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
import ar.edu.uade.zonda.ui.components.EtiquetaConAyuda
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.theme.GrisAviso
import ar.edu.uade.zonda.ui.theme.Tono
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.Instant

/**
 * La tarjeta "Estado del sistema" (devolución del docente: que el análisis en vivo esté monitoreado
 * desde la app). El título lleva el único "?" de la tarjeta, que explica las tres piezas y los colores.
 * Una fila por pieza, el sensor de red, el análisis en vivo y el servidor de alertas, cada una con su punto
 * de color y, a la derecha, su semáforo de dos luces (decisión 48): verde si funciona, rojo si se detuvo,
 * no responde o no se sabe, y la de la derecha en ámbar si el sensor no llega a ver todo el tráfico. Sin
 * palabras en la pantalla: la luz se lee por su lugar y su color, y TalkBack dice "Sensor de red:
 * Funciona". Debajo de cada pieza va su dato, corto y con la misma forma en las tres: "Último tráfico hace
 * 4 min", "Última revisión hace 12 s" ([ahora] avanza cada segundo, así se ve contar) y "Última respuesta
 * hace 7 s". Sin botón de reinicio: el teléfono solo mira.
 */
@Composable
fun TarjetaEstadoSistema(sistema: EstadoSistema, ahora: () -> Instant, modifier: Modifier = Modifier) {
    val momento = ahora()
    Panel(modifier = modifier, espacio = 10.dp) {
        EtiquetaConAyuda(
            texto = stringResource(R.string.estado_sistema_titulo),
            ayuda = stringResource(R.string.estado_sistema_ayuda),
            estilo = MaterialTheme.typography.titleSmall,
        )
        FilaPieza(R.string.estado_sistema_sensor, sistema.sensor, detalleDelSensor(sistema, momento))
        FilaPieza(R.string.estado_sistema_analisis, sistema.analisis, detalleDelAnalisis(sistema, momento))
        FilaPieza(R.string.estado_sistema_servidor, sistema.servidor, detalleDelServidor(sistema, momento))
    }
}

/** "● Sensor de red ······ [● ●]" y debajo su dato. TalkBack lee la fila entera ("Sensor de red: Funciona"). */
@Composable
private fun FilaPieza(@StringRes nombre: Int, estado: EstadoPieza, detalle: String) {
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
            Text(
                text = pieza,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {},
            )
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
 * El sensor: "Último tráfico hace 4 min" (o "Escuchando la red" si todavía no vio tráfico); si ve
 * parcialmente, "Pierde el 5,0 % del tráfico", con "(simulado)" si la pérdida es la de prueba del
 * laboratorio; "No escucha la red" si Zeek no anda y "Sin datos" si el análisis no da noticias de él.
 */
@Composable
private fun detalleDelSensor(sistema: EstadoSistema, ahora: Instant): String {
    when (sistema.sensor) {
        EstadoPieza.SIN_DATOS, EstadoPieza.NO_RESPONDE -> return stringResource(R.string.estado_sistema_sin_datos)
        EstadoPieza.DETENIDO -> return stringResource(R.string.estado_sistema_sensor_no_escucha)
        EstadoPieza.PARCIAL, EstadoPieza.FUNCIONA -> Unit
    }
    val perdida = sistema.perdidaCaptura
    if (sistema.sensor == EstadoPieza.PARCIAL && perdida != null) {
        val pierde = stringResource(R.string.estado_sistema_sensor_pierde, Formato.numero(perdida, 1))
        return if (sistema.perdidaSimulada) stringResource(R.string.estado_sistema_sensor_simulado, pierde) else pierde
    }
    val ultimo = sistema.ultimoFlujo ?: return stringResource(R.string.estado_sistema_sensor_escuchando)
    return stringResource(R.string.estado_sistema_sensor_ultimo, cuanto(Duration.between(ultimo, ahora)))
}

/** El análisis: "Última revisión hace 12 s"; la luz dice si es reciente (verde) o si se detuvo (roja). */
@Composable
private fun detalleDelAnalisis(sistema: EstadoSistema, ahora: Instant): String {
    val corrida = sistema.ultimaCorrida ?: return stringResource(R.string.estado_sistema_sin_datos)
    return stringResource(R.string.estado_sistema_analisis_ultima, cuanto(Duration.between(corrida, ahora)))
}

/** El servidor: "Última respuesta hace 7 s" a la consulta de la app (cada 20 s), "Respondió recién" o "No responde". */
@Composable
private fun detalleDelServidor(sistema: EstadoSistema, ahora: Instant): String {
    if (sistema.servidor == EstadoPieza.NO_RESPONDE) return stringResource(R.string.estado_sistema_no_responde)
    val hace = Duration.between(sistema.consultadoEn, ahora)
    if (hace.seconds < 3) return stringResource(R.string.estado_sistema_servidor_recien)
    return stringResource(R.string.estado_sistema_servidor_ultima, cuanto(hace))
}

/** Cuánto pasó, en una sola unidad para que el dato sea corto: `12 s`, `24 min`, `3 h`, `2 días`. */
private fun cuanto(duracion: Duration): String {
    val segundos = duracion.seconds.coerceAtLeast(0)
    return when {
        segundos < 60 -> "$segundos s"
        segundos < 3600 -> "${segundos / 60} min"
        segundos < 86_400 -> "${segundos / 3600} h"
        segundos < 2 * 86_400 -> "1 día"
        else -> "${segundos / 86_400} días"
    }
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
