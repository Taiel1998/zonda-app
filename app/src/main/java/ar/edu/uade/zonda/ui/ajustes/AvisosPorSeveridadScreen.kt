package ar.edu.uade.zonda.ui.ajustes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.SelectorDeAvisos
import ar.edu.uade.zonda.ui.components.TarjetaSeccion
import ar.edu.uade.zonda.ui.nombreDelBot

/**
 * Avisos por severidad (Figma 51-1688): cómo avisa Telegram cada severidad, de dónde sale el sonido de
 * los avisos y el atajo a la severidad de cada tipo de ataque. Se edita en un borrador y se guarda con
 * "Guardar"; volver sin guardar lo descarta.
 */
@Composable
fun AvisosPorSeveridadScreen(
    viewModel: AjustesViewModel,
    alCambiarSeveridades: () -> Unit,
    alVolver: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AvisosPorSeveridadContenido(
        avisos = estado.borradorAvisos ?: estado.avisos,
        bot = nombreDelBot(estado.redActiva),
        alCambiar = viewModel::cambiarAvisoDelBorrador,
        alCambiarSeveridades = {
            viewModel.empezarAEditarSeveridades()
            alCambiarSeveridades()
        },
        alGuardar = {
            viewModel.guardarAvisos()
            alVolver()
        },
        alVolver = {
            viewModel.descartarBorradorAvisos()
            alVolver()
        },
    )
}

@Composable
fun AvisosPorSeveridadContenido(
    avisos: PreferenciasAvisos,
    alCambiar: (Severidad, ModoAviso) -> Unit,
    /** "@zonda_alertas_bot": el chat donde se cambia el sonido de Telegram. */
    bot: String = stringResource(R.string.telegram_bot),
    alCambiarSeveridades: () -> Unit,
    alGuardar: () -> Unit,
    alVolver: () -> Unit,
) {
    BackHandler(onBack = alVolver)
    Scaffold(
        topBar = { BarraSuperior(titulo = stringResource(R.string.ajustes_avisos_por_severidad), alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MargenAjustes),
            verticalArrangement = EntreTarjetas,
        ) {
            TarjetaSeccion(titulo = stringResource(R.string.avisos_titulo)) {
                Text(
                    text = stringResource(R.string.avisos_texto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectorDeAvisos(avisos = avisos, alCambiar = alCambiar)
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.avisos_sonido_titulo),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.avisos_sonido_telegram, bot),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.avisos_sonido_zonda),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BotonEnlace(
                texto = stringResource(R.string.avisos_cambiar_severidad),
                alTocar = alCambiarSeveridades,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            BotonPrincipal(texto = stringResource(R.string.accion_guardar), alTocar = alGuardar)
        }
    }
}
