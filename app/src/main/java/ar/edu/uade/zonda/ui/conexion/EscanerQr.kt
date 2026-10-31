package ar.edu.uade.zonda.ui.conexion

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.abrirAjustesDeLaApp
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.ColumnaDePantalla
import ar.edu.uade.zonda.ui.theme.FondoEscaner
import ar.edu.uade.zonda.ui.theme.TextoEscaner
import ar.edu.uade.zonda.ui.theme.VerdeAccionAviso
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.tienePermiso
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/** La Activity de un contexto (el de la vista puede venir envuelto). */
private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

/** Lado del visor y de cada esquina verde (Figma 35-474 y 35-681). */
private val LadoVisor = 272.dp
private val LadoEsquina = 40.dp

/**
 * Escanear el QR de pareo (Figma 35-464 y 49-284). Pide el permiso de cámara; si no hay cámara o la
 * persona rechaza el permiso, explica cómo seguir: ingresar los datos a mano o abrir los ajustes (RNF06).
 */
@Composable
fun PantallaEscaner(
    qrNoValido: Boolean,
    alLeer: (String) -> Unit,
    alCancelar: () -> Unit,
    alIngresarAMano: () -> Unit,
) {
    val contexto = LocalContext.current
    val tieneCamara = remember { contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var permiso by remember { mutableStateOf(tienePermiso(contexto, Manifest.permission.CAMERA)) }
    var rechazado by remember { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permiso = concedido
        rechazado = !concedido
    }
    LaunchedEffect(Unit) {
        if (tieneCamara && !permiso) lanzador.launch(Manifest.permission.CAMERA)
    }
    // Al volver de los ajustes del sistema, el permiso pudo haber cambiado.
    LifecycleResumeEffect(Unit) {
        permiso = tienePermiso(contexto, Manifest.permission.CAMERA)
        if (permiso) rechazado = false
        onPauseOrDispose { }
    }
    // Fondo oscuro: los íconos de la barra de estado pasan a claros mientras el escáner está abierto.
    val vista = LocalView.current
    DisposableEffect(vista) {
        val ventana = vista.context.actividad()?.window
        val barras = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        val antes = barras?.isAppearanceLightStatusBars
        barras?.isAppearanceLightStatusBars = false
        onDispose { if (barras != null && antes != null) barras.isAppearanceLightStatusBars = antes }
    }

    Scaffold(
        containerColor = FondoEscaner,
        contentColor = Color.White,
        topBar = {
            BarraSuperior(
                titulo = stringResource(R.string.escanear_titulo),
                alVolver = alCancelar,
                colorFondo = FondoEscaner,
                colorContenido = Color.White,
            )
        },
    ) { relleno ->
        ColumnaDePantalla(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
            alineacion = Alignment.CenterHorizontally,
            alineacionVertical = Alignment.CenterVertically,
        ) {
            when {
                !tieneCamara -> SinCamara(
                    titulo = stringResource(R.string.escanear_sin_camara_titulo),
                    textos = listOf(stringResource(R.string.escanear_sin_camara_texto)),
                    alIngresarAMano = alIngresarAMano,
                    alAbrirAjustes = null,
                )
                permiso -> Visor(qrNoValido = qrNoValido, alLeer = alLeer, alIngresarAMano = alIngresarAMano)
                rechazado -> SinCamara(
                    titulo = stringResource(R.string.escanear_sin_permiso_titulo),
                    textos = listOf(stringResource(R.string.escanear_sin_permiso_texto), stringResource(R.string.escanear_sin_permiso_ajustes)),
                    alIngresarAMano = alIngresarAMano,
                    alAbrirAjustes = { abrirAjustesDeLaApp(contexto) },
                )
                else -> Unit // esperando la respuesta del diálogo de permiso
            }
        }
    }
}

/**
 * La cámara (Figma 35-464): la indicación arriba, el visor de 272 con las esquinas verdes, el aviso
 * con el candado y, abajo de todo, "Ingresar el código a mano".
 */
@Composable
private fun ColumnScope.Visor(qrNoValido: Boolean, alLeer: (String) -> Unit, alIngresarAMano: () -> Unit) {
    Text(
        text = stringResource(if (qrNoValido) R.string.escanear_no_es_zonda else R.string.escanear_apunta),
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        textAlign = TextAlign.Center,
        // Con un QR que no es de Zonda cambia a "Ese QR no es de Zonda...": TalkBack lo anuncia.
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    Box(modifier = Modifier.size(LadoVisor)) {
        val forma = RoundedCornerShape(20.dp)
        VistaCamaraQr(alLeer = alLeer, modifier = Modifier.matchParentSize().clip(forma))
        Box(modifier = Modifier.matchParentSize().border(2.dp, Color.White.copy(alpha = 0.15f), forma))
        // Las cuatro esquinas sobresalen 2 del visor, como en el Figma.
        val lejos: Dp = LadoVisor - LadoEsquina + 2.dp
        Esquina(x = (-2).dp, y = (-2).dp)
        Esquina(x = lejos, y = (-2).dp)
        Esquina(x = (-2).dp, y = lejos)
        Esquina(x = lejos, y = lejos)
    }
    Surface(
        color = Color.White.copy(alpha = 0.10f),
        contentColor = TextoEscaner,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = VerdeAccionAviso, modifier = Modifier.size(16.dp))
            Text(text = stringResource(R.string.escanear_aviso_codigo), style = MaterialTheme.typography.nota, color = TextoEscaner)
        }
    }
    Spacer(modifier = Modifier.weight(1f))
    BotonEnlace(
        texto = stringResource(R.string.escanear_a_mano_abajo),
        alTocar = alIngresarAMano,
        relleno = PaddingValues(8.dp),
        colorTexto = VerdeAccionAviso,
    )
}

/** Una esquina del visor: cuadrado de 40 con borde verde de 4 y esquinas de 6. */
@Composable
private fun Esquina(x: Dp, y: Dp) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(LadoEsquina)
            .border(4.dp, VerdeAccionAviso, RoundedCornerShape(6.dp)),
    )
}

/**
 * La vista de la cámara con el lector de QR de ML Kit. CameraX se encarga del ciclo de vida: la cámara
 * se apaga sola cuando la pantalla se va. Lee varias veces por segundo; el ViewModel se queda con la primera.
 */
@Composable
private fun VistaCamaraQr(alLeer: (String) -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val cicloDeVida = LocalLifecycleOwner.current
    val alLeerActual by rememberUpdatedState(alLeer)
    val controlador = remember { LifecycleCameraController(contexto) }

    DisposableEffect(cicloDeVida) {
        val lector = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val ejecutor = ContextCompat.getMainExecutor(contexto)
        controlador.setImageAnalysisAnalyzer(
            ejecutor,
            MlKitAnalyzer(listOf(lector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, ejecutor) { resultado ->
                resultado.getValue(lector)?.firstNotNullOfOrNull { it.rawValue }?.let { alLeerActual(it) }
            },
        )
        controlador.bindToLifecycle(cicloDeVida)
        onDispose {
            controlador.unbind()
            lector.close()
        }
    }
    AndroidView(
        factory = { PreviewView(it).apply { controller = controlador; scaleType = PreviewView.ScaleType.FILL_CENTER } },
        modifier = modifier,
    )
}

/**
 * Sin cámara o sin permiso (Figma 49-284): el título, la tarjeta con la explicación y las salidas,
 * todo centrado en la pantalla.
 */
@Composable
private fun SinCamara(titulo: String, textos: List<String>, alIngresarAMano: () -> Unit, alAbrirAjustes: (() -> Unit)?) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            textos.forEach { texto ->
                Text(text = texto, style = MaterialTheme.typography.bodySmall, color = TextoEscaner)
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    BotonPrincipal(texto = stringResource(R.string.escanear_a_mano), alTocar = alIngresarAMano)
    if (alAbrirAjustes != null) {
        BotonContorno(
            texto = stringResource(R.string.abrir_ajustes),
            alTocar = alAbrirAjustes,
            relleno = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
            colorTexto = VerdeAccionAviso,
            colorBorde = VerdeAccionAviso.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
