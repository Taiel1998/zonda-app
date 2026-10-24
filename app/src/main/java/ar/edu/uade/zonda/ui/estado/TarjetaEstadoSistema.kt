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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EstadoPieza
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.theme.Tono
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.Instant

/**
 * La tarjeta "Estado del sistema" (devolución del docente: que el análisis en vivo esté monitoreado
 * desde la app). Una fila por pieza, el sensor de red, el análisis en vivo y el servidor de alertas,
 * cada una con su punto, su color y la palabra, para que se entienda sin distinguir colores: verde lo que
 * funciona y rojo lo que no, también lo que no se sabe. Abajo, hace cuánto llegó la última corrida del
 * análisis; [ahora] avanza cada segundo, así se ve contar y, pasados los 2 minutos, el análisis pasa a
 * Detenido. Sin botón de reinicio: el teléfono solo mira.
 */
@Composable
fun TarjetaEstadoSistema(sistema: EstadoSistema, ahora: () -> Instant, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, espacio = 8.dp) {
        Text(text = stringResource(R.string.estado_sistema_titulo), style = MaterialTheme.typography.titleSmall)
        FilaPieza(R.string.estado_sistema_sensor, sistema.sensor)
        FilaPieza(R.string.estado_sistema_analisis, sistema.analisis)
        FilaPieza(R.string.estado_sistema_servidor, sistema.servidor)
        Text(
            text = ultimaCorrida(sistema, ahora()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "● Sensor de red ······ Funciona": el punto y la palabra con el color del estado; TalkBack lee las dos. */
@Composable
private fun FilaPieza(@StringRes nombre: Int, estado: EstadoPieza) {
    val tono = tonoDe(estado)
    val pieza = stringResource(nombre)
    val palabra = stringResource(palabraDe(estado))
    val descripcion = stringResource(R.string.estado_sistema_descripcion, pieza, palabra)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = descripcion },
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(tono.punto, CircleShape),
        )
        Text(text = pieza, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = palabra, style = MaterialTheme.typography.labelLarge, color = tono.texto)
    }
}

@Composable
private fun tonoDe(estado: EstadoPieza): Tono = if (estado == EstadoPieza.FUNCIONA) Tonos.Baja else Tonos.Alta

private fun palabraDe(estado: EstadoPieza): Int = when (estado) {
    EstadoPieza.FUNCIONA -> R.string.estado_sistema_funciona
    EstadoPieza.DETENIDO -> R.string.estado_sistema_detenido
    EstadoPieza.SIN_DATOS -> R.string.estado_sistema_sin_datos
    EstadoPieza.NO_RESPONDE -> R.string.estado_sistema_no_responde
}

/** "Última corrida hace 12 s · último flujo hace 4 min", o que todavía no llegó ninguna. */
@Composable
private fun ultimaCorrida(sistema: EstadoSistema, ahora: Instant): String {
    val corrida = sistema.ultimaCorrida ?: return stringResource(R.string.estado_sistema_sin_corridas)
    val hace = Formato.duracion(Duration.between(corrida, ahora))
    val flujo = sistema.ultimoFlujo ?: return stringResource(R.string.estado_sistema_ultima_corrida, hace)
    return stringResource(R.string.estado_sistema_ultima_corrida_flujo, hace, Formato.haceCuanto(flujo, ahora))
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
                    consultadoEn = ahora,
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
