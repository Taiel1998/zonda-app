package ar.edu.uade.zonda.ui.primerinicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonAyuda
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.HojaTipos
import ar.edu.uade.zonda.ui.components.IndicadorSeveridad
import ar.edu.uade.zonda.ui.components.Opcion
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TituloDeBloque
import ar.edu.uade.zonda.ui.detector
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.zonda

/**
 * Severidad de cada tipo de ataque (RF05 y RF06; Figma 66-3212). Vale para todos los teléfonos de la
 * red. Va en los dos bloques del cambio 8, lo que detectó la IA y lo que no (servicio no autorizado y
 * volumen inusual, cambio 9), con una tarjeta por tipo y sus tres opciones; el ⓘ del primer bloque
 * abre "Qué es cada tipo" con lo elegido en pantalla.
 *
 * Desde la 1.0.5 la cambia solo un administrador (decisión 39) y el primer inicio ya no la muestra. Con
 * [quienesLasCambian], la pantalla es de solo lectura, la de la analista (Figma 468-3353): arriba "Las
 * cambia un administrador: Taiel o Meli." y cada tipo con su etiqueta, sin "Guardar".
 */
@Composable
fun SeveridadesScreen(
    severidades: SeveridadPorTipo,
    alCambiar: (TipoAtaque, Severidad) -> Unit,
    textoBoton: String,
    alGuardar: () -> Unit,
    alVolver: (() -> Unit)? = null,
    titulo: String = stringResource(R.string.inicio_antes_de_empezar),
    intro: String = stringResource(R.string.severidades_intro),
    /** Solo lectura: los nombres de quienes las pueden cambiar ("Taiel o Meli"), o vacío si no se conocen. */
    quienesLasCambian: String? = null,
) {
    var verTipos by rememberSaveable { mutableStateOf(false) }
    val soloLectura = quienesLasCambian != null

    Scaffold(
        topBar = { BarraSuperior(titulo = titulo, alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (quienesLasCambian != null) {
                NotaSoloLectura(
                    if (quienesLasCambian.isEmpty()) {
                        stringResource(R.string.severidades_las_cambia_sin_nombres)
                    } else {
                        stringResource(R.string.severidades_las_cambia, quienesLasCambian)
                    },
                )
            } else {
                Text(text = intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TituloDeBloque(stringResource(R.string.tipos_bloque_ia), modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                BotonAyuda(descripcion = stringResource(R.string.tipos_titulo), alTocar = { verTipos = true })
            }
            TipoAtaque.DETECTADOS_POR_LA_IA.forEach { tipo ->
                if (soloLectura) {
                    FilaDeSeveridad(tipo, severidades.severidadDe(tipo))
                } else {
                    TarjetaDeSeveridad(tipo, severidades.severidadDe(tipo)) { alCambiar(tipo, it) }
                }
            }
            TituloDeBloque(stringResource(R.string.tipos_bloque_sin_ia))
            TipoAtaque.NO_DETECTADOS_POR_LA_IA.forEach { tipo ->
                if (soloLectura) {
                    FilaDeSeveridad(tipo, severidades.severidadDe(tipo))
                } else {
                    TarjetaDeSeveridad(tipo, severidades.severidadDe(tipo)) { alCambiar(tipo, it) }
                }
            }
            if (!soloLectura) {
                Text(
                    text = stringResource(R.string.severidades_nota),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BotonPrincipal(texto = textoBoton, alTocar = alGuardar)
            }
        }
    }

    if (verTipos) {
        HojaTipos(severidades = severidades, alCerrar = { verTipos = false })
    }
}

/**
 * El recuadro crema de lo que no se puede cambiar (Figma 468-3438): el candado de 18 y el texto Regular
 * 13 en gris oscuro, con 12 de margen y 10 entre los dos.
 */
@Composable
fun NotaSoloLectura(texto: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(text = texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.zonda.textoNota)
        }
    }
}

/** Un tipo para mirar (Figma 468-3367): el nombre Medium 15 y, a la derecha, la etiqueta de su severidad. */
@Composable
private fun FilaDeSeveridad(tipo: TipoAtaque, severidad: Severidad) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NombreDeTipo(tipo, Modifier.weight(1f))
            IndicadorSeveridad(severidad)
        }
    }
}

/** El nombre del tipo y, en lo que la IA no detectó, qué detector lo marca, Regular 13 gris a 2 (cambio 9). */
@Composable
private fun NombreDeTipo(tipo: TipoAtaque, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = stringResource(tipo.etiqueta()), style = MaterialTheme.typography.titleSmall)
        tipo.detector()?.let { detector ->
            Text(text = stringResource(detector), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Un tipo con sus tres opciones (Figma 66-3212): el nombre Medium 15 y las opciones a 8 entre sí. Lo que
 * la IA no detectó dice abajo del nombre qué detector lo marca, Regular 13 gris a 2 (cambio 9).
 */
@Composable
private fun TarjetaDeSeveridad(tipo: TipoAtaque, elegida: Severidad, alElegir: (Severidad) -> Unit) {
    Panel {
        NombreDeTipo(tipo)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Severidad.entries.forEach { severidad ->
                Opcion(
                    texto = stringResource(severidad.etiqueta()),
                    elegida = elegida == severidad,
                    alElegir = { alElegir(severidad) },
                )
            }
        }
    }
}
