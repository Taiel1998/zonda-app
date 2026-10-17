package ar.edu.uade.zonda.ui.resumen

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
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
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.theme.GrisCalido
import ar.edu.uade.zonda.ui.theme.HoraElegidaReloj
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloHoja
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cómo se manda el Resumen (cambio 18): la imagen, que es lo que más se manda por WhatsApp, o una hoja A4 en PDF. */
enum class FormatoResumen(val tipo: String, val extension: String) {
    IMAGEN("image/png", "png"),
    PDF("application/pdf", "pdf"),
}

/**
 * El Resumen ya guardado en la caché, como imagen y como PDF, listo para compartir. Los dos salen de la
 * misma captura: el PDF es esa imagen dentro de una hoja A4.
 */
data class ResumenParaCompartir(
    /** "Resumen_21-09_27-09": el nombre sin la extensión. */
    val nombreBase: String,
    val imagen: Uri,
    val pdf: Uri,
    val archivoPdf: File,
    val vistaPrevia: ImageBitmap,
    /** El texto que la acompaña ("Resumen Zonda · del 21/09 al 27/09: 52 alertas…"). */
    val texto: String,
) {
    /** "Resumen_21-09_27-09.png" o ".pdf" (Figma 107-5511 y 463-3243). */
    fun nombre(formato: FormatoResumen): String = "$nombreBase.${formato.extension}"

    fun uri(formato: FormatoResumen): Uri = if (formato == FormatoResumen.IMAGEN) imagen else pdf
}

/** Carpeta de la caché que expone el FileProvider (res/xml/archivos_compartidos.xml); no tiene otra cosa. */
private const val CARPETA = "compartir"

/**
 * Guarda la captura como PNG y como PDF en la caché y devuelve sus direcciones para el FileProvider.
 * La copia a un mapa de bits común es porque el de la captura puede ser de hardware, que no se
 * comprime ni se dibuja en el PDF.
 */
suspend fun prepararParaCompartir(contexto: Context, captura: ImageBitmap, nombreBase: String, texto: String): ResumenParaCompartir =
    withContext(Dispatchers.IO) {
        val mapa = captura.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val (png, pdf) = escribirArchivos(File(contexto.cacheDir, CARPETA), mapa, nombreBase)
        val autoridad = "${contexto.packageName}.archivos"
        ResumenParaCompartir(
            nombreBase = nombreBase,
            imagen = FileProvider.getUriForFile(contexto, autoridad, png),
            pdf = FileProvider.getUriForFile(contexto, autoridad, pdf),
            archivoPdf = pdf,
            vistaPrevia = mapa.asImageBitmap(),
            texto = texto,
        )
    }

/**
 * Escribe en [carpeta] la imagen ("Resumen_21-09_27-09.png") y el PDF (".pdf") de la misma captura,
 * borrando los anteriores. [armarPdf] es [escribirPdf]; las pruebas lo cambian porque PdfDocument
 * necesita el Android de verdad.
 */
fun escribirArchivos(
    carpeta: File,
    mapa: Bitmap,
    nombreBase: String,
    armarPdf: (Bitmap, File) -> Unit = ::escribirPdf,
): Pair<File, File> {
    carpeta.mkdirs()
    carpeta.listFiles()?.forEach { it.delete() }
    val png = File(carpeta, "$nombreBase.${FormatoResumen.IMAGEN.extension}")
    png.outputStream().use { salida -> mapa.compress(Bitmap.CompressFormat.PNG, 100, salida) }
    val pdf = File(carpeta, "$nombreBase.${FormatoResumen.PDF.extension}")
    armarPdf(mapa, pdf)
    return png to pdf
}

/** Una hoja A4 en puntos (1/72 de pulgada), como la mide PdfDocument. */
const val ANCHO_A4 = 595
const val ALTO_A4 = 842

/** El margen de la hoja A4 alrededor de la imagen (Figma 463-3298). */
private const val MARGEN_A4 = 40f

/**
 * Dónde va la imagen en la hoja A4 (Figma 463-3298): lo más grande que entra con 40 de margen, centrada
 * a lo ancho y arriba. La imagen de 380 por 913 queda de 317 de ancho, a 139 del borde.
 */
fun ubicacionEnA4(ancho: Int, alto: Int): RectF {
    val escala = minOf((ANCHO_A4 - 2 * MARGEN_A4) / ancho, (ALTO_A4 - 2 * MARGEN_A4) / alto)
    val anchoEnHoja = ancho * escala
    val izquierda = (ANCHO_A4 - anchoEnHoja) / 2
    return RectF(izquierda, MARGEN_A4, izquierda + anchoEnHoja, MARGEN_A4 + alto * escala)
}

/**
 * El PDF del Resumen (cambio 18): una sola hoja A4 blanca con la imagen, armada con PdfDocument de
 * Android, sin librerías. Es la imagen escalada, así que el texto no se puede seleccionar.
 */
fun escribirPdf(mapa: Bitmap, destino: File) {
    val documento = PdfDocument()
    try {
        val pagina = documento.startPage(PdfDocument.PageInfo.Builder(ANCHO_A4, ALTO_A4, 1).create())
        pagina.canvas.drawColor(android.graphics.Color.WHITE)
        pagina.canvas.drawBitmap(mapa, null, ubicacionEnA4(mapa.width, mapa.height), Paint(Paint.FILTER_BITMAP_FLAG))
        documento.finishPage(pagina)
        destino.outputStream().use { documento.writeTo(it) }
    } finally {
        documento.close()
    }
}

/**
 * "Imprimir" (cambio 18): la impresión de Android con el PDF, que también deja guardarlo como
 * archivo. Devuelve false si no se pudo abrir.
 */
fun imprimir(contexto: Context, resumen: ResumenParaCompartir): Boolean {
    val impresion = contexto.getSystemService(PrintManager::class.java) ?: return false
    val adaptador = object : PrintDocumentAdapter() {
        override fun onLayout(
            anteriores: PrintAttributes?,
            nuevos: PrintAttributes?,
            cancelacion: CancellationSignal?,
            respuesta: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancelacion?.isCanceled == true) {
                respuesta.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(resumen.nombre(FormatoResumen.PDF))
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            respuesta.onLayoutFinished(info, anteriores != nuevos)
        }

        override fun onWrite(
            paginas: Array<out PageRange>?,
            destino: ParcelFileDescriptor,
            cancelacion: CancellationSignal?,
            respuesta: WriteResultCallback,
        ) {
            try {
                FileInputStream(resumen.archivoPdf).use { entrada ->
                    FileOutputStream(destino.fileDescriptor).use { salida -> entrada.copyTo(salida) }
                }
                respuesta.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: IOException) {
                respuesta.onWriteFailed(e.message)
            }
        }
    }
    return try {
        impresion.print(resumen.nombreBase, adaptador, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        true
    } catch (e: IllegalStateException) {
        false
    }
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

/** Las apps instaladas entre WhatsApp, Telegram, Gmail y Drive: las mismas para la imagen y el PDF. */
fun appsDisponibles(contexto: Context): List<DestinoCompartir> =
    DestinoCompartir.APPS.filter { paqueteInstalado(contexto, it) != null }

/** Las apps y, al final, Copiar con la imagen o Imprimir con el PDF (Figma 107-5511 y 463-3243). */
fun destinosPara(formato: FormatoResumen, apps: List<DestinoCompartir>): List<DestinoCompartir> =
    apps + if (formato == FormatoResumen.IMAGEN) DestinoCompartir.COPIAR else DestinoCompartir.IMPRIMIR

/**
 * Manda el Resumen a [destino] como [formato]: abre esa app con el archivo y el texto, copia la imagen
 * al portapapeles o abre la impresión con el PDF. Devuelve false si no se pudo.
 */
fun compartirCon(contexto: Context, destino: DestinoCompartir, resumen: ResumenParaCompartir, formato: FormatoResumen): Boolean {
    if (destino == DestinoCompartir.COPIAR) {
        val portapapeles = contexto.getSystemService(ClipboardManager::class.java) ?: return false
        portapapeles.setPrimaryClip(ClipData.newUri(contexto.contentResolver, resumen.nombre(formato), resumen.uri(formato)))
        return true
    }
    if (destino == DestinoCompartir.IMPRIMIR) return imprimir(contexto, resumen)
    val paquete = paqueteInstalado(contexto, destino) ?: return false
    val uri = resumen.uri(formato)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = formato.tipo
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, resumen.texto)
        clipData = ClipData.newRawUri(resumen.nombre(formato), uri)
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

/**
 * La hoja de compartir del Figma (107-5511 y 463-3243): el título, el selector "Imagen · PDF" (empieza
 * en Imagen), la tarjeta con la vista previa y el archivo que se va a mandar, "Compartir con", las apps
 * en círculos de 52 (las instaladas, con su ícono) con Copiar o Imprimir al final, y Cancelar. 20 a los
 * costados y 16 entre piezas. Sin el tamaño ni "sin IPs ni nombres de equipos" (Taiel: es ruido; lo
 * dice el pie de la imagen y del PDF, que es lo que ve quien lo recibe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaCompartir(
    resumen: ResumenParaCompartir,
    apps: List<DestinoCompartir>,
    alElegir: (DestinoCompartir, FormatoResumen) -> Unit,
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
        ContenidoHojaCompartir(resumen = resumen, apps = apps, alElegir = alElegir, alCancelar = alCancelar)
    }
}

/** El contenido de la hoja, aparte para probarlo sin la hoja modal. */
@Composable
fun ContenidoHojaCompartir(
    resumen: ResumenParaCompartir,
    apps: List<DestinoCompartir>,
    alElegir: (DestinoCompartir, FormatoResumen) -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var formato by rememberSaveable { mutableStateOf(FormatoResumen.IMAGEN) }
    val destinos = destinosPara(formato, apps)
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.resumen_hoja_titulo), style = MaterialTheme.typography.tituloHoja)
        SelectorDeFormato(elegido = formato, alElegir = { formato = it })
        TarjetaDeArchivo(resumen, formato)
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
            destinos.forEach { destino -> BotonDestino(destino = destino, alTocar = { alElegir(destino, formato) }) }
        }
        BotonContorno(texto = stringResource(R.string.accion_cancelar), alTocar = alCancelar, modifier = Modifier.fillMaxWidth())
    }
}

/** El fondo de la tarjeta del archivo en la hoja (Figma 107-5511). */
private val FondoArchivo = Color(0xFFF4F1EA)

/** El borde del selector "Imagen · PDF" (Figma 107-5511). */
private val BordeSelector = Color(0xFF9B998F)

/**
 * "Imagen · PDF" (cambio 18; Figma 107-5511): el SegmentedButton de Material 3 con el borde gris, de
 * 40 de alto y esquinas redondas. El elegido va en verde claro con la tilde y el texto verde.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDeFormato(elegido: FormatoResumen, alElegir: (FormatoResumen) -> Unit) {
    val colores = SegmentedButtonDefaults.colors(
        activeContainerColor = HoraElegidaReloj,
        activeContentColor = MaterialTheme.colorScheme.primary,
        activeBorderColor = BordeSelector,
        inactiveContainerColor = MaterialTheme.colorScheme.surface,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = BordeSelector,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        FormatoResumen.entries.forEachIndexed { indice, formato ->
            SegmentedButton(
                selected = formato == elegido,
                onClick = { alElegir(formato) },
                shape = SegmentedButtonDefaults.itemShape(index = indice, count = FormatoResumen.entries.size),
                colors = colores,
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = stringResource(if (formato == FormatoResumen.IMAGEN) R.string.resumen_hoja_imagen else R.string.resumen_hoja_pdf),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/** La vista previa, el nombre del archivo y debajo "Imagen" o "PDF · 1 página A4" (Figma 107-5511). */
@Composable
private fun TarjetaDeArchivo(resumen: ResumenParaCompartir, formato: FormatoResumen) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(FondoArchivo, forma)
            .padding(12.dp),
    ) {
        Image(
            bitmap = resumen.vistaPrevia,
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
            Text(text = resumen.nombre(formato), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(if (formato == FormatoResumen.IMAGEN) R.string.resumen_hoja_imagen else R.string.resumen_hoja_pdf_detalle),
                style = MaterialTheme.typography.nota,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Un destino: el círculo de 52 con el ícono de la app (o el de copiar o imprimir sobre gris) y el nombre debajo. */
@Composable
private fun BotonDestino(destino: DestinoCompartir, alTocar: () -> Unit) {
    val contexto = LocalContext.current
    val icono = remember(destino) { iconoDeLaApp(contexto, destino) }
    val sinApp = destino == DestinoCompartir.COPIAR || destino == DestinoCompartir.IMPRIMIR
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
                .background(if (sinApp) GrisCalido else MaterialTheme.colorScheme.surface)
                .then(if (sinApp) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)),
        ) {
            when {
                icono != null -> Image(bitmap = icono, contentDescription = null, modifier = Modifier.size(52.dp))
                destino == DestinoCompartir.COPIAR ->
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                destino == DestinoCompartir.IMPRIMIR ->
                    Icon(imageVector = Icons.Outlined.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
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

/** El ícono de la app instalada, o null para Copiar e Imprimir (o si no se pudo leer). */
private fun iconoDeLaApp(contexto: Context, destino: DestinoCompartir): ImageBitmap? {
    val paquete = paqueteInstalado(contexto, destino) ?: return null
    return try {
        val tamanio = (52 * contexto.resources.displayMetrics.density).toInt()
        contexto.packageManager.getApplicationIcon(paquete).toBitmap(tamanio, tamanio).asImageBitmap()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
