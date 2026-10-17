package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.CodigoPersona
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.ColumnaDePantalla
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.theme.Borde
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * "Código para Laura" (decisión 39; Figma 468-2735): el QR, el código en texto y cuánto le queda. La
 * otra persona lo escanea en su primer inicio, como el de `zonda pair`. El QR va siempre negro sobre
 * blanco, también en modo oscuro: la cámara lo lee mejor. Usa el ViewModel de Personas.
 */
@Composable
fun CodigoPersonaScreen(viewModel: PersonasViewModel, alVolver: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val volver = {
        // La persona pudo haberse sumado mientras tanto: la lista se vuelve a pedir.
        viewModel.actualizar()
        alVolver()
    }
    CodigoPersonaContenido(codigo = estado.codigo, alVolver = volver)
}

@Composable
fun CodigoPersonaContenido(codigo: CodigoPersona?, alVolver: () -> Unit, reloj: () -> Instant = Instant::now) {
    var ahora by remember { mutableStateOf(reloj()) }
    LaunchedEffect(codigo) {
        while (true) {
            ahora = reloj()
            delay(1_000)
        }
    }
    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = stringResource(R.string.codigo_titulo, codigo?.nombre.orEmpty()),
                alVolver = alVolver,
            )
        },
    ) { relleno ->
        ColumnaDePantalla(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
            alineacion = Alignment.CenterHorizontally,
        ) {
            if (codigo != null) {
                Text(
                    text = stringResource(R.string.codigo_intro, codigo.nombre, stringResource(codigo.perfil.etiqueta()).lowercase()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CodigoQr(contenido = codigo.contenido, descripcion = stringResource(R.string.codigo_qr_descripcion, codigo.nombre))
                // SemiBold 24 con 2 % entre letras (Figma 468-3005).
                Text(
                    text = codigo.codigo,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.48.sp),
                )
                val queda = Duration.between(ahora, codigo.vence)
                Text(
                    text = if (queda.isNegative || queda.isZero) {
                        stringResource(R.string.codigo_vencido)
                    } else {
                        stringResource(R.string.codigo_vence, "%d:%02d".format(queda.toMinutes(), queda.toSecondsPart()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            BotonContorno(texto = stringResource(R.string.codigo_listo), alTocar = alVolver, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * El QR (Figma 468-2774): 168 de lado dentro de un recuadro blanco de 200 con borde gris y esquinas de
 * 12. Lo dibuja ZXing, módulo por módulo, sin margen propio: el margen es el del recuadro.
 */
@Composable
fun CodigoQr(contenido: String, descripcion: String, modifier: Modifier = Modifier) {
    val matriz = remember(contenido) { matrizQr(contenido) }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Borde),
        modifier = modifier.size(200.dp),
    ) {
        Canvas(
            modifier = Modifier
                .padding(16.dp)
                .size(168.dp)
                .semantics { contentDescription = descripcion },
        ) {
            val lado = size.minDimension / matriz.width
            for (y in 0 until matriz.height) {
                for (x in 0 until matriz.width) {
                    if (matriz[x, y]) drawRect(Color.Black, topLeft = Offset(x * lado, y * lado), size = Size(lado, lado))
                }
            }
        }
    }
}

/** La matriz del QR, un módulo por celda. */
fun matrizQr(contenido: String): BitMatrix =
    QRCodeWriter().encode(
        contenido,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
