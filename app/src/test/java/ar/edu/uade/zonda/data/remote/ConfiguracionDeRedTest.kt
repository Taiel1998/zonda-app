package ar.edu.uade.zonda.data.remote

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * Lee las configuraciones de red de la app y falla si alguien abre http en release o suma una
 * dirección sin cifrar en debug (RNF04; docs/decisiones.md, decisión 26). Las pruebas corren desde la
 * carpeta del módulo `app`, así que las rutas son las del proyecto.
 */
class ConfiguracionDeRedTest {

    @Test
    fun `el manifiesto principal usa la configuracion de red, asi vale tambien en Android 8`() {
        val aplicacion = leer("src/main/AndroidManifest.xml").getElementsByTagName("application").aLista().single()

        assertEquals("@xml/network_security_config", aplicacion.getAttributeNS(ANDROID, "networkSecurityConfig"))
    }

    @Test
    fun `release no permite texto claro hacia ninguna direccion y confia solo en las autoridades del sistema`() {
        val config = leer("src/main/res/xml/network_security_config.xml")

        val base = config.getElementsByTagName("base-config").aLista().single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        assertTrue("release no tiene excepciones por dirección", config.getElementsByTagName("domain-config").length == 0)
        assertTrue("release no tiene reglas solo para depurar", config.getElementsByTagName("debug-overrides").length == 0)
        assertEquals(listOf("system"), config.getElementsByTagName("certificates").aLista().map { it.getAttribute("src") })
    }

    @Test
    fun `debug permite texto claro solo hacia la computadora de la API local`() {
        val config = leer("src/debug/res/xml/network_security_config.xml")

        assertEquals("false", config.getElementsByTagName("base-config").aLista().single().getAttribute("cleartextTrafficPermitted"))
        val direcciones = config.getElementsByTagName("domain-config").aLista()
            .filter { it.getAttribute("cleartextTrafficPermitted") == "true" }
            .flatMap { it.getElementsByTagName("domain").aLista() }
        assertEquals(setOf("10.0.2.2", "127.0.0.1", "localhost"), direcciones.map { it.textContent.trim() }.toSet())
        assertTrue("sin subdominios", direcciones.all { it.getAttribute("includeSubdomains") == "false" })
        assertEquals(listOf("system"), config.getElementsByTagName("certificates").aLista().map { it.getAttribute("src") })
    }

    private fun leer(ruta: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(ruta))

    private fun NodeList.aLista(): List<Element> = (0 until length).map { item(it) as Element }

    companion object {
        private const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
