package ar.edu.uade.zonda.ui.resumen

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.Tramo
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las frases que arma el Resumen con sus cifras (Figma 52-2629, 100-7024 y 107-4941 a 107-5485). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextosResumenTest {

    private val recursos: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val utc = ZoneOffset.UTC

    // Domingo 27/09/2026, como el ejemplo del Figma.
    private val domingo = Instant.parse("2026-09-27T05:14:00Z")

    private fun alertaDel(
        dia: String,
        id: String,
        tipo: TipoAtaque = TipoAtaque.FUERZA_BRUTA,
        destinoIp: String = "192.168.10.50",
        revision: Revision? = null,
    ) = Instant.parse("${dia}T10:00:00Z").let { inicio ->
        Fixtures.alerta(id, tipoAtaque = tipo, destinoIp = destinoIp, inicio = inicio, creadaEn = inicio.plus(Duration.ofMinutes(5)), revision = revision)
    }

    @Test
    fun `en 7 dias la frase destaca el dia con mas alertas`() {
        val lista = listOf(
            alertaDel("2026-09-25", "v-1"), alertaDel("2026-09-25", "v-2", destinoIp = "192.168.10.3"),
            alertaDel("2026-09-25", "v-3", tipo = TipoAtaque.ESCANEO),
        )

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc)

        assertEquals("El viernes concentra 3 alertas: 2 de fuerza bruta.", fraseDelGrafico(resumen, recursos))
    }

    @Test
    fun `en 30 dias la frase dice la semana de mas actividad`() {
        val lista = listOf(alertaDel("2026-09-15", "s-1"), alertaDel("2026-09-16", "s-2"), alertaDel("2026-09-22", "s-3"))

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.TREINTA_DIAS, domingo, utc)

        assertEquals("La semana del 14/9 fue la de más actividad: 2 alertas.", fraseDelGrafico(resumen, recursos))
    }

    @Test
    fun `con un grupo la frase dice que se esta mostrando, con el dia y el metodo como en el chip`() {
        val lista = listOf(alertaDel("2026-09-25", "v-1"))
        val viernes = LocalDate.of(2026, 9, 25)

        val porDia = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, GrupoResumen.Barra(Tramo(viernes, viernes)))
        val porMetodo = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, GrupoResumen.Metodo(MetodoDeteccion.IA))

        assertEquals("Mostrando Viernes 25/09: 1 alerta. Tocá de nuevo para ver todas.", fraseDelGrafico(porDia, recursos))
        assertEquals("Mostrando modelo de IA: 1 alerta. Tocá de nuevo para ver todas.", fraseDelGrafico(porMetodo, recursos))
        assertEquals("Semana del 28/8", Tramo(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 6)).etiquetaChip(recursos))
    }

    @Test
    fun `el texto y el nombre de la imagen llevan solo fechas y cantidades`() {
        val lista = listOf(alertaDel("2026-09-25", "v-1"), alertaDel("2026-09-26", "s-1"))
        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc)

        assertEquals("Resumen_21-09_27-09.png", nombreDeLaImagen(resumen.tramo, recursos))
        assertEquals("Resumen Zonda · del 21/09 al 27/09: 2 alertas, 0 revisadas, 2 sin revisar.", textoParaCompartir(resumen, recursos))
    }

    @Test
    fun `los avisos al volver de compartir son los del Figma`() {
        assertEquals("Resumen enviado por Telegram.", textoDelAviso(AvisoResumen.Enviado(DestinoCompartir.TELEGRAM), recursos))
        assertEquals("Resumen enviado por WhatsApp.", textoDelAviso(AvisoResumen.Enviado(DestinoCompartir.WHATSAPP), recursos))
        assertEquals("Resumen adjuntado en un correo de Gmail.", textoDelAviso(AvisoResumen.Enviado(DestinoCompartir.GMAIL), recursos))
        assertEquals("Resumen guardado en Drive.", textoDelAviso(AvisoResumen.Enviado(DestinoCompartir.DRIVE), recursos))
        assertEquals("Imagen copiada. Pegala donde quieras.", textoDelAviso(AvisoResumen.Copiado, recursos))
    }

    @Test
    fun `con 30 revisiones o mas la comparacion entre modelos ya no tiene advertencia`() {
        val revisadas = (1..30).map { i -> alertaDel("2026-09-25", "r-$i", revision = Fixtures.revision(fecha = domingo)) }

        val resumen = ResumenActividad.calcular(revisadas, PeriodoResumen.SIETE_DIAS, domingo, utc)

        assertEquals(
            "Precisión = alertas reales / alertas revisadas que marcó cada modelo. El modelo de IA tiene la precisión más alta.",
            notaDePrecision(resumen, recursos),
        )
    }
}
