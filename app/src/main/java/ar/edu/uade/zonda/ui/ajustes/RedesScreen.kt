package ar.edu.uade.zonda.ui.ajustes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.NotaAlPie
import ar.edu.uade.zonda.ui.theme.Tono
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.nota
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Redes (RF06; Figma 51-1583 y 66-4123): cada red pareada con su estado, la activa elegida y un menú
 * para quitar este dispositivo de esa red. Comparte el ViewModel con Ajustes.
 */
@Composable
fun RedesScreen(
    viewModel: AjustesViewModel,
    alAgregarRed: () -> Unit,
    alConectarDeNuevo: () -> Unit,
    alQuedarSinRedes: () -> Unit,
    alVolver: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    RedesContenido(
        estado = estado,
        alActivar = viewModel::activarRed,
        alQuitar = { red -> viewModel.quitarDispositivo(red.id) },
        alConectarDeNuevo = alConectarDeNuevo,
        alAgregarRed = alAgregarRed,
        alQuedarSinRedes = alQuedarSinRedes,
        alAvisoMostrado = viewModel::avisoMostrado,
        alVolver = alVolver,
    )
}

@Composable
fun RedesContenido(
    estado: AjustesUiState,
    alActivar: (String) -> Unit,
    alQuitar: (Red) -> Unit,
    alConectarDeNuevo: () -> Unit,
    alAgregarRed: () -> Unit,
    alQuedarSinRedes: () -> Unit,
    alAvisoMostrado: () -> Unit,
    alVolver: () -> Unit,
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    var aQuitar by rememberSaveable { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    BackHandler(onBack = alVolver)

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        if (aviso !is AvisoAjustes.RedActivada && aviso !is AvisoAjustes.DispositivoQuitado) return@LaunchedEffect
        alAvisoMostrado()
        if (aviso is AvisoAjustes.DispositivoQuitado && !aviso.quedanRedes) {
            alQuedarSinRedes()
            return@LaunchedEffect
        }
        val (texto, accion) = textoDelAviso(contexto, aviso)
        // Consumir el aviso cambia la clave de este efecto: se muestra en otro alcance para no cortarlo.
        alcance.launch { snackbar.showSnackbar(message = texto, actionLabel = accion, duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = { BarraSuperior(titulo = stringResource(R.string.redes_titulo), alVolver = alVolver) },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MargenAjustes),
            verticalArrangement = EntreTarjetas,
        ) {
            Text(
                text = stringResource(R.string.redes_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            estado.redes.forEach { conEstado ->
                TarjetaRed(
                    conEstado = conEstado,
                    activa = conEstado.red.id == estado.redActiva?.id,
                    alActivar = { alActivar(conEstado.red.id) },
                    alQuitar = { aQuitar = conEstado.red.id },
                    alConectarDeNuevo = alConectarDeNuevo,
                    zona = zona,
                )
            }
            NotaAlPie(texto = stringResource(R.string.redes_nota))
            // En el Figma hay 24 más entre la nota y el botón.
            Spacer(modifier = Modifier.height(24.dp))
            BotonPrincipal(texto = stringResource(R.string.redes_agregar), alTocar = alAgregarRed, icono = Icons.Filled.QrCodeScanner)
            NotaAlPie(
                texto = stringResource(R.string.redes_agregar_nota),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val red = estado.redes.firstOrNull { it.red.id == aQuitar }
    if (red != null) {
        HojaQuitarDispositivo(
            red = red.red,
            nombreDelTelefono = estado.nombreDelTelefono,
            alertasGuardadas = red.alertasGuardadas,
            alConfirmar = {
                aQuitar = null
                alQuitar(red.red)
            },
            alCerrar = { aQuitar = null },
        )
    }
}

/**
 * Una red (Figma 51-1583): botón de radio, nombre, dirección y estado con su punto de color, y el
 * menú de tres puntos. La activa va en verde claro con borde verde.
 */
@Composable
private fun TarjetaRed(
    conEstado: RedConEstado,
    activa: Boolean,
    alActivar: () -> Unit,
    alQuitar: () -> Unit,
    alConectarDeNuevo: () -> Unit,
    zona: ZoneId,
) {
    val red = conEstado.red
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = alActivar,
        shape = RoundedCornerShape(12.dp),
        color = if (activa) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 12 de margen más el borde; el menú llega hasta el borde para tener 48 dp de área táctil.
        Row(modifier = Modifier.padding(start = 13.dp, top = 13.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = activa,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = red.nombre, style = MaterialTheme.typography.titleSmall)
                Text(text = direccion(red), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val (tono, texto) = estadoDeLaRed(conEstado, zona)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(tono.punto, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = texto, style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.redes_menu, red.nombre),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (red.accesoVencido) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.acceso_vencido_conectar), style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                menu = false
                                alConectarDeNuevo()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.ajustes_quitar_dispositivo),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menu = false
                            alQuitar()
                        },
                    )
                }
            }
        }
    }
}

/** El estado de una red con su color: acceso vencido, sin datos, altas, medias o tranquila. */
@Composable
private fun estadoDeLaRed(conEstado: RedConEstado, zona: ZoneId): Pair<Tono, String> {
    val red = conEstado.red
    val ultima = red.ultimaSincronizacion
    val c = conEstado.contadores
    return when {
        red.accesoVencido -> Tonos.Alta to stringResource(R.string.acceso_vencido_corto)
        ultima == null -> Tonos.Neutro to stringResource(R.string.redes_sin_datos)
        c.altas > 0 -> Tonos.Alta to pluralStringResource(R.plurals.redes_altas, c.altas, c.altas, Formato.hora(ultima, zona))
        c.medias > 0 -> Tonos.Media to pluralStringResource(R.plurals.redes_medias, c.medias, c.medias, Formato.hora(ultima, zona))
        else -> Tonos.Baja to stringResource(R.string.redes_tranquila, Formato.hora(ultima, zona))
    }
}
