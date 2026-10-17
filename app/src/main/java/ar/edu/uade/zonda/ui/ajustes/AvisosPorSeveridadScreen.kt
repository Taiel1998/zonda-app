package ar.edu.uade.zonda.ui.ajustes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import ar.edu.uade.zonda.domain.model.ModoNotificacion
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.PreferenciasNotificaciones
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.components.AvisosDeTelegram
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.ConsejoSonidoDeTelegram
import ar.edu.uade.zonda.ui.components.NotificacionesDeZonda
import ar.edu.uade.zonda.ui.components.TarjetaSeccion
import ar.edu.uade.zonda.ui.EstadoPermiso
import ar.edu.uade.zonda.ui.nombreDelBot
import ar.edu.uade.zonda.ui.rememberPermisoNotificaciones

/**
 * Avisos por severidad (Figma 51-1688): cómo avisa Telegram cada severidad con el consejo del sonido
 * propio (cambio 6), cómo avisa Zonda lo que no llegó por Telegram con el recordatorio de altas (1.0.4)
 * y el atajo a la severidad de cada tipo de ataque. Las dos tarjetas se editan en un borrador y se
 * guardan juntas con "Guardar"; volver sin guardar lo descarta. Al prender una notificación de Zonda
 * sin el permiso del sistema, se pide, como hacía el interruptor de Ajustes hasta la 1.0.3.
 */
@Composable
fun AvisosPorSeveridadScreen(
    viewModel: AjustesViewModel,
    alCambiarSeveridades: () -> Unit,
    alVolver: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val permiso = rememberPermisoNotificaciones()
    AvisosPorSeveridadContenido(
        avisos = estado.borradorAvisos ?: estado.avisos,
        notificaciones = estado.borradorNotificaciones ?: estado.notificaciones,
        bot = nombreDelBot(estado.redActiva),
        alCambiar = viewModel::cambiarAvisoDelBorrador,
        alCambiarNotificacion = viewModel::cambiarNotificacionDelBorrador,
        alCambiarRecordatorio = viewModel::cambiarRecordatorioDelBorrador,
        alActivarNotificacion = { if (permiso.estado != EstadoPermiso.CONCEDIDO) permiso.pedir() },
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
    notificaciones: PreferenciasNotificaciones = PreferenciasNotificaciones(),
    alCambiarNotificacion: (Severidad, ModoNotificacion) -> Unit = { _, _ -> },
    alCambiarRecordatorio: (Boolean) -> Unit = {},
    /** Se prendió una notificación de Zonda o el recordatorio: la pantalla pide el permiso si falta. */
    alActivarNotificacion: () -> Unit = {},
    /** "@zonda_alertas_bot": el chat al que llegan los avisos. */
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
                    text = stringResource(R.string.avisos_texto, bot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AvisosDeTelegram(avisos = avisos, alCambiar = alCambiar)
                ConsejoSonidoDeTelegram()
            }
            // 1.0.4: en el lugar del recuadro gris "Sonido de los avisos" (Figma 51-1688).
            TarjetaSeccion(titulo = stringResource(R.string.notificaciones_zonda_titulo)) {
                Text(
                    text = stringResource(R.string.notificaciones_zonda_texto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NotificacionesDeZonda(
                    notificaciones = notificaciones,
                    alCambiar = alCambiarNotificacion,
                    alCambiarRecordatorio = alCambiarRecordatorio,
                    alActivar = alActivarNotificacion,
                )
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
