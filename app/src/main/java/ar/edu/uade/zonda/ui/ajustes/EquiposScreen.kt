package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.EtiquetaImportancia
import ar.edu.uade.zonda.ui.components.FilaDeCambio
import ar.edu.uade.zonda.ui.components.FilaRadio
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.components.HojaMotivo
import ar.edu.uade.zonda.ui.components.Opcion
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.descripcion
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloHoja
import ar.edu.uade.zonda.ui.viewModelDeZonda
import kotlinx.coroutines.launch

@Composable
fun equiposViewModel(): EquiposViewModel = viewModelDeZonda { contenedor ->
    EquiposViewModel(contenedor.redes, contenedor.cambiarImportancia, contenedor.sincronizarAlertas, contenedor.alcanceDeLaApp)
}

/**
 * Ajustes → Equipos (decisión 41; Figma 468-3089): cada equipo con nombre, su dirección y su importancia.
 * El administrador toca uno para cambiarla (Figma 468-3178) y después escribe el motivo (468-3463).
 */
@Composable
fun EquiposScreen(alVolver: () -> Unit, viewModel: EquiposViewModel = equiposViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    EquiposContenido(
        estado = estado,
        alVolver = alVolver,
        alElegir = viewModel::elegir,
        alCerrarHoja = viewModel::cerrarHoja,
        alElegirImportancia = viewModel::elegirImportancia,
        alCambiarMotivo = viewModel::cambiarMotivo,
        alCancelarMotivo = viewModel::cancelarMotivo,
        alGuardar = viewModel::guardar,
        alAvisoMostrado = viewModel::avisoMostrado,
    )
}

@Composable
fun EquiposContenido(
    estado: EquiposUiState,
    alVolver: () -> Unit,
    alElegir: (EquipoDeLaRed) -> Unit = {},
    alCerrarHoja: () -> Unit = {},
    alElegirImportancia: (Importancia) -> Unit = {},
    alCambiarMotivo: (String) -> Unit = {},
    alCancelarMotivo: () -> Unit = {},
    alGuardar: () -> Unit = {},
    alAvisoMostrado: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        alAvisoMostrado()
        val texto = when (aviso) {
            is AvisoEquipos.Guardado -> contexto.getString(R.string.equipos_guardado, aviso.equipo)
            is AvisoEquipos.Error -> contexto.getString(aviso.error.mensaje())
        }
        alcance.launch { snackbar.showSnackbar(message = texto, actionLabel = contexto.getString(R.string.aviso_ok), duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = { BarraSuperior(titulo = stringResource(R.string.equipos_titulo), alVolver = alVolver) },
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
            Text(text = stringResource(R.string.equipos_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (estado.equipos.isEmpty()) {
                Text(text = stringResource(R.string.equipos_vacio), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Panel(espacio = 4.dp) {
                    estado.equipos.forEach { equipo ->
                        FilaEquipo(equipo = equipo, alTocar = if (estado.esAdministrador) ({ alElegir(equipo) }) else null)
                    }
                }
            }
        }
    }

    estado.elegido?.let { equipo ->
        HojaInferior(alCerrar = alCerrarHoja) {
            ContenidoHojaImportancia(equipo, alElegirImportancia)
        }
    }
    estado.cambio?.let { cambio ->
        HojaMotivo(
            motivo = estado.motivo,
            alCambiarMotivo = alCambiarMotivo,
            conError = estado.faltaMotivo,
            alCancelar = alCancelarMotivo,
            alGuardar = alGuardar,
        ) {
            FilaDeCambio(
                nombre = cambio.equipo.nombre,
                antes = { EtiquetaImportancia(cambio.equipo.importancia) },
                despues = { EtiquetaImportancia(cambio.nueva) },
            )
        }
    }
}

/** Un equipo (Figma 468-3130): nombre Medium 15 y dirección Regular 12, la importancia y, si se puede tocar, la flecha. */
@Composable
private fun FilaEquipo(equipo: EquipoDeLaRed, alTocar: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = equipo.nombre, style = MaterialTheme.typography.titleSmall)
            Text(text = equipo.ip, style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EtiquetaImportancia(equipo.importancia)
        if (alTocar != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * La hoja de un equipo (Figma 468-3178): su nombre, su dirección y las tres importancias con lo que hace
 * cada una. Se cierra al elegir, como la de los avisos; abajo dice que después pide el motivo.
 */
@Composable
fun ColumnScope.ContenidoHojaImportancia(equipo: EquipoDeLaRed, alElegir: (Importancia) -> Unit) {
    Text(text = equipo.nombre, style = MaterialTheme.typography.tituloHoja)
    Text(text = equipo.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Importancia.entries.forEach { importancia ->
            FilaRadio(
                opcion = Opcion(importancia, stringResource(importancia.etiqueta()), stringResource(importancia.descripcion())),
                elegida = equipo.importancia == importancia,
                alElegir = { alElegir(importancia) },
            )
        }
    }
    Text(text = stringResource(R.string.equipos_nota_motivo), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
