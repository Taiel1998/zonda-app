package ar.edu.uade.zonda.ui.resumen

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La imagen y el PDF del Resumen (cambio 18; Figma 463-3298): los archivos, sus nombres y la hoja A4. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompartirResumenTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `en la hoja A4 la imagen va lo mas grande que entra con 40 de margen, centrada, como el Figma`() {
        val lugar = ubicacionEnA4(ancho = 380, alto = 913)

        // Figma 463-3298: 317 de ancho, a 139 del borde y a 40 de arriba.
        assertEquals(139f, lugar.left, 0.5f)
        assertEquals(40f, lugar.top, 0.01f)
        assertEquals(317f, lugar.width(), 0.5f)
        assertEquals(ALTO_A4 - 40f, lugar.bottom, 0.5f)
        // Una imagen corta y ancha se limita por el ancho.
        val ancha = ubicacionEnA4(ancho = 1000, alto = 400)
        assertEquals(40f, ancha.left, 0.01f)
        assertEquals(ANCHO_A4 - 40f, ancha.right, 0.01f)
    }

    @Test
    fun `escribe la imagen y el PDF de la misma captura con el mismo nombre y borra los anteriores`() {
        val carpeta = File(contexto.cacheDir, "compartir").apply { mkdirs() }
        File(carpeta, "Resumen_viejo.png").writeText("viejo")
        val mapa = Bitmap.createBitmap(380, 913, Bitmap.Config.ARGB_8888)
        val dibujadas = mutableListOf<Pair<Int, Int>>()

        // PdfDocument necesita el Android de verdad (se prueba en el emulador): acá se cambia por uno que anota qué recibió.
        val (png, pdf) = escribirArchivos(carpeta, mapa, "Resumen_21-09_27-09") { recibido, archivo ->
            dibujadas += recibido.width to recibido.height
            archivo.writeText("%PDF-1.4 de prueba")
        }

        assertEquals("Resumen_21-09_27-09.png", png.name)
        assertEquals("Resumen_21-09_27-09.pdf", pdf.name)
        assertEquals(setOf("Resumen_21-09_27-09.png", "Resumen_21-09_27-09.pdf"), carpeta.list()!!.toSet())
        assertEquals(listOf(380 to 913), dibujadas)
        // El PNG empieza con su firma.
        assertEquals(0x89.toByte(), png.readBytes()[0])
    }

    @Test
    fun `el nombre del archivo lleva la extension de cada formato`() {
        val listo = ResumenParaCompartir(
            nombreBase = "Resumen_21-09_27-09",
            imagen = android.net.Uri.parse("content://x/a.png"),
            pdf = android.net.Uri.parse("content://x/a.pdf"),
            archivoPdf = File("a.pdf"),
            vistaPrevia = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap(),
            texto = "",
        )

        assertEquals("Resumen_21-09_27-09.png", listo.nombre(FormatoResumen.IMAGEN))
        assertEquals("Resumen_21-09_27-09.pdf", listo.nombre(FormatoResumen.PDF))
        assertEquals("content://x/a.pdf", listo.uri(FormatoResumen.PDF).toString())
    }

    @Test
    fun `las apps son las mismas con imagen o PDF, y al final Copiar o Imprimir`() {
        val apps = listOf(DestinoCompartir.WHATSAPP, DestinoCompartir.GMAIL)

        assertEquals(apps + DestinoCompartir.COPIAR, destinosPara(FormatoResumen.IMAGEN, apps))
        assertEquals(apps + DestinoCompartir.IMPRIMIR, destinosPara(FormatoResumen.PDF, apps))
        assertEquals("application/pdf", FormatoResumen.PDF.tipo)
    }
}
