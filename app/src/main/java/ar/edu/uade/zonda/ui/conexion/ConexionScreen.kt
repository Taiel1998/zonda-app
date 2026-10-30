package ar.edu.uade.zonda.ui.conexion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.AccionDeHoja
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.CampoCodigo
import ar.edu.uade.zonda.ui.components.CampoTexto
import ar.edu.uade.zonda.ui.components.FilaDato
import ar.edu.uade.zonda.ui.components.FilaNavegable
import ar.edu.uade.zonda.ui.components.HojaAyuda
import ar.edu.uade.zonda.ui.components.LeyendaObligatorio
import ar.edu.uade.zonda.ui.components.LogoZonda
import ar.edu.uade.zonda.ui.components.NotaAlPie
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TarjetaTono
import ar.edu.uade.zonda.ui.components.TituloSeccion
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.cuerpo
import ar.edu.uade.zonda.ui.theme.encabezado
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.rotulo
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Lo que la persona puede hacer en Conexión. Los valores por defecto no hacen nada (cómodo en las pruebas). */
class AccionesConexion(
    val alCambiarDireccion: (String) -> Unit = {},
    val alCambiarCodigo: (String) -> Unit = {},
    val alPegar: (String?) -> Unit = {},
    val alConectar: () -> Unit = {},
    val alEscanear: () -> Unit = {},
    val alLeerQr: (String) -> Unit = {},
    val alCancelarEscaneo: () -> Unit = {},
    val alIngresarAMano: () -> Unit = {},
    val alReintentar: () -> Unit = {},
    val alContinuar: () -> Unit = {},
    val alVerDetallesTecnicos: () -> Unit = {},
)

/** Las dos ayudas de la pantalla (Figma 66-4286 y 66-4353). */
private enum class Ayuda { RED, CODIGO }

/**
 * Pantalla Conexión (RF05; Figma 35-432 a 50-729): pareo con el QR o a mano, los pasos mientras
 * conecta, la red conectada y los errores con su salida. [alVolver] es null cuando no hay adónde volver.
 */
@Composable
fun ConexionScreen(
    alTerminar: (Red) -> Unit,
    alVolver: (() -> Unit)?,
    alVerDetallesTecnicos: () -> Unit,
    viewModel: ConexionViewModel = viewModelDeZonda {
        ConexionViewModel(it.parearRed, it.sincronizarAlertas, it.nombreDelTelefono, it.alcanceDeLaApp)
    },
) {
    // Los campos de texto necesitan el estado al instante: si llega un cuadro tarde, el campo pisa la
    // letra anterior al escribir rápido. Con Main.immediate el valor nuevo llega antes de la tecla siguiente.
    val estado by viewModel.estado.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val continuar = { viewModel.continuar()?.let(alTerminar) }
    BackHandler(enabled = estado is ConexionUiState.Escaneando) { viewModel.cancelarEscaneo() }
    // Mientras conecta no se puede volver; con la red ya conectada, Volver equivale a Continuar.
    BackHandler(enabled = estado is ConexionUiState.Conectando) { }
    BackHandler(enabled = estado is ConexionUiState.Conectada) { continuar() }
    ConexionContenido(
        estado = estado,
        acciones = AccionesConexion(
            alCambiarDireccion = viewModel::cambiarDireccion,
            alCambiarCodigo = viewModel::cambiarCodigo,
            alPegar = viewModel::pegar,
            alConectar = viewModel::conectarManual,
            alEscanear = viewModel::abrirEscaner,
            alLeerQr = viewModel::leyoQr,
            alCancelarEscaneo = viewModel::cancelarEscaneo,
            alIngresarAMano = viewModel::ingresarAMano,
            alReintentar = viewModel::reintentar,
            alContinuar = { continuar() },
            alVerDetallesTecnicos = alVerDetallesTecnicos,
        ),
        alVolver = alVolver,
    )
}

@Composable
fun ConexionContenido(
    estado: ConexionUiState,
    acciones: AccionesConexion,
    alVolver: (() -> Unit)? = null,
    zona: ZoneId = ZoneId.systemDefault(),
) {
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
            BarraSuperior(
                titulo = stringResource(R.string.conexion_titulo),
                alVolver = alVolver.takeIf { estado is ConexionUiState.Formulario },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .consumeWindowInsets(relleno)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (estado) {
                is ConexionUiState.Formulario -> Formulario(estado, acciones, alAyuda = { ayuda = it })
                is ConexionUiState.Conectando -> Conectando(estado)
                is ConexionUiState.Conectada -> Conectada(estado, acciones)
                is ConexionUiState.Fallo -> Fallo(estado, acciones, zona)
                is ConexionUiState.Escaneando -> Unit
            }
        }
    }

    when (ayuda) {
        Ayuda.RED -> HojaAyuda(
            titulo = stringResource(R.string.ayuda_red_titulo),
            introduccion = stringResource(R.string.ayuda_red_intro),
            pasos = listOf(
                stringResource(R.string.ayuda_red_paso_1),
                stringResource(R.string.ayuda_red_paso_2),
                stringResource(R.string.ayuda_red_paso_3),
            ),
            alCerrar = { ayuda = null },
        )
        Ayuda.CODIGO -> HojaAyuda(
            titulo = stringResource(R.string.conexion_donde_codigo),
            introduccion = stringResource(R.string.ayuda_codigo_intro),
            pasos = listOf(
                stringResource(R.string.ayuda_codigo_paso_1),
                stringResource(R.string.ayuda_codigo_paso_2),
                stringResource(R.string.ayuda_codigo_paso_3),
            ),
            alCerrar = { ayuda = null },
            accion = AccionDeHoja(stringResource(R.string.ayuda_codigo_escanear)) {
                ayuda = null
                acciones.alEscanear()
            },
        )
        null -> Unit
    }
}

/**
 * El formulario (Figma 35-432). Al tocar un campo se oculta la parte de arriba y queda la
 * explicación "Escribí los dos datos que trae el código…" (Figma 49-10 y 49-131).
 */
@Composable
private fun ColumnScope.Formulario(estado: ConexionUiState.Formulario, acciones: AccionesConexion, alAyuda: (Ayuda) -> Unit) {
    val foco = LocalFocusManager.current
    val interaccionDireccion = remember { MutableInteractionSource() }
    val interaccionCodigo = remember { MutableInteractionSource() }
    val direccionEnfocada by interaccionDireccion.collectIsFocusedAsState()
    val codigoEnfocado by interaccionCodigo.collectIsFocusedAsState()
    val escribiendo = direccionEnfocada || codigoEnfocado
    // Con el teclado abierto, Volver primero cierra el teclado y después saca el foco: vuelve la parte de arriba.
    BackHandler(enabled = escribiendo) { foco.clearFocus() }
    val portapapeles = LocalClipboard.current
    val alcance = rememberCoroutineScope()

    if (escribiendo) {
        Text(
            text = stringResource(R.string.conexion_escribi_los_datos),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        EncabezadoDePareo()
        RequisitoDeRed(alVerQueSignifica = { alAyuda(Ayuda.RED) })
        BotonPrincipal(texto = stringResource(R.string.conexion_escanear), alTocar = acciones.alEscanear, icono = Icons.Outlined.QrCodeScanner)
        Divisor()
    }
    CampoTexto(
        valor = estado.direccion,
        alCambiar = acciones.alCambiarDireccion,
        etiqueta = stringResource(R.string.conexion_direccion),
        ejemplo = stringResource(R.string.conexion_direccion_ejemplo),
        ayuda = stringResource(R.string.conexion_direccion_ayuda),
        error = if (estado.errorDireccion) stringResource(R.string.conexion_direccion_error) else null,
        opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        interaccion = interaccionDireccion,
        obligatorio = true,
    )
    CampoCodigo(
        codigo = estado.codigo,
        alCambiar = acciones.alCambiarCodigo,
        etiqueta = stringResource(R.string.conexion_codigo),
        ejemplo = stringResource(if (escribiendo) R.string.conexion_codigo_ejemplo_corto else R.string.conexion_codigo_ejemplo),
        ayuda = stringResource(R.string.conexion_codigo_ayuda),
        textoPegar = stringResource(R.string.conexion_pegar),
        alPegar = {
            alcance.launch {
                val texto = portapapeles.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                acciones.alPegar(texto)
            }
        },
        error = if (estado.errorCodigo) stringResource(R.string.conexion_codigo_error) else null,
        opcionesTeclado = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        accionesTeclado = KeyboardActions(onDone = {
            foco.clearFocus()
            if (estado.puedeConectar) acciones.alConectar()
        }),
        interaccion = interaccionCodigo,
        obligatorio = true,
    )
    LeyendaObligatorio()
    // Con la dirección en foco el Figma no muestra la ayuda del código (49-10); con el código, sí (49-131).
    if (!direccionEnfocada) {
        BotonEnlace(
            texto = stringResource(R.string.conexion_donde_codigo),
            alTocar = { alAyuda(Ayuda.CODIGO) },
            relleno = PaddingValues(start = 4.dp, end = 24.dp),
            modifier = Modifier.height(28.dp),
        )
    }
    BotonPrincipal(
        texto = stringResource(R.string.conexion_conectar),
        alTocar = {
            foco.clearFocus()
            acciones.alConectar()
        },
        habilitado = estado.puedeConectar,
    )
}

/** El logo de 48 y "Necesitás el código de pareo" (Figma 66-4274). */
@Composable
private fun EncabezadoDePareo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LogoZonda(tamanio = 48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.conexion_necesitas), style = MaterialTheme.typography.encabezado)
            Text(text = stringResource(R.string.conexion_te_lo_da), style = MaterialTheme.typography.cuerpo, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Tenés que estar conectado a la red de la empresa" con "ⓘ Qué significa" (Figma 66-4278). */
@Composable
private fun RequisitoDeRed(alVerQueSignifica: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.conexion_en_la_red), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(role = Role.Button, onClick = alVerQueSignifica),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Text(text = stringResource(R.string.conexion_que_significa), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** "o ingresalo a mano" entre dos líneas (Figma 35-453). */
@Composable
private fun Divisor() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(text = stringResource(R.string.conexion_o_a_mano), style = MaterialTheme.typography.rotulo, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** La pantalla muestra la dirección como la escribe la gente, sin `https://`. */
private fun sinEsquema(direccion: String): String = direccion.removePrefix("https://")

/** El panel "Red" con la dirección y, si ya se sabe, el nombre (Figma 49-557). */
@Composable
private fun PanelRed(direccion: String, nombre: String? = null) {
    Panel {
        TituloSeccion(stringResource(R.string.conexion_red))
        if (nombre != null) FilaDato(etiqueta = stringResource(R.string.conexion_nombre), valor = nombre)
        FilaDato(etiqueta = stringResource(R.string.conexion_direccion_corta), valor = sinEsquema(direccion))
    }
}

private enum class EstadoPaso { HECHO, EN_CURSO, PENDIENTE }

private fun estadoDe(actual: PasoPareo, paso: PasoPareo): EstadoPaso = when {
    actual.ordinal > paso.ordinal -> EstadoPaso.HECHO
    actual == paso -> EstadoPaso.EN_CURSO
    else -> EstadoPaso.PENDIENTE
}

/** Conectando (Figma 49-521): los tres pasos, la nota y el botón deshabilitado. */
@Composable
private fun Conectando(estado: ConexionUiState.Conectando) {
    PanelRed(estado.direccion)
    Panel {
        TituloSeccion(stringResource(R.string.conexion_conectando))
        // Buscar el servidor y verificar su certificado son el mismo pedido: terminan juntos.
        FilaPaso(stringResource(R.string.conexion_paso_buscando), estadoDe(estado.paso, PasoPareo.BUSCANDO_SERVIDOR))
        FilaPaso(stringResource(R.string.conexion_paso_verificando), estadoDe(estado.paso, PasoPareo.BUSCANDO_SERVIDOR))
        FilaPaso(stringResource(R.string.conexion_paso_registrando), estadoDe(estado.paso, PasoPareo.REGISTRANDO_TELEFONO))
    }
    Text(text = stringResource(R.string.conexion_tarda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    BotonPrincipal(texto = stringResource(R.string.conexion_conectando), alTocar = {}, habilitado = false)
}

/** Un paso: círculo verde claro de 24 con la tilde o la rueda, y el texto Regular 14. */
@Composable
private fun FilaPaso(texto: String, estado: EstadoPaso) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (estado == EstadoPaso.PENDIENTE) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (estado) {
                EstadoPaso.HECHO -> Icon(Icons.Filled.Check, contentDescription = null, tint = Tonos.Baja.texto, modifier = Modifier.size(14.dp))
                EstadoPaso.EN_CURSO -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
                EstadoPaso.PENDIENTE -> Unit
            }
        }
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = if (estado == EstadoPaso.PENDIENTE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Red conectada (Figma 35-692). Con la API local de pruebas por http no se dice "Conexión segura":
 * la tarjeta pasa a ámbar y lo aclara (en la empresa la conexión siempre es HTTPS).
 */
@Composable
private fun Conectada(estado: ConexionUiState.Conectada, acciones: AccionesConexion) {
    PanelRed(direccion = estado.red.urlBase, nombre = estado.red.nombre)
    val cifrada = estado.red.urlBase.startsWith("https://", ignoreCase = true)
    TarjetaTono(
        tono = if (cifrada) Tonos.Baja else Tonos.Media,
        titulo = stringResource(if (cifrada) R.string.conexion_segura else R.string.conexion_sin_cifrar),
        icono = if (cifrada) Icons.Filled.Lock else Icons.Filled.LockOpen,
        textos = listOf(
            stringResource(R.string.conexion_registrado_como, estado.nombreDelTelefono),
            pluralStringResource(R.plurals.conexion_alertas_para_descargar, estado.alertasParaDescargar, estado.alertasParaDescargar),
        ),
    )
    Panel {
        FilaNavegable(
            titulo = stringResource(R.string.ajustes_detalles_tecnicos),
            detalle = stringResource(R.string.ajustes_detalles_tecnicos_detalle),
            alTocar = acciones.alVerDetallesTecnicos,
        )
    }
    BotonPrincipal(texto = stringResource(R.string.conexion_continuar), alTocar = acciones.alContinuar)
    NotaAlPie(stringResource(R.string.conexion_nota_acceso))
}

/**
 * Los errores (Figma 35-720, 50-679 y 50-729): en rojo si no se pudo conectar o verificar el
 * servidor, en ámbar si el problema es el código. Cada uno con su salida. Una dirección http usa el
 * cuadro del servidor no verificado con su propio texto (el Figma no tiene ese caso).
 */
@Composable
private fun Fallo(estado: ConexionUiState.Fallo, acciones: AccionesConexion, zona: ZoneId) {
    PanelRed(estado.direccion)
    val error = estado.error
    val textos = when (error) {
        ErrorPareo.SIN_SERVIDOR -> listOfNotNull(
            stringResource(R.string.error_pareo_sin_servidor_texto),
            estado.codigoValidoHasta?.let { stringResource(R.string.error_pareo_codigo_valido_hasta, Formato.hora(it, zona)) },
        )
        // Con una dirección http no hubo certificado que mirar: Android no deja usarla (RNF04).
        ErrorPareo.SERVIDOR_NO_VERIFICADO -> if (estado.direccion.startsWith("http://", ignoreCase = true)) {
            listOf(stringResource(R.string.error_pareo_sin_https_texto), stringResource(R.string.error_pareo_sin_https_admin))
        } else {
            listOf(stringResource(R.string.error_pareo_no_verificado_texto), stringResource(R.string.error_pareo_no_verificado_vpn))
        }
        ErrorPareo.CODIGO_VENCIDO -> listOf(stringResource(R.string.error_pareo_vencido_texto))
        ErrorPareo.CODIGO_INVALIDO -> listOf(stringResource(R.string.error_pareo_invalido_texto))
        ErrorPareo.OTRO -> listOf(stringResource(R.string.error_pareo_otro_texto))
    }
    val titulo = when (error) {
        ErrorPareo.SIN_SERVIDOR -> R.string.error_pareo_sin_servidor_titulo
        ErrorPareo.SERVIDOR_NO_VERIFICADO -> R.string.error_pareo_no_verificado_titulo
        ErrorPareo.CODIGO_VENCIDO -> R.string.error_pareo_vencido_titulo
        ErrorPareo.CODIGO_INVALIDO -> R.string.error_pareo_invalido_titulo
        ErrorPareo.OTRO -> R.string.error_pareo_otro_titulo
    }
    TarjetaTono(
        tono = if (error.esDelCodigo) Tonos.Media else Tonos.Alta,
        titulo = stringResource(titulo),
        textos = textos,
    )
    if (error.esDelCodigo) {
        BotonPrincipal(texto = stringResource(R.string.pareo_escanear_otro), alTocar = acciones.alEscanear)
        BotonEnlace(texto = stringResource(R.string.pareo_otro_a_mano), alTocar = acciones.alIngresarAMano, modifier = Modifier.fillMaxWidth())
    } else {
        BotonPrincipal(texto = stringResource(R.string.error_reintentar), alTocar = acciones.alReintentar)
        BotonEnlace(
            texto = stringResource(if (error == ErrorPareo.SIN_SERVIDOR) R.string.pareo_corregir_direccion else R.string.accion_volver),
            alTocar = acciones.alIngresarAMano,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        text = stringResource(R.string.conexion_donde_codigo_pie),
        style = MaterialTheme.typography.opcion,
        color = MaterialTheme.colorScheme.primary,
    )
}
