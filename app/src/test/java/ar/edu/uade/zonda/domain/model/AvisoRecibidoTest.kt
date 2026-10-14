package ar.edu.uade.zonda.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lo que trajo el enlace del aviso (RF03, criterio 3): solo valores que cierran. */
class AvisoRecibidoTest {

    @Test
    fun `lee la severidad, el servicio y las dos horas del enlace del bot`() {
        val aviso = AvisoRecibido.desdeEnlace("alta", "ssh", "2026-09-27T05:09:12Z", "2026-09-27T05:10:03Z")

        assertEquals(
            AvisoRecibido(Severidad.ALTA, "ssh", Instant.parse("2026-09-27T05:09:12Z"), Instant.parse("2026-09-27T05:10:03Z")),
            aviso,
        )
    }

    @Test
    fun `un enlace con solo el id o con una severidad o un servicio que no cierran no trae nada`() {
        assertNull(AvisoRecibido.desdeEnlace(null, null, null, null))
        assertNull(AvisoRecibido.desdeEnlace("urgente", "ssh", null, null))
        assertNull(AvisoRecibido.desdeEnlace("alta", null, null, null))
        // Nada de texto armado: un servicio con espacios, etiquetas o una dirección se descarta.
        assertNull(AvisoRecibido.desdeEnlace("alta", "<b>ssh</b>", null, null))
        assertNull(AvisoRecibido.desdeEnlace("alta", "192.168.10.50 srv-sueldos", null, null))
    }

    @Test
    fun `una hora mal escrita se pierde sin invalidar lo demas`() {
        val aviso = AvisoRecibido.desdeEnlace("MEDIA", "dce_rpc", "ayer", null)

        assertEquals(AvisoRecibido(Severidad.MEDIA, "dce_rpc", deteccion = null, enviadoEn = null), aviso)
    }
}
