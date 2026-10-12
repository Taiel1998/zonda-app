package ar.edu.uade.zonda.ui.conexion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.ui.components.LogoZonda
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.VerdeZondaClaro
import ar.edu.uade.zonda.ui.viewModelDeZonda

/** Lo que la persona puede hacer en Conexión. Los valores por defecto no hacen nada (cómodo en las pruebas). */
class AccionesConexion(
    val alCambiarDireccion: (String) -> Unit = {},
    val alCambiarCodigo: (String) -> Unit = {},
    val alConectar: () -> Unit = {},
    val alEscanear: () -> Unit = {},
    val alLeerQr: (String) -> Unit = {},
    val alCancelarEscaneo: () -> Unit = {},
    val alIngresarAMano: () -> Unit = {},
    val alReintentar: () -> Unit = {},
    val alContinuar: (Red) -> Unit = {},
)

/** Las dos ayudas de la pantalla (informe, 11.10). */
private enum class Ayuda { RED, CODIGO }

/**
 * Pantalla Conexión (RF05; Figma, figuras 14 y 15): pareo con el QR o a mano, los pasos mientras
 * conecta, la red conectada y los errores con su salida. [alVolver] es null cuando no hay adónde volver.
 */
@Composable
fun ConexionScreen(
    alTerminar: (Red) -> Unit,
    alVolver: (() -> Unit)?,
    viewModel: ConexionViewModel = viewModelDeZonda { ConexionViewModel(it.parearRed, it.sincronizarAlertas, it.nombreDelTelefono) },
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    BackHandler(enabled = estado is ConexionUiState.Escaneando) { viewModel.cancelarEscaneo() }
    // Mientras conecta no se puede volver; con la red ya conectada, Volver equivale a Continuar.
    BackHandler(enabled = estado is ConexionUiState.Conectando) { }
    BackHandler(enabled = estado is ConexionUiState.Conectada) {
        (estado as? ConexionUiState.Conectada)?.let { alTerminar(it.red) }
    }
    ConexionContenido(
        estado = estado,
        acciones = AccionesConexion(
            alCambiarDireccion = viewModel::cambiarDireccion,
            alCambiarCodigo = viewModel::cambiarCodigo,
            alConectar = viewModel::conectarManual,
            alEscanear = viewModel::abrirEscaner,
            alLeerQr = viewModel::leyoQr,
            alCancelarEscaneo = viewModel::cancelarEscaneo,
            alIngresarAMano = viewModel::ingresarAMano,
            alReintentar = viewModel::reintentar,
            alContinuar = alTerminar,
        ),
        alVolver = alVolver,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConexionContenido(estado: ConexionUiState, acciones: AccionesConexion, alVolver: (() -> Unit)? = null) {
    if (estado is ConexionUiState.Escaneando) {
        PantallaEscaner(
            qrNoValido = estado.qrNoValido,
            alLeer = acciones.alLeerQr,
            alCancelar = acciones.alCancelarEscaneo,
            alIngresarAMano = acciones.alIngresarAMano,
        )
        return
    }
    var ayuda by rememberSaveable { mutableStateOf<Ayuda?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conexion_titulo)) },
                navigationIcon = {
                    val puedeVolver = estado is ConexionUiState.Formulario || estado is ConexionUiState.Fallo
                    if (alVolver != null && puedeVolver) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (estado) {
                is ConexionUiState.Formulario -> Formulario(estado, acciones, alAyuda = { ayuda = it })
                is ConexionUiState.Conectando -> Conectando(estado)
                is ConexionUiState.Conectada -> Conectada(estado, acciones)
                is ConexionUiState.Fallo -> Fallo(estado, acciones, alAyuda = { ayuda = it })
                is ConexionUiState.Escaneando -> Unit
            }
        }
    }

    ayuda?.let { actual ->
        AlertDialog(
            onDismissRequest = { ayuda = null },
            confirmButton = { TextButton(onClick = { ayuda = null }) { Text(stringResource(R.string.conexion_entendido)) } },
            title = { Text(stringResource(if (actual == Ayuda.RED) R.string.conexion_que_significa else R.string.conexion_donde_codigo)) },
            text = { Text(stringResource(if (actual == Ayuda.RED) R.string.conexion_que_significa_texto else R.string.conexion_donde_codigo_texto)) },
        )
    }
}

@Composable
private fun ColumnScope.Formulario(estado: ConexionUiState.Formulario, acciones: AccionesConexion, alAyuda: (Ayuda) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoZonda(tamanio = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = stringResource(R.string.conexion_necesitas), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.conexion_te_lo_da), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.conexion_en_la_red), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { alAyuda(Ayuda.RED) }) { Text(stringResource(R.string.conexion_que_significa)) }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(onClick = acciones.alEscanear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conexion_escanear)) }
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.conexion_o_a_mano),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = estado.direccion,
        onValueChange = acciones.alCambiarDireccion,
        label = { Text(stringResource(R.string.conexion_direccion)) },
        placeholder = { Text(stringResource(R.string.conexion_direccion_ejemplo)) },
        isError = estado.errorDireccion,
        supportingText = if (estado.errorDireccion) {
            { Text(stringResource(R.string.conexion_direccion_error)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = estado.codigo,
        onValueChange = acciones.alCambiarCodigo,
        label = { Text(stringResource(R.string.conexion_codigo)) },
        placeholder = { Text(stringResource(R.string.conexion_codigo_ejemplo)) },
        isError = estado.errorCodigo,
        supportingText = if (estado.errorCodigo) {
            { Text(stringResource(R.string.conexion_codigo_error)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (estado.puedeConectar) acciones.alConectar() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(onClick = acciones.alConectar, enabled = estado.puedeConectar, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.conexion_conectar))
    }
    TextButton(onClick = { alAyuda(Ayuda.CODIGO) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.conexion_donde_codigo))
    }
}

@Composable
private fun TarjetaRed(direccion: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = stringResource(R.string.conexion_red), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                Text(text = stringResource(R.string.conexion_direccion_corta), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = sinEsquema(direccion), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** La pantalla muestra la dirección como la escribe la gente, sin `https://`. */
private fun sinEsquema(direccion: String): String = direccion.removePrefix("https://")

private enum class EstadoPaso { HECHO, EN_CURSO, PENDIENTE }

private fun estadoDe(actual: PasoPareo, paso: PasoPareo): EstadoPaso = when {
    actual.ordinal > paso.ordinal -> EstadoPaso.HECHO
    actual == paso -> EstadoPaso.EN_CURSO
    else -> EstadoPaso.PENDIENTE
}

@Composable
private fun Conectando(estado: ConexionUiState.Conectando) {
    TarjetaRed(estado.direccion)
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.conexion_conectando), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Buscar el servidor y verificar su certificado son el mismo pedido: terminan juntos.
            FilaPaso(stringResource(R.string.conexion_paso_buscando), estadoDe(estado.paso, PasoPareo.BUSCANDO_SERVIDOR))
            FilaPaso(stringResource(R.string.conexion_paso_verificando), estadoDe(estado.paso, PasoPareo.BUSCANDO_SERVIDOR))
            FilaPaso(stringResource(R.string.conexion_paso_registrando), estadoDe(estado.paso, PasoPareo.REGISTRANDO_TELEFONO))
            FilaPaso(stringResource(R.string.conexion_paso_descargando), estadoDe(estado.paso, PasoPareo.DESCARGANDO_ALERTAS))
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = stringResource(R.string.conexion_tarda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(12.dp))
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conexion_conectando)) }
}

@Composable
private fun FilaPaso(texto: String, estado: EstadoPaso) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (estado) {
                EstadoPaso.HECHO -> Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(VerdeZondaClaro, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                EstadoPaso.EN_CURSO -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                EstadoPaso.PENDIENTE -> Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            color = if (estado == EstadoPaso.PENDIENTE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ColumnScope.Conectada(estado: ConexionUiState.Conectada, acciones: AccionesConexion) {
    Spacer(modifier = Modifier.height(24.dp))
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(VerdeZondaClaro, CircleShape)
            .align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.conexion_conectada_titulo),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(modifier = Modifier.height(16.dp))
    TarjetaRed(estado.red.urlBase)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = estado.red.nombre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
    Spacer(modifier = Modifier.height(4.dp))
    val alertas = estado.alertasDescargadas
    Text(
        text = if (alertas == null) {
            stringResource(R.string.conexion_conectada_sin_descarga)
        } else {
            pluralStringResource(R.plurals.conexion_conectada_alertas, alertas, alertas)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(onClick = { acciones.alContinuar(estado.red) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.conexion_continuar))
    }
}

@Composable
private fun ColumnScope.Fallo(estado: ConexionUiState.Fallo, acciones: AccionesConexion, alAyuda: (Ayuda) -> Unit) {
    val (titulo, texto) = when (estado.error) {
        ErrorPareo.CODIGO_VENCIDO -> R.string.error_pareo_vencido_titulo to R.string.error_pareo_vencido_texto
        ErrorPareo.CODIGO_INVALIDO -> R.string.error_pareo_invalido_titulo to R.string.error_pareo_invalido_texto
        ErrorPareo.SIN_SERVIDOR -> R.string.error_pareo_sin_servidor_titulo to R.string.error_pareo_sin_servidor_texto
        ErrorPareo.SERVIDOR_NO_VERIFICADO -> R.string.error_pareo_no_verificado_titulo to R.string.error_pareo_no_verificado_texto
        ErrorPareo.OTRO -> R.string.error_pareo_otro_titulo to R.string.error_pareo_otro_texto
    }
    TarjetaRed(estado.direccion)
    Spacer(modifier = Modifier.height(12.dp))
    Surface(color = Tonos.Media.fondo, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = stringResource(titulo), style = MaterialTheme.typography.titleSmall, color = Tonos.Media.texto)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = stringResource(texto), style = MaterialTheme.typography.bodyMedium, color = Tonos.Media.texto)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (estado.error.esDelCodigo) {
        Button(onClick = acciones.alEscanear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pareo_escanear_otro)) }
        TextButton(onClick = acciones.alIngresarAMano, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.pareo_otro_a_mano))
        }
    } else {
        Button(onClick = acciones.alReintentar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.error_reintentar)) }
        TextButton(onClick = acciones.alIngresarAMano, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.pareo_revisar_direccion))
        }
    }
    TextButton(onClick = { alAyuda(Ayuda.CODIGO) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.conexion_donde_codigo))
    }
}
