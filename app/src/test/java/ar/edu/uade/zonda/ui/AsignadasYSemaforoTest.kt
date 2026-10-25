package ar.edu.uade.zonda.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroResponsable
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.alertas.AlertasContenido
import ar.edu.uade.zonda.ui.alertas.AlertasUiState
import ar.edu.uade.zonda.ui.alertas.ContenidoAlertas
import ar.edu.uade.zonda.ui.estado.AccionesEstado
import ar.edu.uade.zonda.ui.estado.ContenidoEstado
import ar.edu.uade.zonda.ui.estado.EstadoContenido
import ar.edu.uade.zonda.ui.estado.EstadoUiState
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lo que suma la 1.0.5 en Estado y Alertas: asignadas a vos, el semáforo que filtra, el responsable y la importancia. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AsignadasYSemaforoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")
    private val taiel = Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, null, vos = false)
    private val laura = Persona("d-2", "Laura", "Teléfono de Laura", Perfil.ANALISTA, null, vos = true)
    private val meli = Persona("d-3", "Meli", "Galaxy A54", Perfil.ADMINISTRADOR, null, vos = false)

    /** Laura es este teléfono; srv-web es crítico. */
    private val contexto = ContextoTarjetas(
        miId = "d-2",
        severidades = SeveridadPorTipo.RECOMENDADA,
        importancias = mapOf("192.168.10.50" to Importancia.CRITICO, "192.168.10.80" to Importancia.POCO_IMPORTANTE),
        personas = Persona.ordenar(listOf(taiel, laura, meli)),
    )

    private val deTaiel = Fixtures.alerta("a-1").copy(asignacion = Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA))
    private val deMeli = Fixtures.alerta("a-2", severidad = Severidad.MEDIA, tipoAtaque = TipoAtaque.ESCANEO).copy(asignacion = Asignacion("d-3", "Meli", "d-3", "Meli", Fixtures.AHORA))

    private fun estado(asignadas: List<ar.edu.uade.zonda.domain.model.Alerta>, elegida: Severidad? = null) = EstadoUiState(
        contenido = ContenidoEstado.Datos(
            semaforo = Semaforo.ROJO,
            sinRevisar = ContadoresSinRevisar(altas = 1, medias = 1, bajas = 0),
            ultimaDeteccion = Fixtures.AHORA.minus(Duration.ofMinutes(5)),
            alertas = listOf(deTaiel, deMeli),
        ),
        severidadElegida = elegida,
        datosDe = Fixtures.AHORA,
        asignadasAMi = asignadas,
        contexto = contexto,
    )

    private fun dibujarEstado(estado: EstadoUiState, acciones: AccionesEstado = AccionesEstado()) {
        compose.setContent { ZondaTheme { EstadoContenido(estado = estado, acciones = acciones, ahora = Fixtures.AHORA, zona = utc) } }
    }

    @Test
    fun `estado avisa las alertas asignadas a vos y Ver abre Alertas con esas`() {
        var ver = false
        dibujarEstado(estado(listOf(deTaiel)), AccionesEstado(alVerAsignadas = { ver = true }))

        compose.onNodeWithText("1 alerta asignada a vos").assertIsDisplayed()
        compose.onNodeWithText("Te la asignó Taiel.").assertIsDisplayed()
        compose.onNodeWithText("1 alerta asignada a vos").performClick()
        assertTrue(ver)
    }

    @Test
    fun `con varias de distintas personas dice quienes y vos al final si te asignaste alguna`() {
        val otra = Fixtures.alerta("a-3").copy(asignacion = Asignacion("d-2", "Laura", "d-3", "Meli", Fixtures.AHORA))
        val propia = Fixtures.alerta("a-4").copy(asignacion = Asignacion("d-2", "Laura", "d-2", "Laura", Fixtures.AHORA))
        dibujarEstado(estado(listOf(deTaiel, otra, propia)))

        compose.onNodeWithText("3 alertas asignadas a vos").assertIsDisplayed()
        compose.onNodeWithText("Te las asignaron Taiel, Meli y vos.").assertIsDisplayed()
    }

    @Test
    fun `una propia y otra de Taiel no dicen que las dos son de Taiel`() {
        // El caso del moto (17/10): decía "Te las asignó Sofia." aunque una te la habías asignado vos.
        val propia = Fixtures.alerta("a-4").copy(asignacion = Asignacion("d-2", "Laura", "d-2", "Laura", Fixtures.AHORA))
        dibujarEstado(estado(listOf(deTaiel, propia)))

        compose.onNodeWithText("2 alertas asignadas a vos").assertIsDisplayed()
        compose.onNodeWithText("Te las asignaron Taiel y vos.").assertIsDisplayed()
    }

    @Test
    fun `dos de la misma persona dicen solo su nombre`() {
        val otraDeTaiel = Fixtures.alerta("a-3").copy(asignacion = Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA))
        dibujarEstado(estado(listOf(deTaiel, otraDeTaiel)))

        compose.onNodeWithText("Te las asignó Taiel.").assertIsDisplayed()
    }

    @Test
    fun `sin asignadas a vos no hay aviso y las tarjetas dicen Vos y el nombre de quien la tiene`() {
        dibujarEstado(estado(emptyList()))

        compose.onNodeWithText("1 alerta asignada a vos").assertDoesNotExist()
        // La lista es perezosa: se la desplaza hasta cada tarjeta.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Vos"))
        compose.onNodeWithText("Vos").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Meli"))
        compose.onNodeWithText("Meli").assertIsDisplayed()
    }

    @Test
    fun `tocar el semaforo rojo filtra las altas y TalkBack dice que hace`() {
        var tocada: Severidad? = null
        dibujarEstado(estado(emptyList()), AccionesEstado(alTocarContador = { tocada = it }))

        compose.onNode(SemanticsMatcher("toca para ver las altas") { nodo ->
            nodo.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Ver las alertas altas sin revisar"
        }).performClick()
        assertEquals(Severidad.ALTA, tocada)
    }

    @Test
    fun `con las altas elegidas el semaforo ofrece ver todas y la ayuda dice toca de nuevo`() {
        dibujarEstado(estado(emptyList(), elegida = Severidad.ALTA))

        compose.onNodeWithText("Mostrando solo altas sin revisar. Tocá de nuevo para ver todas.").assertIsDisplayed()
        compose.onNode(SemanticsMatcher("toca para ver todas") { nodo ->
            nodo.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Ver todas las alertas sin revisar"
        }).assertExists()
    }

    // ---- Alertas ----

    private fun dibujarAlertas(estado: AlertasUiState, alCambiarFiltro: (FiltroAlertas) -> Unit = {}) {
        compose.setContent {
            ZondaTheme {
                AlertasContenido(estado = estado, alAbrirDetalle = {}, alCambiarFiltro = alCambiarFiltro, alSincronizar = {}, alQuitarFiltros = {}, zona = utc)
            }
        }
    }

    @Test
    fun `la tarjeta dice por que subio o bajo la severidad`() {
        val escaneo = Fixtures.alerta("m-1", severidad = Severidad.ALTA, tipoAtaque = TipoAtaque.ESCANEO)
        val impresora = Fixtures.alerta("b-1", severidad = Severidad.BAJA, tipoAtaque = TipoAtaque.ESCANEO, destinoIp = "192.168.10.80")
        val tope = Fixtures.alerta("a-9", severidad = Severidad.ALTA)
        dibujarAlertas(AlertasUiState(contenido = ContenidoAlertas.Lista(listOf(escaneo, impresora, tope), sinRevisar = 3), contexto = contexto))

        compose.onNodeWithText("Subió de media a alta: srv-web es crítico.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bajó de media a baja: srv-web es poco importante.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("srv-web es crítico (ya era alta).").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `asignadas a mi lo dice el resumen y Mas filtros cuenta uno`() {
        val filtro = FiltroAlertas(responsable = FiltroResponsable.AsignadasAMi("d-2"))
        dibujarAlertas(AlertasUiState(contenido = ContenidoAlertas.Lista(listOf(deTaiel), sinRevisar = 1), filtro = filtro, contexto = contexto))

        compose.onNodeWithText("1 alerta · asignadas a mí · últimos 30 días").assertIsDisplayed()
        compose.onNodeWithText("Más filtros (1)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `mas filtros tiene Responsable con cualquiera, asignadas a mi, sin asignar y las otras personas`() {
        val aplicados = mutableListOf<FiltroAlertas>()
        dibujarAlertas(AlertasUiState(contenido = ContenidoAlertas.Lista(listOf(deTaiel), sinRevisar = 1), contexto = contexto), alCambiarFiltro = { aplicados += it })

        compose.onNodeWithText("Más filtros").performScrollTo().performClick()
        listOf("Responsable", "Cualquiera", "Asignadas a mí", "Sin asignar", "Taiel", "Meli").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Laura").assertDoesNotExist()
        compose.onNodeWithText("Meli").performScrollTo().performClick()
        compose.onNodeWithText("Aplicar").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf(FiltroAlertas(responsable = FiltroResponsable.De("d-3"))), aplicados)
    }

    @Test
    fun `el filtro de responsable acepta lo que corresponde`() {
        val sinAsignar = Fixtures.alerta("a-5")

        assertTrue(FiltroResponsable.AsignadasAMi("d-2").acepta(deTaiel))
        assertTrue(FiltroResponsable.SinAsignar.acepta(sinAsignar))
        assertTrue(FiltroResponsable.De("d-3").acepta(deMeli))
        assertEquals(false, FiltroResponsable.SinAsignar.acepta(deTaiel))
        assertEquals(1, FiltroAlertas(responsable = FiltroResponsable.SinAsignar).cantidadMasFiltros)
    }
}
