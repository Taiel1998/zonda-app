package ar.edu.uade.zonda.ui.primerinicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import ar.edu.uade.zonda.ui.components.Opcion
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TituloDeBloque
import ar.edu.uade.zonda.ui.etiqueta

/**
 * Severidad de cada tipo de ataque (RF05 y RF06; Figma 66-3212 y 104-2). Viene con los valores
 * recomendados y vale para todos los teléfonos de la red. Va en los dos bloques del cambio 8, lo que
 * detectó la IA y lo que no, con una tarjeta por tipo y sus tres opciones; el ⓘ del primer bloque abre
 * "Qué es cada tipo" con lo elegido en pantalla. La usan el primer inicio y Ajustes.
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
) {
    var verTipos by rememberSaveable { mutableStateOf(false) }

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
            Text(text = intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TituloDeBloque(stringResource(R.string.tipos_bloque_ia), modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                BotonAyuda(descripcion = stringResource(R.string.tipos_titulo), alTocar = { verTipos = true })
            }
            TipoAtaque.DETECTADOS_POR_LA_IA.forEach { tipo ->
                TarjetaDeSeveridad(tipo, severidades.severidadDe(tipo)) { alCambiar(tipo, it) }
            }
            TituloDeBloque(stringResource(R.string.tipos_bloque_sin_ia))
            TipoAtaque.NO_DETECTADOS_POR_LA_IA.forEach { tipo ->
                TarjetaDeSeveridad(tipo, severidades.severidadDe(tipo)) { alCambiar(tipo, it) }
            }
            Text(
                text = stringResource(R.string.severidades_nota),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BotonPrincipal(texto = textoBoton, alTocar = alGuardar)
        }
    }

    if (verTipos) {
        HojaTipos(severidades = severidades, alCerrar = { verTipos = false })
    }
}

/** Un tipo con sus tres opciones (Figma 66-3212): el nombre Medium 15 y las opciones a 8 entre sí. */
@Composable
private fun TarjetaDeSeveridad(tipo: TipoAtaque, elegida: Severidad, alElegir: (Severidad) -> Unit) {
    Panel {
        Text(text = stringResource(tipo.etiqueta()), style = MaterialTheme.typography.titleSmall)
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
