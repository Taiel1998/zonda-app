package ar.edu.uade.zonda.ui.primerinicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.etiqueta

/** Los tipos en el orden en que se muestran: de la severidad recomendada más alta a la más baja. */
val TIPOS_EN_PANTALLA = listOf(
    TipoAtaque.FUERZA_BRUTA,
    TipoAtaque.SIN_TIPIFICAR,
    TipoAtaque.ESCANEO,
    TipoAtaque.DDOS,
    TipoAtaque.SOLO_REGLAS_O_UMBRALES,
    TipoAtaque.PROBABLE_FALSA_ALARMA,
)

/**
 * Severidad de cada tipo de ataque (RF05 y RF06; Figma, figura 9). Viene con los valores recomendados
 * y vale para todos los teléfonos de la red. La usa el primer inicio y, en la fase 8, Ajustes.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    if (alVolver != null) {
                        IconButton(onClick = alVolver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                        }
                    }
                },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(text = intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TIPOS_EN_PANTALLA.forEach { tipo ->
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = stringResource(tipo.etiqueta()), style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Severidad.entries.forEach { severidad ->
                                FilterChip(
                                    selected = severidades.severidadDe(tipo) == severidad,
                                    onClick = { alCambiar(tipo, severidad) },
                                    label = { Text(stringResource(severidad.etiqueta())) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = stringResource(R.string.severidades_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = alGuardar, modifier = Modifier.fillMaxWidth()) { Text(textoBoton) }
        }
    }
}
