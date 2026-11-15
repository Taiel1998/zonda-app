package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.FilaNavegable
import ar.edu.uade.zonda.ui.components.TarjetaSeccion
import ar.edu.uade.zonda.ui.navigation.AlRetocarPestania
import ar.edu.uade.zonda.ui.navigation.Rutas
import kotlinx.coroutines.launch

/**
 * Administración (como el tablero, 15/11): la pestaña solo del administrador de la red activa, con lo que
 * vale para toda la red: Personas, Navegadores, Equipos, Severidad por tipo de ataque y Registro de
 * cambios. Va bajo el nombre de la red, porque un teléfono puede estar pareado con varias. El analista no
 * la tiene: ve Equipos, las severidades y el registro desde su Ajustes, sin poder cambiarlos. Usa el
 * ViewModel de Ajustes, que ya trae la gente, los equipos y el registro de la red activa.
 */
@Composable
fun AdministracionScreen(
    alAbrirPersonas: () -> Unit,
    alAbrirNavegadores: () -> Unit,
    alAbrirEquipos: () -> Unit,
    alAbrirSeveridades: () -> Unit,
    alAbrirRegistro: () -> Unit,
    viewModel: AjustesViewModel = ajustesViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AdministracionContenido(
        estado = estado,
        acciones = AccionesAdministracion(
            alAbrirPersonas = alAbrirPersonas,
            alAbrirNavegadores = alAbrirNavegadores,
            alAbrirEquipos = alAbrirEquipos,
            alAbrirSeveridades = {
                viewModel.empezarAEditarSeveridades()
                alAbrirSeveridades()
            },
            alAbrirRegistro = alAbrirRegistro,
            alSincronizar = viewModel::sincronizarAhora,
            alAvisoMostrado = viewModel::avisoMostrado,
        ),
    )
}

/** Lo que se puede hacer desde Administración; separado para probar la pantalla sin ViewModel. */
data class AccionesAdministracion(
    val alAbrirPersonas: () -> Unit = {},
    val alAbrirNavegadores: () -> Unit = {},
    val alAbrirEquipos: () -> Unit = {},
    val alAbrirSeveridades: () -> Unit = {},
    val alAbrirRegistro: () -> Unit = {},
    /** "Reintentar" del aviso de un error, como en Ajustes. */
    val alSincronizar: () -> Unit = {},
    val alAvisoMostrado: () -> Unit = {},
)

@Composable
fun AdministracionContenido(estado: AjustesUiState, acciones: AccionesAdministracion) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    // "Severidades guardadas" vuelve acá desde Severidad por tipo de ataque, como en Ajustes.
    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        // Se consume enseguida; se muestra en otro alcance para que consumirlo no lo corte.
        acciones.alAvisoMostrado()
        val (texto, accion) = textoDelAviso(contexto, aviso)
        alcance.launch {
            val resultado = snackbar.showSnackbar(message = texto, actionLabel = accion, duration = SnackbarDuration.Short)
            if (aviso is AvisoAjustes.Error && aviso.error !is ErrorZonda.UltimoAdministrador && resultado == SnackbarResult.ActionPerformed) {
                acciones.alSincronizar()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { BarraSuperior(titulo = stringResource(R.string.administracion_titulo)) },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        val desplazamiento = rememberScrollState()
        AlRetocarPestania(Rutas.ADMINISTRACION) { desplazamiento.animateScrollTo(0) }
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(desplazamiento)
                .padding(MargenAjustes),
            verticalArrangement = EntreTarjetas,
        ) {
            val red = estado.redActiva ?: return@Column
            TarjetaSeccion(titulo = red.nombre) {
                // Un servidor anterior a la API 0.6.0 no tiene gente ni registro: esas filas no aparecen.
                if (estado.personas.isNotEmpty()) {
                    val gente = estado.personas.size - estado.navegadores.size
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_personas),
                        detalle = pluralStringResource(R.plurals.administracion_personas_detalle, gente, gente),
                        alTocar = acciones.alAbrirPersonas,
                    )
                }
                // Las computadoras con el tablero web no son personas: van aparte, como en el tablero.
                if (estado.navegadores.isNotEmpty()) {
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_navegadores),
                        detalle = pluralStringResource(R.plurals.ajustes_navegadores_detalle, estado.navegadores.size, estado.navegadores.size),
                        alTocar = acciones.alAbrirNavegadores,
                    )
                }
                if (estado.equipos.isNotEmpty()) {
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_equipos),
                        detalle = resumenDeEquipos(estado.equipos),
                        alTocar = acciones.alAbrirEquipos,
                    )
                }
                FilaNavegable(
                    titulo = stringResource(R.string.ajustes_severidad_por_tipo),
                    detalle = stringResource(R.string.ajustes_severidad_por_tipo_detalle),
                    alTocar = acciones.alAbrirSeveridades,
                )
                if (estado.personas.isNotEmpty()) {
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_registro),
                        detalle = stringResource(R.string.ajustes_registro_detalle),
                        alTocar = acciones.alAbrirRegistro,
                    )
                }
            }
        }
    }
}
