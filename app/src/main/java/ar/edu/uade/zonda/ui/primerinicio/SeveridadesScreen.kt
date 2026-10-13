package ar.edu.uade.zonda.ui.primerinicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.NotaAlPie
import ar.edu.uade.zonda.ui.components.Opcion
import ar.edu.uade.zonda.ui.components.Panel
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
 * Severidad de cada tipo de ataque (RF05 y RF06; Figma 66-3212). Viene con los valores recomendados
 * y vale para todos los teléfonos de la red: una tarjeta por tipo con sus tres opciones. La usa el
 * primer inicio y Ajustes.
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
            Text(text = intro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TIPOS_EN_PANTALLA.forEach { tipo ->
                Panel {
                    Text(text = stringResource(tipo.etiqueta()), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Severidad.entries.forEach { severidad ->
                            Opcion(
                                texto = stringResource(severidad.etiqueta()),
                                elegida = severidades.severidadDe(tipo) == severidad,
                                alElegir = { alCambiar(tipo, severidad) },
                            )
                        }
                    }
                }
            }
            NotaAlPie(texto = stringResource(R.string.severidades_nota))
            BotonPrincipal(texto = textoBoton, alTocar = alGuardar)
        }
    }
}
