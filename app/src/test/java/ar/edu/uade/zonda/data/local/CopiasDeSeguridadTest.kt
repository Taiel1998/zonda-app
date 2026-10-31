package ar.edu.uade.zonda.data.local

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * Lee el manifiesto y las reglas de extracción y falla si algún dato de la app puede salir del teléfono
 * (RNF03): ni en la copia en la nube ni en el pase a un teléfono nuevo de Android 12, que allowBackup no
 * frena. Las pruebas corren desde la carpeta del módulo `app`, así que las rutas son las del proyecto.
 */
class CopiasDeSeguridadTest {

    @Test
    fun `el manifiesto apaga las copias y declara las reglas de Android 12`() {
        val aplicacion = leer("src/main/AndroidManifest.xml").getElementsByTagName("application").aLista().single()

        assertEquals("false", aplicacion.getAttributeNS(ANDROID, "allowBackup"))
        assertEquals("@xml/reglas_de_extraccion", aplicacion.getAttributeNS(ANDROID, "dataExtractionRules"))
    }

    @Test
    fun `ni la copia en la nube ni el pase a un telefono nuevo se llevan datos de la app`() {
        val reglas = leer("src/main/res/xml/reglas_de_extraccion.xml")

        for (seccion in listOf("cloud-backup", "device-transfer")) {
            val elegida = reglas.getElementsByTagName(seccion).aLista().single()
            assertEquals(seccion, 0, elegida.getElementsByTagName("include").length)
            val excluidos = elegida.getElementsByTagName("exclude").aLista().filter { it.getAttribute("path") == "." }
            assertEquals(seccion, DOMINIOS, excluidos.map { it.getAttribute("domain") }.toSet())
        }
    }

    private fun leer(ruta: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(ruta))

    private fun NodeList.aLista(): List<Element> = (0 until length).map { item(it) as Element }

    companion object {
        private const val ANDROID = "http://schemas.android.com/apk/res/android"

        /** Todo lo que guarda la app: la base, los secretos, las preferencias y la caché. */
        private val DOMINIOS = setOf("root", "file", "database", "sharedpref", "external")
    }
}
