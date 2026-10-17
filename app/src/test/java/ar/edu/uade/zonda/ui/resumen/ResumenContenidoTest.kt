package ar.edu.uade.zonda.ui.resumen

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.Franja
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.RangoResumen
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.RevisionResumen
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.ToqueResumen
import ar.edu.uade.zonda.ui.theme.Texto
import ar.edu.uade.zonda.ui.theme.TextoSecundario
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El Resumen dibujado con Compose sobre Robolectric, sin emulador (Figma 52-2629 a 107-5511). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResumenContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    /** Lunes 12/10/2026 al mediodía: el período de 7 días va del martes 6 al lunes 12. */
    private val ahora = Fixtures.AHORA

    private fun alertas(): List<Alerta> {
        val hace5h = ahora.minus(Duration.ofHours(5))
        return listOf(
            Fixtures.alerta(
                "a-1", tipoAtaque = TipoAtaque.FUERZA_BRUTA, creadaEn = hace5h,
                revision = Fixtures.revision(fecha = hace5h.plus(Duration.ofMinutes(14))),
                metodos = setOf(MetodoDeteccion.IA, MetodoDeteccion.UMBRALES),
            ),
            Fixtures.alerta("a-2", tipoAtaque = TipoAtaque.FUERZA_BRUTA, creadaEn = hace5h),
            Fixtures.alerta(
                "a-3", severidad = Severidad.MEDIA, tipoAtaque = TipoAtaque.ESCANEO, creadaEn = ahora.minus(Duration.ofDays(3)),
                revision = Fixtures.revision(ResultadoRevision.FALSO_POSITIVO, Motivo.BACKUP, fecha = ahora.minus(Duration.ofDays(3)).plus(Duration.ofMinutes(14))),
            ),
            Fixtures.alerta("a-4", severidad = Severidad.BAJA, tipoAtaque = TipoAtaque.PROBABLE_FALSA_ALARMA, creadaEn = ahora.minus(Duration.ofDays(3))),
        )
    }

    private fun resumen(filtro: FiltroResumen = FiltroResumen(), lista: List<Alerta> = alertas()) =
        ResumenActividad.calcular(lista, PeriodoResumen.SIETE_DIAS, ahora, utc, filtro)

    private fun estado(resumen: ResumenActividad, sinConexion: Boolean = false) = ResumenUiState(
        contenido = if (resumen.totalDelPeriodo == 0) ContenidoResumen.Vacio(resumen) else ContenidoResumen.Datos(resumen),
        periodo = resumen.periodo ?: PeriodoResumen.SIETE_DIAS,
        filtro = resumen.filtro,
        red = "Oficina central",
        datosDe = ahora,
        sinConexion = sinConexion,
    )

    /** El color con que se dibujó un texto. */
    private fun colorDelTexto(texto: String): Color {
        val resultados = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.first().layoutInput.style.color
    }

    private fun dibujar(
        estado: ResumenUiState,
        alTocar: (ToqueResumen) -> Unit = {},
        alCompartir: () -> Unit = {},
        alElegirPeriodo: (PeriodoResumen) -> Unit = {},
        alAplicarFiltro: (FiltroResumen) -> Unit = {},
        alVerAlertas: (FiltroResumen) -> Unit = {},
    ) {
        compose.setContent {
            ZondaTheme {
                ResumenContenido(
                    estado = estado,
                    alVolver = {},
                    alElegirPeriodo = alElegirPeriodo,
                    alTocar = alTocar,
                    alCompartir = alCompartir,
                    alAplicarFiltro = alAplicarFiltro,
                    alVerAlertas = alVerAlertas,
                    zona = utc,
                )
            }
        }
    }

    @Test
    fun `muestra los totales, las metricas y las tarjetas del Figma`() {
        dibujar(estado(resumen()))

        compose.onNodeWithText("Oficina central · del 06/10 al 12/10 · actualizado 12:00").assertIsDisplayed()
        compose.onNodeWithText("revisadas · 50 %").assertIsDisplayed()
        compose.onNodeWithText("14 min").assertIsDisplayed()
        compose.onNodeWithText("hasta la primera revisión").assertIsDisplayed()
        // Dos altas de hace 5 horas y una revisada a los 14 minutos; "1 de 2" también es la precisión de la IA.
        compose.onAllNodesWithText("1 de 2").assertCountEquals(2)
        compose.onNodeWithText("Alertas por día").assertIsDisplayed()
        compose.onNodeWithText("El lunes concentra 2 alertas: 2 de fuerza bruta sobre el mismo servidor.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Por tipo de ataque").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Precisión de cada modelo (de las revisadas)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("50 % · 2 de 4").performScrollTo().assertIsDisplayed()
        // Sin la fórmula, la frase del ganador ni la nota del recall (cambios 4, 10 y 15).
        compose.onAllNodesWithText("Precisión =", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("precisión más alta", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Recall", substring = true).assertCountEquals(0)
        // Sin el pie gris: la pantalla termina en "Compartir resumen" (cambio 17).
        compose.onAllNodesWithText("Calculado con", substring = true).assertCountEquals(0)
    }

    @Test
    fun `cada cifra se toca y suma o saca su filtro`() {
        val tocadas = mutableListOf<ToqueResumen>()
        dibujar(estado(resumen()), alTocar = { tocadas += it })

        compose.onNodeWithText("sin revisar").performClick()
        compose.onNodeWithText("alertas").performClick()
        compose.onNodeWithText("altas revisadas en la primera hora").performScrollTo().performClick()
        compose.onNodeWithText("vie").performScrollTo().performClick()
        compose.onNodeWithText("Probable falsa alarma").performScrollTo().performClick()
        compose.onNodeWithText("Falsos positivos").performScrollTo().performClick()
        compose.onNodeWithText("Umbrales estadísticos").performScrollTo().performClick()
        compose.onNodeWithText("Cobertura de revisión").performScrollTo().performClick()

        val viernes = Franja.dia(LocalDate.of(2026, 10, 9))
        assertEquals(
            listOf(
                ToqueResumen.Revision(RevisionResumen.SIN_REVISAR),
                ToqueResumen.Total,
                ToqueResumen.Altas,
                ToqueResumen.Barra(viernes),
                ToqueResumen.Tipo(TipoAtaque.PROBABLE_FALSA_ALARMA),
                ToqueResumen.Revision(RevisionResumen.FALSOS_POSITIVOS),
                ToqueResumen.Metodo(MetodoDeteccion.UMBRALES),
                ToqueResumen.Revision(RevisionResumen.REVISADAS),
            ),
            tocadas,
        )
    }

    @Test
    fun `con un filtro el boton dice Filtros (1) y la frase lo que se esta mostrando`() {
        dibujar(estado(resumen(FiltroResumen(tipos = setOf(TipoAtaque.FUERZA_BRUTA)))))

        compose.onNodeWithText("Filtros (1)").assertIsDisplayed()
        compose.onNodeWithText("Mostrando fuerza bruta: 2 alertas. Tocá de nuevo para sacarlo.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el chip Fechas abre la hoja y con fechas elegidas dice los dias y el relojito`() {
        dibujar(estado(resumen()))
        compose.onNodeWithText("Fechas").performClick()
        compose.onNodeWithText("Elegí las fechas").assertIsDisplayed()
    }

    @Test
    fun `con unas horas de un dia el grafico va por hora y la linea dice el rango exacto`() {
        val rango = RangoResumen(LocalDate.of(2026, 10, 12).atStartOfDay(), LocalDate.of(2026, 10, 12).atTime(11, 59))
        val resumen = ResumenActividad.calcular(alertas(), rango, ahora, utc)
        dibujar(estado(resumen).copy(fechas = rango))

        compose.onNodeWithText("lun 12/10").assertIsDisplayed()
        compose.onNodeWithText("Alertas por hora").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Oficina central · lunes 12/10 · de 00:00 a 11:59 · actualizado 12:00").assertIsDisplayed()
    }

    @Test
    fun `sin filtros el boton dice Filtros y abre la hoja con todas las opciones`() {
        dibujar(estado(resumen()))

        compose.onNodeWithText("Filtros").performClick()

        compose.onNodeWithText("Filtrar el resumen").assertIsDisplayed()
        compose.onNodeWithText("Cuándo · podés elegir varios").assertExists()
        compose.onNodeWithText("vie 09/10").assertExists()
        compose.onNodeWithText("Tipo de ataque · podés elegir varios").assertExists()
        compose.onNodeWithText("Volumen inusual de conexiones").assertExists()
        compose.onNodeWithText("Revisión").assertExists()
        compose.onNodeWithText("Modelo").assertExists()
        compose.onNodeWithText("Ver estas 4 alertas en la lista →").assertExists()
        compose.onNodeWithText("Mostrar 4 alertas").assertExists()
    }

    @Test
    fun `la hoja cuenta cuantas quedan antes de aplicar y Mostrar aplica todo junto`() {
        val aplicados = mutableListOf<FiltroResumen>()
        dibujar(estado(resumen()), alAplicarFiltro = { aplicados += it })

        compose.onNodeWithText("Filtros").performClick()
        compose.onNode(hasText("Fuerza bruta (SSH, FTP)") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performScrollTo().performClick()
        compose.onNodeWithText("Mostrar 2 alertas").performScrollTo()
        compose.onNodeWithText("Sin revisar").performScrollTo().performClick()
        compose.onNodeWithText("Mostrar 1 alerta").performScrollTo().performClick()

        assertEquals(listOf(FiltroResumen(tipos = setOf(TipoAtaque.FUERZA_BRUTA), revision = RevisionResumen.SIN_REVISAR)), aplicados)
        compose.onAllNodesWithText("Filtrar el resumen").assertCountEquals(0)
    }

    @Test
    fun `la hoja muestra tildado lo que se toco en el Resumen, Limpiar lo saca y Ver en la lista lleva esos filtros`() {
        val enLista = mutableListOf<FiltroResumen>()
        dibujar(estado(resumen(FiltroResumen(revision = RevisionResumen.REVISADAS))), alVerAlertas = { enLista += it })

        compose.onNodeWithText("Filtros (1)").performClick()
        compose.onNodeWithText("Ver estas 2 alertas en la lista →").performScrollTo()
        compose.onNodeWithText("Limpiar").performScrollTo().performClick()
        compose.onNodeWithText("Ver estas 4 alertas en la lista →").performScrollTo().performClick()

        assertEquals(listOf(FiltroResumen()), enLista)
    }

    @Test
    fun `7 o 30 dias y compartir`() {
        val periodos = mutableListOf<PeriodoResumen>()
        var compartido = 0
        dibujar(estado(resumen()), alElegirPeriodo = { periodos += it }, alCompartir = { compartido++ })

        compose.onNodeWithText("30 días").performClick()
        compose.onAllNodesWithText("Compartir resumen").onFirst().performClick()
        compose.onNodeWithContentDescription("Compartir resumen").performClick()

        assertEquals(listOf(PeriodoResumen.TREINTA_DIAS), periodos)
        assertEquals(2, compartido)
    }

    @Test
    fun `sin alertas muestra el vacio del Figma y se puede compartir igual`() {
        var compartido = false
        dibujar(estado(resumen(lista = emptyList())), alCompartir = { compartido = true })

        compose.onNodeWithText("Sin alertas en los últimos 7 días").assertIsDisplayed()
        compose.onNodeWithText("El sensor siguió activo toda la semana. Podés compartir el resumen igual: muestra que la red estuvo tranquila.")
            .assertIsDisplayed()
        compose.onAllNodesWithText("Calculado con", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Compartir resumen").onFirst().performClick()

        assertTrue(compartido)
    }

    @Test
    fun `sin conexion avisa con que datos esta hecho`() {
        dibujar(estado(resumen(), sinConexion = true))

        compose.onNodeWithText("Sin conexión · resumen con los datos de las 12:00").assertIsDisplayed()
        compose.onNodeWithText("Oficina central · del 06/10 al 12/10 · datos de las 12:00").assertIsDisplayed()
    }

    @Test
    fun `la imagen que se comparte lleva solo cantidades, sin direcciones ni nombres de equipos`() {
        compose.setContent {
            ZondaTheme {
                ImagenResumen(resumen = resumen(), red = "Oficina central", datosDe = ahora, sinConexion = false, zona = utc)
            }
        }

        val textos = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
            .flatMap { nodo -> nodo.config[SemanticsProperties.Text].map { it.text } }
        assertTrue(textos.contains("Resumen semanal"))
        assertTrue(textos.contains("Generado con Zonda · no incluye IPs ni nombres de equipos"))
        // Las alertas de prueba van de pc-09 (192.168.10.9) a srv-web (192.168.10.50) (RNF02).
        assertFalse(textos.any { it.contains("192.168") || it.contains("pc-09") || it.contains("srv-web") })
    }

    @Test
    fun `la hoja de compartir muestra el archivo y solo las apps que hay`() {
        val elegidos = mutableListOf<DestinoCompartir>()
        val imagen = ImagenCompartida(
            nombre = "Resumen_06-10_12-10.png",
            uri = Uri.parse("content://ar.edu.uade.zonda.archivos/compartir/Resumen_06-10_12-10.png"),
            bytes = 48_500,
            vistaPrevia = ImageBitmap(10, 10),
            texto = "Resumen Zonda",
        )
        compose.setContent {
            ZondaTheme {
                ContenidoHojaCompartir(
                    imagen = imagen,
                    destinos = listOf(DestinoCompartir.GMAIL, DestinoCompartir.COPIAR),
                    alElegir = { elegidos += it },
                    alCancelar = {},
                )
            }
        }

        compose.onNodeWithText("Resumen_06-10_12-10.png").assertIsDisplayed()
        compose.onNodeWithText("Imagen · 48 KB · sin IPs ni nombres de equipos").assertIsDisplayed()
        compose.onNodeWithText("Gmail").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("WhatsApp").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Copiar").performClick()

        assertEquals(listOf(DestinoCompartir.COPIAR), elegidos)
    }

    @Test
    fun `por tipo de ataque va en dos bloques, con los tipos en cero en gris y sin el bloque vacio`() {
        // Fuerza bruta, escaneo y probable falsa alarma: lo que la IA no detectó queda en cero y se
        // oculta con su título (cambio 14). "Sin tipo identificado", en cero, se ve en gris.
        dibujar(estado(resumen()))

        compose.onNodeWithText("LO QUE DETECTÓ LA IA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin tipo identificado").performScrollTo().assertIsDisplayed()
        assertEquals(TextoSecundario, colorDelTexto("Sin tipo identificado"))
        assertEquals(Texto, colorDelTexto("Fuerza bruta (SSH, FTP)"))
        compose.onAllNodesWithText("LO QUE LA IA NO DETECTÓ").assertCountEquals(0)
        compose.onAllNodesWithText("Servicio no autorizado").assertCountEquals(0)
        compose.onAllNodesWithText("Volumen inusual").assertCountEquals(0)
        compose.onAllNodesWithText("Otros").assertCountEquals(0)
    }

    @Test
    fun `con algo que la IA no detecto se ve su bloque y cada fila dice la severidad elegida`() {
        val solo = Fixtures.alerta(
            "a-5", severidad = Severidad.MEDIA, tipoAtaque = TipoAtaque.SERVICIO_NO_AUTORIZADO,
            creadaEn = ahora.minus(Duration.ofHours(2)), metodos = setOf(MetodoDeteccion.REGLAS),
        )
        val severidades = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA)
        dibujar(estado(resumen(lista = alertas() + solo)).copy(severidades = severidades))

        compose.onNodeWithText("LO QUE LA IA NO DETECTÓ").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Servicio no autorizado").performScrollTo().assertIsDisplayed()
        // Volumen inusual, en cero, queda en gris dentro del bloque.
        compose.onNodeWithText("Volumen inusual").performScrollTo().assertIsDisplayed()
        assertEquals(TextoSecundario, colorDelTexto("Volumen inusual"))
        // El punto de color también lo lee TalkBack.
        compose.onNode(hasText("Escaneo de puertos") and hasContentDescription("Severidad alta")).assertExists()
        compose.onNode(hasText("Probable falsa alarma") and hasContentDescription("Severidad baja")).assertExists()
        compose.onNode(hasText("Servicio no autorizado") and hasContentDescription("Severidad media")).assertExists()
    }

    @Test
    fun `el i de por tipo de ataque abre que es cada tipo`() {
        dibujar(estado(resumen()))

        compose.onNodeWithContentDescription("Qué es cada tipo").performScrollTo().performClick()

        compose.onNodeWithText("Qué es cada tipo").assertIsDisplayed()
        compose.onNodeWithText("La IA vio algo raro y otro modelo le pone el tipo de ataque que parece.").assertExists()
        compose.onNodeWithText("Fuerza bruta. Muchos intentos de contraseña contra un servicio.").assertExists()
        compose.onNodeWithText("Lo marcaron los otros dos detectores de Zonda.").assertExists()
        compose.onNodeWithText("Entendido").performScrollTo().performClick()
        compose.onAllNodesWithText("Qué es cada tipo").assertCountEquals(0)
    }

    @Test
    fun `el i de la precision abre su hoja con un ejemplo de las cifras y lo que cuenta sin filtro`() {
        dibujar(estado(resumen()))

        compose.onNodeWithContentDescription("Cómo se lee la precisión").performScrollTo().performClick()

        compose.onNodeWithText("Cómo se lee la precisión").assertIsDisplayed()
        compose.onNodeWithText(
            "De las alertas que marcó un modelo y ya revisaste, cuántas eran ataques reales. " +
                "Por ejemplo, la IA tiene 1 de 2: marcó 2 alertas que ya revisaste y 1 era un ataque.",
        ).assertExists()
        compose.onNodeWithText("Sin filtro: la precisión cuenta todas las alertas revisadas de estos 7 días.").assertExists()
        compose.onNodeWithText("Se calcula con las alertas guardadas en el teléfono, de los últimos 30 días.").assertExists()
        compose.onNodeWithText("Entendido").performScrollTo().performClick()
        compose.onAllNodesWithText("Cómo se lee la precisión").assertCountEquals(0)
    }
}
