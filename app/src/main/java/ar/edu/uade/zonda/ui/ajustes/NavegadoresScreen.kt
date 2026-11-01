package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.ColumnaDePantalla
import ar.edu.uade.zonda.ui.components.FilaNavegador
import ar.edu.uade.zonda.ui.components.HojaConfirmacion
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.nombreDeNavegador
import ar.edu.uade.zonda.ui.detalle.ConfirmacionConHuella
import ar.edu.uade.zonda.ui.detalle.LocalConfirmacionDelDueno
import ar.edu.uade.zonda.ui.detalle.actividad
import ar.edu.uade.zonda.ui.detalle.confirmarConHuella
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Ajustes → Navegadores: las computadoras que abren el tablero web, aparte de las personas, como en el
 * tablero. Cada una entra con su propio código, como un teléfono, pero no recibe avisos ni se le asignan
 * alertas. Todos la ven; el administrador le quita el acceso a una con la huella o el PIN, como cuando
 * quita a una persona (decisión 51). Usa el ViewModel de Personas.
 */
@Composable
fun NavegadoresScreen(viewModel: PersonasViewModel, alVolver: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    val confirmacion = LocalConfirmacionDelDueno.current ?: remember(contexto) { contexto.actividad()?.let(::ConfirmacionConHuella) }
    NavegadoresContenido(
        estado = estado,
        alVolver = alVolver,
        alQuitar = { navegador ->
            confirmarConHuella(
                pide = true,
                confirmacion = confirmacion,
                titulo = recursos.getString(R.string.huella_titulo),
                detalle = recursos.getString(R.string.huella_quitar_navegador, nombreDeNavegador(navegador, recursos)),
                alConfirmar = { viewModel.quitar(navegador) },
                alNoPoder = viewModel::sinBloqueoDePantalla,
            )
        },
        alAvisoMostrado = viewModel::avisoMostrado,
    )
}

/**
 * La lista de Navegadores: cada computadora con su perfil y su último uso. El administrador toca una y
 * confirma "Quitar acceso", con los textos del tablero; es lo único que se le hace a un navegador.
 */
@Composable
fun NavegadoresContenido(
    estado: PersonasUiState,
    alVolver: () -> Unit,
    alQuitar: (Persona) -> Unit,
    alAvisoMostrado: () -> Unit = {},
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
) {
    val snackbar = remember { SnackbarHostState() }
    val recursos = LocalResources.current
    val alcance = rememberCoroutineScope()
    var aQuitarId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        alAvisoMostrado()
        val texto = textoDelAvisoDePersonas(aviso, recursos)
        // Consumir el aviso cambia la clave de este efecto: se muestra en otro alcance para no cortarlo.
        alcance.launch { snackbar.showSnackbar(message = texto, actionLabel = recursos.getString(R.string.aviso_ok), duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = { BarraSuperior(titulo = stringResource(R.string.navegadores_titulo), alVolver = alVolver) },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        ColumnaDePantalla(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.navegadores_intro, estado.red?.nombre.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (estado.navegadores.isEmpty()) {
                Text(
                    text = stringResource(R.string.navegadores_vacio),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Panel(espacio = 4.dp) {
                    estado.navegadores.forEach { navegador ->
                        FilaNavegador(
                            navegador = navegador,
                            ahora = ahora,
                            zona = zona,
                            alTocar = if (estado.esAdministrador) ({ aQuitarId = navegador.id }) else null,
                        )
                    }
                }
            }
        }
    }

    estado.navegadores.firstOrNull { it.id == aQuitarId }?.let { navegador ->
        HojaConfirmacion(
            titulo = stringResource(R.string.navegadores_quitar_titulo, nombreDeNavegador(navegador, recursos)),
            texto = stringResource(R.string.navegadores_quitar_texto),
            textoConfirmar = stringResource(R.string.navegadores_quitar_confirmar),
            alConfirmar = {
                aQuitarId = null
                alQuitar(navegador)
            },
            alCerrar = { aQuitarId = null },
        )
    }
}
