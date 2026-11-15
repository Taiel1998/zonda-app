package ar.edu.uade.zonda.ui.resumen

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.Franja
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.Proporcion
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.RevisionResumen
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAtaque
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        severidad: Severidad = Severidad.ALTA,
    ) = Instant.parse("${dia}T10:00:00Z").let { inicio ->
        Fixtures.alerta(
            id, severidad = severidad, tipoAtaque = tipo, destinoIp = destinoIp, inicio = inicio,
            creadaEn = inicio.plus(Duration.ofMinutes(5)), revision = revision,
        )
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
    fun `sin Otros, la frase y el chip nombran cada tipo`() {
        // Cambio 14: el tipo que más se repite puede ser uno sin nombre de ataque.
        val falsas = listOf(
            alertaDel("2026-09-25", "f-1", tipo = TipoAtaque.PROBABLE_FALSA_ALARMA, destinoIp = "192.168.10.3"),
            alertaDel("2026-09-25", "f-2", tipo = TipoAtaque.PROBABLE_FALSA_ALARMA),
            alertaDel("2026-09-25", "f-3"),
        )
        val sinTipo = listOf(alertaDel("2026-09-25", "t-1", tipo = TipoAtaque.SIN_TIPIFICAR))

        assertEquals(
            "El viernes concentra 3 alertas: 2 probables falsas alarmas.",
            fraseDelGrafico(ResumenActividad.calcular(falsas, PeriodoResumen.SIETE_DIAS, domingo, utc), recursos),
        )
        assertEquals(
            "El viernes concentra 1 alerta: 1 sin tipo identificado.",
            fraseDelGrafico(ResumenActividad.calcular(sinTipo, PeriodoResumen.SIETE_DIAS, domingo, utc), recursos),
        )
        assertEquals(listOf("volumen inusual"), FiltroResumen(tipos = setOf(TipoAtaque.VOLUMEN_INUSUAL)).grupos(recursos, conFecha = false))
    }

    @Test
    fun `en 30 dias la frase dice la semana de mas actividad`() {
        val lista = listOf(alertaDel("2026-09-15", "s-1"), alertaDel("2026-09-16", "s-2"), alertaDel("2026-09-22", "s-3"))

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.TREINTA_DIAS, domingo, utc)

        assertEquals("La semana del 14/9 fue la de más actividad: 2 alertas.", fraseDelGrafico(resumen, recursos))
    }

    @Test
    fun `con un filtro la frase dice que se esta mostrando y como sacarlo, Figma 100-8096`() {
        val lista = listOf(alertaDel("2026-09-25", "v-1"))
        val viernes = Franja.dia(LocalDate.of(2026, 9, 25))

        val porDia = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, FiltroResumen(barras = setOf(viernes)))
        val porMetodo = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, FiltroResumen(metodo = MetodoDeteccion.IA))

        assertEquals("Mostrando Viernes 25/09: 1 alerta. Tocá de nuevo para sacarlo.", fraseDelGrafico(porDia, recursos))
        assertEquals("Mostrando modelo de IA: 1 alerta. Tocá de nuevo para sacarlo.", fraseDelGrafico(porMetodo, recursos))
        // En la imagen no se puede tocar.
        assertEquals("Mostrando Viernes 25/09: 1 alerta.", fraseDelGrafico(porDia, recursos, enImagen = true))
    }

    @Test
    fun `con varios filtros la frase los nombra, y con mas de cuatro dice cuantos son, Figma 463-1145`() {
        val lista = listOf(alertaDel("2026-09-25", "v-1"), alertaDel("2026-09-26", "s-1", tipo = TipoAtaque.ESCANEO))
        val viernes = Franja.dia(LocalDate.of(2026, 9, 25))
        val sabado = Franja.dia(LocalDate.of(2026, 9, 26))
        fun frase(filtro: FiltroResumen) = fraseDelGrafico(ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, filtro), recursos)

        assertEquals("Mostrando 1 alerta: viernes · fuerza bruta.", frase(FiltroResumen(barras = setOf(viernes), tipos = setOf(TipoAtaque.FUERZA_BRUTA))))
        assertEquals(
            "Mostrando 2 alertas: viernes y sábado · fuerza bruta y escaneo de puertos.",
            frase(FiltroResumen(barras = setOf(sabado, viernes), tipos = setOf(TipoAtaque.ESCANEO, TipoAtaque.FUERZA_BRUTA))),
        )
        assertEquals(
            "Mostrando 0 alertas con 5 filtros.",
            frase(FiltroResumen(barras = setOf(sabado, viernes), tipos = setOf(TipoAtaque.ESCANEO, TipoAtaque.FUERZA_BRUTA), revision = RevisionResumen.REALES)),
        )
    }

    @Test
    fun `la severidad se cuenta debajo de los totales y se nombra en las frases, en el orden de la hoja`() {
        val viernes = Franja.dia(LocalDate.of(2026, 9, 25))
        val lista = listOf(
            alertaDel("2026-09-25", "v-1"),
            alertaDel("2026-09-25", "v-2", severidad = Severidad.MEDIA),
            alertaDel("2026-09-26", "s-1", severidad = Severidad.MEDIA),
        )
        fun resumen(filtro: FiltroResumen) = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, filtro)

        assertEquals("1 alta", cuentaDeSeveridad(Severidad.ALTA, 1, recursos))
        assertEquals("2 medias", cuentaDeSeveridad(Severidad.MEDIA, 2, recursos))
        assertEquals("38 bajas", cuentaDeSeveridad(Severidad.BAJA, 38, recursos))

        // Sola, no dice "Tocá de nuevo": la severidad se elige en la hoja de filtros y en la pantalla no se toca.
        val medias = resumen(FiltroResumen(severidades = setOf(Severidad.MEDIA)))
        assertEquals("Mostrando severidad media: 2 alertas. Se saca desde Filtros.", fraseDelGrafico(medias, recursos))
        assertEquals("Mostrando severidad media: 2 alertas.", fraseDelGrafico(medias, recursos, enImagen = true))
        assertEquals(
            "Estás viendo la severidad media: la precisión cuenta solo las alertas revisadas de esa severidad.",
            recuadroDePrecision(medias, recursos),
        )

        // Con otros filtros va en el orden de la hoja: cuándo, severidad y tipo.
        val varios = FiltroResumen(barras = setOf(viernes), severidades = setOf(Severidad.MEDIA, Severidad.ALTA), tipos = setOf(TipoAtaque.FUERZA_BRUTA))
        assertEquals(listOf("viernes", "severidad alta y media", "fuerza bruta"), varios.grupos(recursos, conFecha = false))
        assertEquals("Mostrando 2 alertas: viernes · severidad alta y media · fuerza bruta.", fraseDelGrafico(resumen(varios), recursos))
        assertEquals("Del Resumen: viernes 25/09 · severidad alta y media · fuerza bruta", lineaDeLaLista(resumen(varios), recursos))
    }

    @Test
    fun `los tipos que la red tiene en alta se nombran uno por uno, como cualquier otra combinacion`() {
        // Antes eran "las altas", un solo filtro con "Tocá de nuevo para sacarlo": ya no hay nada con ese
        // nombre para tocar, y son dos filtros.
        val lista = listOf(alertaDel("2026-09-25", "v-1"))
        val enAlta = setOf(TipoAtaque.FUERZA_BRUTA, TipoAtaque.SIN_TIPIFICAR)
        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, FiltroResumen(tipos = enAlta))

        assertEquals("Mostrando 1 alerta: fuerza bruta y sin tipo identificado.", fraseDelGrafico(resumen, recursos))
        assertEquals(
            "Estás viendo varios filtros juntos: la precisión cuenta solo las alertas revisadas que pasan todos.",
            recuadroDePrecision(resumen, recursos),
        )
    }

    @Test
    fun `los dias y las semanas se nombran como en el Figma`() {
        val viernes = Franja.dia(LocalDate.of(2026, 9, 25))
        val semana = Franja.semana(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 6))

        assertEquals("vie", viernes.etiquetaGrafico())
        assertEquals("viernes", viernes.enFrase(recursos))
        assertEquals("viernes 25/09", viernes.conFecha(recursos))
        assertEquals("vie 25/09", viernes.enHoja(recursos))
        assertEquals("28/8", semana.etiquetaGrafico())
        assertEquals("semana del 28/8", semana.enHoja(recursos))
    }

    @Test
    fun `la lista dice de donde salen sus alertas, Figma 463-1669`() {
        val lista = listOf(alertaDel("2026-09-25", "v-1"))
        val viernes = Franja.dia(LocalDate.of(2026, 9, 25))
        fun linea(filtro: FiltroResumen) =
            lineaDeLaLista(ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc, filtro), recursos)

        assertEquals("Del Resumen: viernes 25/09 · fuerza bruta", linea(FiltroResumen(barras = setOf(viernes), tipos = setOf(TipoAtaque.FUERZA_BRUTA))))
        assertEquals("Del Resumen: del 21/09 al 27/09 · sin revisar", linea(FiltroResumen(revision = RevisionResumen.SIN_REVISAR)))
    }

    @Test
    fun `el texto y el nombre de la imagen llevan solo fechas y cantidades`() {
        val lista = listOf(alertaDel("2026-09-25", "v-1"), alertaDel("2026-09-26", "s-1"))
        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc)

        assertEquals("Resumen_21-09_27-09", nombreDelArchivo(resumen.tramo, recursos))
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
    fun `la barra de un modelo va clara hasta que ese modelo tiene 30 revisiones`() {
        assertTrue(Proporcion(11, 12).barraClara())
        assertTrue(Proporcion(29, 29).barraClara())
        assertFalse(Proporcion(24, 30).barraClara())
    }

    @Test
    fun `el ejemplo de la hoja usa las cifras del primer modelo con dos revisiones o mas`() {
        val real = Fixtures.revision(fecha = domingo)
        val falso = Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, Motivo.BACKUP, fecha = domingo)
        val lista = listOf(alertaDel("2026-09-25", "r-1", revision = real), alertaDel("2026-09-25", "r-2", revision = falso), alertaDel("2026-09-25", "r-3", revision = falso))

        val resumen = ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, domingo, utc)

        assertEquals("Por ejemplo, la IA tiene 1 de 3: marcó 3 alertas que ya revisaste y 1 era un ataque.", ejemploDePrecision(resumen, recursos))
        val sinRevisiones = ResumenActividad.calcular(listOf(alertaDel("2026-09-25", "s-1")), PeriodoResumen.SIETE_DIAS, domingo, utc)
        assertNull(ejemploDePrecision(sinRevisiones, recursos))
    }

    @Test
    fun `el recuadro de la hoja cambia con el filtro elegido`() {
        val lista = listOf(alertaDel("2026-09-25", "v-1", revision = Fixtures.revision(fecha = domingo)))
        fun recuadro(filtro: FiltroResumen, periodo: PeriodoResumen = PeriodoResumen.SIETE_DIAS) =
            recuadroDePrecision(ResumenActividad.calcular(lista, periodo, domingo, utc, filtro), recursos)
        val viernes = Franja.dia(LocalDate.parse("2026-09-25"))

        assertEquals("Sin filtro: la precisión cuenta todas las alertas revisadas de estos 7 días.", recuadro(FiltroResumen()))
        assertEquals("Sin filtro: la precisión cuenta todas las alertas revisadas de estos 30 días.", recuadro(FiltroResumen(), PeriodoResumen.TREINTA_DIAS))
        assertEquals(
            "Estás viendo el modelo de IA: cada fila cuenta las alertas que marcó la IA junto con ese método.",
            recuadro(FiltroResumen(metodo = MetodoDeteccion.IA)),
        )
        assertEquals(
            "Estás viendo el viernes 25/09: la precisión cuenta solo las alertas revisadas de ese día.",
            recuadro(FiltroResumen(barras = setOf(viernes))),
        )
        assertEquals(
            "Estás viendo fuerza bruta: la precisión cuenta solo las alertas revisadas de ese tipo.",
            recuadro(FiltroResumen(tipos = setOf(TipoAtaque.FUERZA_BRUTA))),
        )
        assertEquals(
            "Estás viendo los falsos positivos: ninguna es real, así que cada método da 0 %.",
            recuadro(FiltroResumen(revision = RevisionResumen.FALSOS_POSITIVOS)),
        )
        // Con un modelo elegido manda lo del modelo; con varios filtros de otra clase, que cuenta los que pasan todos.
        assertEquals(
            "Estás viendo el modelo de IA: cada fila cuenta las alertas que marcó la IA junto con ese método.",
            recuadro(FiltroResumen(barras = setOf(viernes), metodo = MetodoDeteccion.IA)),
        )
        assertEquals(
            "Estás viendo varios filtros juntos: la precisión cuenta solo las alertas revisadas que pasan todos.",
            recuadro(FiltroResumen(barras = setOf(viernes), tipos = setOf(TipoAtaque.FUERZA_BRUTA))),
        )
    }
}
