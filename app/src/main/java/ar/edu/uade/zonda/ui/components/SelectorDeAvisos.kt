package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.etiqueta

/** Cómo avisa cada severidad: con sonido, vibración o sin aviso (RF05). Lo usan el primer inicio y Ajustes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorDeAvisos(avisos: PreferenciasAvisos, alCambiar: (Severidad, ModoAviso) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Severidad.entries.forEach { severidad ->
            Spacer(modifier = Modifier.height(12.dp))
            IndicadorSeveridad(severidad)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModoAviso.entries.forEach { modo ->
                    FilterChip(
                        selected = avisos.para(severidad) == modo,
                        onClick = { alCambiar(severidad, modo) },
                        label = { Text(stringResource(modo.etiqueta())) },
                    )
                }
            }
        }
    }
}
