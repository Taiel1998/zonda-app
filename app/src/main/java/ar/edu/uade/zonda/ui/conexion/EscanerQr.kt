package ar.edu.uade.zonda.ui.conexion

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.abrirAjustesDeLaApp
import ar.edu.uade.zonda.ui.tienePermiso
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Escanear el QR de pareo (Figma, figuras 14 y 15). Pide el permiso de cámara; si no hay cámara o la
 * persona rechaza el permiso, explica cómo seguir: ingresar los datos a mano o abrir los ajustes (RNF06).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.escanear_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alCancelar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { relleno ->
        Box(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            when {
                !tieneCamara -> SinCamara(
                    titulo = stringResource(R.string.escanear_sin_camara_titulo),
                    texto = stringResource(R.string.escanear_sin_camara_texto),
                    alIngresarAMano = alIngresarAMano,
                    alAbrirAjustes = null,
                )
                permiso -> {
                    VistaCamaraQr(alLeer = alLeer, modifier = Modifier.fillMaxSize())
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(if (qrNoValido) R.string.escanear_no_es_zonda else R.string.escanear_apunta),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                rechazado -> SinCamara(
                    titulo = stringResource(R.string.escanear_sin_permiso_titulo),
                    texto = stringResource(R.string.escanear_sin_permiso_texto),
                    alIngresarAMano = alIngresarAMano,
                    alAbrirAjustes = { abrirAjustesDeLaApp(contexto) },
                )
                else -> Unit // esperando la respuesta del diálogo de permiso
            }
        }
    }
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
            MlKitAnalyzer(listOf(lector), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, ejecutor) { resultado ->
                resultado.getValue(lector)?.firstNotNullOfOrNull { it.rawValue }?.let { alLeerActual(it) }
            },
        )
        controlador.bindToLifecycle(cicloDeVida)
        onDispose {
            controlador.unbind()
            lector.close()
        }
    }
    AndroidView(factory = { PreviewView(it).apply { controller = controlador } }, modifier = modifier)
}

@Composable
private fun SinCamara(titulo: String, texto: String, alIngresarAMano: () -> Unit, alAbrirAjustes: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = titulo, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Surface(color = Color(0xFF3A3A3A), contentColor = Color.White, shape = RoundedCornerShape(12.dp)) {
            Text(text = texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = alIngresarAMano, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.escanear_a_mano)) }
        if (alAbrirAjustes != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = alAbrirAjustes, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.abrir_ajustes), color = Color.White)
            }
        }
    }
}
