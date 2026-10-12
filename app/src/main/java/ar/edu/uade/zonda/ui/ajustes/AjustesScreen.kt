package ar.edu.uade.zonda.ui.ajustes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.SelectorDeAvisos
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.primerinicio.TIPOS_EN_PANTALLA
import ar.edu.uade.zonda.ui.primerinicio.TarjetaNotificaciones
import ar.edu.uade.zonda.ui.theme.SeveridadMedia
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.ZoneId

/** Lo que la persona puede hacer en Ajustes. Los valores por defecto no hacen nada (cómodo en las pruebas). */
class AccionesAjustes(
    val alActivarRed: (String) -> Unit = {},
    val alAgregarRed: () -> Unit = {},
    val alConectarDeNuevo: () -> Unit = {},
    val alCambiarAviso: (Severidad, ModoAviso) -> Unit = { _, _ -> },
    val alEditarSeveridades: () -> Unit = {},
    val alSincronizar: () -> Unit = {},
    val alBorrarDatos: () -> Unit = {},
    val alQuitarDispositivo: () -> Unit = {},
    val alQuedarSinRedes: () -> Unit = {},
    val alAvisoMostrado: () -> Unit = {},
)

/**
 * Pantalla Ajustes (RF05 y RF06; informe, 11.10): redes, Telegram (fase 10), avisos por severidad,
 * severidad de cada tipo de ataque, notificaciones, detalles técnicos y datos locales.
 */
@Composable
fun AjustesScreen(
    alAgregarRed: () -> Unit,
    alConectarDeNuevo: () -> Unit,
    alEditarSeveridades: () -> Unit,
    alQuedarSinRedes: () -> Unit,
    viewModel: AjustesViewModel = ajustesViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AjustesContenido(
        estado = estado,
        acciones = AccionesAjustes(
            alActivarRed = viewModel::activarRed,
            alAgregarRed = alAgregarRed,
            alConectarDeNuevo = alConectarDeNuevo,
            alCambiarAviso = viewModel::cambiarAviso,
            alEditarSeveridades = {
                viewModel.empezarAEditarSeveridades()
                alEditarSeveridades()
            },
            alSincronizar = viewModel::sincronizarAhora,
            alBorrarDatos = viewModel::borrarDatosLocales,
            alQuitarDispositivo = viewModel::quitarDispositivo,
            alQuedarSinRedes = alQuedarSinRedes,
            alAvisoMostrado = viewModel::avisoMostrado,
        ),
    )
}

/** El ViewModel de Ajustes, el mismo para la pantalla y para la edición de severidades. */
@Composable
fun ajustesViewModel(dueño: androidx.lifecycle.ViewModelStoreOwner? = null): AjustesViewModel = viewModelDeZonda(dueño) { contenedor ->
    AjustesViewModel(
        redes = contenedor.redes,
        preferencias = contenedor.preferencias,
        sincronizarAlertas = contenedor.sincronizarAlertas,
        cambiarSeveridades = contenedor.cambiarSeveridades,
        quitarDispositivo = contenedor.quitarDispositivo,
        borrarDatosLocales = contenedor.borrarDatosLocales,
        alcanceDeEnvio = contenedor.alcanceDeLaApp,
        nombreDelTelefono = contenedor.nombreDelTelefono,
        versionDeLaApp = contenedor.versionDeLaApp,
    )
}

private enum class Confirmacion { BORRAR_DATOS, QUITAR_DISPOSITIVO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesContenido(estado: AjustesUiState, acciones: AccionesAjustes, zona: ZoneId = ZoneId.systemDefault()) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    var confirmacion by rememberSaveable { mutableStateOf<Confirmacion?>(null) }

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        if (aviso is AvisoAjustes.DispositivoQuitado && !aviso.quedanRedes) {
            // Sin redes no queda nada que mostrar acá: se vuelve a Conexión sin esperar al aviso.
            acciones.alAvisoMostrado()
            acciones.alQuedarSinRedes()
            return@LaunchedEffect
        }
        snackbar.showSnackbar(textoDelAviso(contexto, aviso))
        acciones.alAvisoMostrado()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.destino_ajustes)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Seccion(titulo = stringResource(R.string.ajustes_redes)) {
                if (estado.redes.isEmpty()) {
                    Text(text = stringResource(R.string.ajustes_sin_redes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                estado.redes.forEachIndexed { indice, red ->
                    if (indice > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    FilaRed(red = red, acciones = acciones)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = acciones.alAgregarRed, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ajustes_agregar_red))
                }
            }

            Seccion(titulo = stringResource(R.string.ajustes_telegram)) {
                Text(text = stringResource(R.string.ajustes_telegram_pronto), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Seccion(titulo = stringResource(R.string.avisos_titulo)) {
                Text(text = stringResource(R.string.avisos_texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectorDeAvisos(avisos = estado.avisos, alCambiar = acciones.alCambiarAviso)
            }

            Seccion(titulo = stringResource(R.string.ajustes_severidades)) {
                TIPOS_EN_PANTALLA.forEach { tipo ->
                    FilaDato(stringResource(tipo.etiqueta()), stringResource(estado.severidades.severidadDe(tipo).etiqueta()))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.severidades_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = acciones.alEditarSeveridades, enabled = estado.redActiva != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ajustes_cambiar_severidades))
                }
            }

            TarjetaNotificaciones()

            Seccion(titulo = stringResource(R.string.ajustes_detalles_tecnicos)) {
                val red = estado.redActiva
                if (red != null) {
                    FilaDato(stringResource(R.string.ajustes_red_activa), red.nombre)
                    FilaDato(stringResource(R.string.conexion_direccion_corta), red.urlBase)
                    FilaDato(
                        stringResource(R.string.ajustes_ultima_sincronizacion),
                        red.ultimaSincronizacion?.let { Formato.fechaYHora(it, zona) } ?: stringResource(R.string.alertas_sin_sincronizar),
                    )
                    FilaDato(stringResource(R.string.ajustes_cursor), red.cursor ?: "–")
                }
                FilaDato(stringResource(R.string.ajustes_este_telefono), estado.nombreDelTelefono)
                FilaDato(stringResource(R.string.ajustes_version), estado.versionDeLaApp)
                FilaDato(stringResource(R.string.ajustes_sincronizacion_fondo), stringResource(R.string.ajustes_sincronizacion_fondo_valor))
            }

            Seccion(titulo = stringResource(R.string.ajustes_datos_locales)) {
                if (estado.sincronizando) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.conexion_conectando))
                    }
                } else {
                    FilledTonalButton(onClick = acciones.alSincronizar, enabled = estado.redActiva != null, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.accion_sincronizar))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmacion = Confirmacion.BORRAR_DATOS }, enabled = estado.redes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ajustes_borrar_datos))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { confirmacion = Confirmacion.QUITAR_DISPOSITIVO },
                    enabled = estado.redActiva != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ajustes_quitar_dispositivo))
                }
            }
        }
    }

    confirmacion?.let { actual ->
        val esBorrar = actual == Confirmacion.BORRAR_DATOS
        AlertDialog(
            onDismissRequest = { confirmacion = null },
            title = { Text(stringResource(if (esBorrar) R.string.ajustes_borrar_datos else R.string.ajustes_quitar_dispositivo)) },
            text = {
                Text(
                    if (esBorrar) {
                        stringResource(R.string.ajustes_borrar_datos_confirmar)
                    } else {
                        stringResource(R.string.ajustes_quitar_dispositivo_confirmar, estado.redActiva?.nombre.orEmpty())
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmacion = null
                        if (esBorrar) acciones.alBorrarDatos() else acciones.alQuitarDispositivo()
                    },
                ) {
                    Text(stringResource(if (esBorrar) R.string.ajustes_borrar else R.string.ajustes_quitar))
                }
            },
            dismissButton = { TextButton(onClick = { confirmacion = null }) { Text(stringResource(R.string.revisar_cancelar)) } },
        )
    }
}

@Composable
private fun FilaRed(red: Red, acciones: AccionesAjustes) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = red.nombre, style = MaterialTheme.typography.titleSmall)
            Text(text = red.urlBase.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (red.accesoVencido) {
                Text(text = stringResource(R.string.acceso_vencido_corto), style = MaterialTheme.typography.bodySmall, color = SeveridadMedia)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            red.accesoVencido -> FilledTonalButton(onClick = acciones.alConectarDeNuevo) { Text(stringResource(R.string.acceso_vencido_conectar)) }
            red.activa -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.ajustes_red_activa_etiqueta), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            else -> TextButton(onClick = { acciones.alActivarRed(red.id) }) { Text(stringResource(R.string.ajustes_activar)) }
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            contenido()
        }
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = valor, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun textoDelAviso(contexto: Context, aviso: AvisoAjustes): String = when (aviso) {
    is AvisoAjustes.Sincronizado -> if (aviso.nuevas == 0) {
        contexto.getString(R.string.aviso_sincronizado_sin_novedades)
    } else {
        contexto.resources.getQuantityString(R.plurals.aviso_sincronizado_nuevas, aviso.nuevas, aviso.nuevas)
    }
    AvisoAjustes.SeveridadesGuardadas -> contexto.getString(R.string.ajustes_severidades_guardadas)
    AvisoAjustes.DatosBorrados -> contexto.getString(R.string.ajustes_datos_borrados)
    is AvisoAjustes.DispositivoQuitado -> contexto.getString(R.string.ajustes_dispositivo_quitado)
    is AvisoAjustes.Error -> contexto.getString(aviso.error.mensaje())
}
