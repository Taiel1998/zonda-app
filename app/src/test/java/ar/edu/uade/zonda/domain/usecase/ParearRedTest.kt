package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PasoPareo
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParearRedTest {

    private val redes = FakeRedesRepository()
    private val parear = ParearRed(redes)

    @Test
    fun `normaliza la direccion y el codigo, verifica el servidor y despues parea`() = runTest {
        val pasos = mutableListOf<PasoPareo>()

        val pareada = parear(DatosPareo(" zonda-empresa.duckdns.org/ ", "k7m2 qx9p"), "  Pixel 7 ") { pasos += it }

        assertEquals(listOf("https://zonda-empresa.duckdns.org"), redes.verificaciones)
        assertEquals(listOf(DatosPareo("https://zonda-empresa.duckdns.org", "K7M2-QX9P")), redes.pareos)
        // La descarga de las alertas ya no es un paso del pareo: pasa en Estado (Figma 49-521 y 50-616).
        assertEquals(listOf(PasoPareo.BUSCANDO_SERVIDOR, PasoPareo.REGISTRANDO_TELEFONO), pasos)
        assertTrue(pareada.red.activa)
        assertEquals(213, pareada.alertasParaDescargar)
    }

    @Test
    fun `respeta el http escrito a mano para la API local del emulador`() = runTest {
        parear(DatosPareo("http://10.0.2.2:8000", "K7M2-QX9P"), "Emulador")

        assertEquals(listOf("http://10.0.2.2:8000"), redes.verificaciones)
    }

    @Test
    fun `si el servidor no responde o no se verifica, el codigo no se manda`() = runTest {
        redes.errorAlVerificar = ErrorZonda.ServidorNoVerificado()

        val error = runCatching { parear(DatosPareo("zonda.duckdns.org", "K7M2-QX9P"), "Pixel") }.exceptionOrNull()

        assertTrue(error is ErrorZonda.ServidorNoVerificado)
        assertTrue(redes.pareos.isEmpty())
    }

    @Test
    fun `valida los datos antes de tocar la red`() = runTest {
        suspend fun campoConError(datos: DatosPareo, nombre: String = "Pixel"): String =
            (runCatching { parear(datos, nombre) }.exceptionOrNull() as ErrorZonda.Validacion).campo

        assertEquals("urlBase", campoConError(DatosPareo("   ", "K7M2-QX9P")))
        assertEquals("urlBase", campoConError(DatosPareo("zonda empresa.org", "K7M2-QX9P")))
        assertEquals("codigo", campoConError(DatosPareo("zonda.duckdns.org", "K7M2")))
        assertEquals("nombreDispositivo", campoConError(DatosPareo("zonda.duckdns.org", "K7M2-QX9P"), nombre = "  "))
        assertTrue(redes.verificaciones.isEmpty())
    }

    @Test
    fun `la direccion y el codigo se normalizan como los escribe la gente`() {
        assertEquals("https://zonda.duckdns.org", DatosPareo.normalizarDireccion(" zonda.duckdns.org/ "))
        assertEquals("https://zonda.duckdns.org", DatosPareo.normalizarDireccion("https://zonda.duckdns.org"))
        assertEquals("", DatosPareo.normalizarDireccion("   "))
        assertEquals("K7M2-QX9P", DatosPareo.normalizarCodigo("k7m2-qx9p"))
        assertEquals("K7M2-QX9P", DatosPareo.normalizarCodigo("K7M2QX9P"))
        assertEquals("K7M2", DatosPareo.normalizarCodigo(" k7m2 "))
    }

    @Test
    fun `el QR trae la direccion, el codigo y cuando vence`() {
        val datos = DatosPareo.desdeQr("zonda:https://zonda-empresa.duckdns.org#K7M2-QX9P;vence=2026-10-12T02:19:00+00:00")

        assertEquals(DatosPareo("https://zonda-empresa.duckdns.org", "K7M2-QX9P", Instant.parse("2026-10-12T02:19:00Z")), datos)
    }

    @Test
    fun `un QR sin vence, o con la fecha mal escrita, sigue sirviendo y uno que no es de Zonda no`() {
        assertEquals(DatosPareo("https://zonda.duckdns.org", "K7M2-QX9P"), DatosPareo.desdeQr("zonda:https://zonda.duckdns.org#K7M2-QX9P"))
        assertEquals(DatosPareo("https://zonda.duckdns.org", "K7M2-QX9P"), DatosPareo.desdeQr("zonda:https://zonda.duckdns.org/#k7m2-qx9p;vence=ayer"))
        assertNull(DatosPareo.desdeQr("https://zonda.duckdns.org#K7M2-QX9P"))
        assertNull(DatosPareo.desdeQr("zonda:https://zonda.duckdns.org#K7M2;vence=2026-10-12T02:19:00+00:00"))
    }
}
