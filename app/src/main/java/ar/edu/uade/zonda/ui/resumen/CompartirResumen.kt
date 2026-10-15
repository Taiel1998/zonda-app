package ar.edu.uade.zonda.ui.resumen

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.theme.GrisCalido
import ar.edu.uade.zonda.ui.theme.tituloHoja
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** La imagen del Resumen ya guardada en la caché, lista para compartir. */
data class ImagenCompartida(
    val nombre: String,
    val uri: Uri,
    val bytes: Long,
    val vistaPrevia: ImageBitmap,
    /** El texto que la acompaña ("Resumen Zonda · del 21/09 al 27/09: 52 alertas…"). */
    val texto: String,
)

/** Carpeta de la caché que expone el FileProvider (res/xml/archivos_compartidos.xml); no tiene otra cosa. */
private const val CARPETA = "compartir"

/**
 * Guarda la imagen como PNG en la caché, borrando las anteriores, y devuelve su dirección para el
 * FileProvider. La copia a un mapa de bits común es porque el de la captura puede ser de hardware,
 * que no se comprime.
 */
suspend fun guardarImagen(contexto: Context, captura: ImageBitmap, nombre: String, texto: String): ImagenCompartida =
    withContext(Dispatchers.IO) {
        val carpeta = File(contexto.cacheDir, CARPETA).apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }
        val archivo = File(carpeta, nombre)
        val mapa = captura.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        archivo.outputStream().use { salida -> mapa.compress(Bitmap.CompressFormat.PNG, 100, salida) }
        ImagenCompartida(
            nombre = nombre,
            uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo),
            bytes = archivo.length(),
            vistaPrevia = mapa.asImageBitmap(),
            texto = texto,
        )
    }

/** El primer paquete instalado de [destino]; null si no hay ninguno. */
private fun paqueteInstalado(contexto: Context, destino: DestinoCompartir): String? =
    destino.paquetes.firstOrNull { paquete ->
        try {
            contexto.packageManager.getPackageInfo(paquete, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

/** Los destinos que se pueden ofrecer en este teléfono: las apps instaladas y siempre Copiar. */
fun destinosDisponibles(contexto: Context): List<DestinoCompartir> =
    DestinoCompartir.entries.filter { it == DestinoCompartir.COPIAR || paqueteInstalado(contexto, it) != null }

/**
 * Manda la imagen a [destino]: abre esa app con la imagen y el texto, o la copia al portapapeles.
 * Devuelve false si no se pudo.
 */
fun compartirCon(contexto: Context, destino: DestinoCompartir, imagen: ImagenCompartida): Boolean {
    if (destino == DestinoCompartir.COPIAR) {
        val portapapeles = contexto.getSystemService(ClipboardManager::class.java) ?: return false
        portapapeles.setPrimaryClip(ClipData.newUri(contexto.contentResolver, imagen.nombre, imagen.uri))
        return true
    }
    val paquete = paqueteInstalado(contexto, destino) ?: return false
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imagen.uri)
        putExtra(Intent.EXTRA_TEXT, imagen.texto)
        clipData = ClipData.newRawUri(imagen.nombre, imagen.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(paquete)
    }
    // Algunas apps tienen más de un destino para una imagen (Gmail: el correo y el chat); sin elegir,
    // Android preguntaría cuál. Va el que se llama como la app, que es el principal.
    actividadPrincipal(contexto, envio, paquete)?.let { envio.setClassName(paquete, it) }
    return try {
        contexto.startActivity(envio)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** La actividad de [paquete] que recibe [envio] y se llama como la app; si no hay, la primera. */
private fun actividadPrincipal(contexto: Context, envio: Intent, paquete: String): String? {
    val gestor = contexto.packageManager
    val candidatas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gestor.queryIntentActivities(envio, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        gestor.queryIntentActivities(envio, PackageManager.MATCH_DEFAULT_ONLY)
    }
    val nombreApp = try {
        gestor.getApplicationLabel(gestor.getApplicationInfo(paquete, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
    val elegida = candidatas.firstOrNull { it.loadLabel(gestor).toString() == nombreApp } ?: candidatas.firstOrNull()
    return elegida?.activityInfo?.name
}

/** "48 KB", como en la hoja del Figma. */
fun tamanioDeArchivo(bytes: Long): String {
    val kb = ceil(bytes / 1024.0).toLong().coerceAtLeast(1)
    return if (kb < 1024) "$kb KB" else String.format(Locale.forLanguageTag("es-AR"), "%.1f MB", kb / 1024.0)
}

/**
 * La hoja de compartir del Figma (107-5511): el título, la tarjeta con la vista previa, el nombre y
 * "Imagen · 48 KB · sin IPs ni nombres de equipos", "Compartir con", las apps en círculos de 52 (las
 * instaladas, con su ícono, y Copiar) y Cancelar. 20 a los costados y 14 entre piezas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaCompartir(
    imagen: ImagenCompartida,
    destinos: List<DestinoCompartir>,
    alElegir: (DestinoCompartir) -> Unit,
    alCancelar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = alCancelar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        ContenidoHojaCompartir(imagen = imagen, destinos = destinos, alElegir = alElegir, alCancelar = alCancelar)
    }
}

/** El contenido de la hoja, aparte para probarlo sin la hoja modal. */
@Composable
fun ContenidoHojaCompartir(
    imagen: ImagenCompartida,
    destinos: List<DestinoCompartir>,
    alElegir: (DestinoCompartir) -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = stringResource(R.string.resumen_hoja_titulo), style = MaterialTheme.typography.tituloHoja)
        TarjetaDeArchivo(imagen)
        Text(
            text = stringResource(R.string.resumen_hoja_compartir_con),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = if (destinos.size >= 4) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
        ) {
            destinos.forEach { destino -> BotonDestino(destino = destino, alTocar = { alElegir(destino) }) }
        }
        BotonContorno(texto = stringResource(R.string.accion_cancelar), alTocar = alCancelar, modifier = Modifier.fillMaxWidth())
    }
}

/** El fondo de la tarjeta del archivo en la hoja (Figma 107-5511). */
private val FondoArchivo = Color(0xFFF4F1EA)

@Composable
private fun TarjetaDeArchivo(imagen: ImagenCompartida) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(FondoArchivo, forma)
            .padding(12.dp),
    ) {
        Image(
            bitmap = imagen.vistaPrevia,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = imagen.nombre, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.resumen_hoja_archivo, tamanioDeArchivo(imagen.bytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Un destino: el círculo de 52 con el ícono de la app (o el de copiar sobre gris) y el nombre debajo. */
@Composable
private fun BotonDestino(destino: DestinoCompartir, alTocar: () -> Unit) {
    val contexto = LocalContext.current
    val icono = remember(destino) { iconoDeLaApp(contexto, destino) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = alTocar),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (destino == DestinoCompartir.COPIAR) GrisCalido else MaterialTheme.colorScheme.surface)
                .then(if (destino == DestinoCompartir.COPIAR) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)),
        ) {
            when {
                icono != null -> Image(bitmap = icono, contentDescription = null, modifier = Modifier.size(52.dp))
                destino == DestinoCompartir.COPIAR ->
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                else -> Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(destino.nombre),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** El ícono de la app instalada, o null para Copiar (o si no se pudo leer). */
private fun iconoDeLaApp(contexto: Context, destino: DestinoCompartir): ImageBitmap? {
    val paquete = paqueteInstalado(contexto, destino) ?: return null
    return try {
        val tamanio = (52 * contexto.resources.displayMetrics.density).toInt()
        contexto.packageManager.getApplicationIcon(paquete).toBitmap(tamanio, tamanio).asImageBitmap()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
