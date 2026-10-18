package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.CalculoSeveridad
import ar.edu.uade.zonda.domain.model.CambioDeSeveridad
import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.TipoCambio
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El responsable, "Asignar a", el cálculo de la severidad y el historial nuevo del detalle (decisiones 40 a 42). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResponsableYCalculoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")
    private val taiel = Persona("d-1", "Taiel", "moto g(9) plus", Perfil.ADMINISTRADOR, null, vos = false)
    private val laura = Persona("d-2", "Laura", "Teléfono de Laura", Perfil.ANALISTA, null, vos = false)
    private val meli = Persona("d-3", "Meli", "Galaxy A54", Perfil.ADMINISTRADOR, null, vos = false)
    private val hace10 = Fixtures.AHORA.minus(Duration.ofMinutes(10))

    private fun contenido(alerta: Alerta, perfil: Perfil, miId: String, calculo: CalculoSeveridad? = null, cambios: List<CambioDeSeveridad> = emptyList()) =
        DetalleUiState.Contenido(
            alerta = alerta,
            accion = null,
            sinConexion = false,
            datosDe = Fixtures.AHORA,
            nombreDelTelefono = "moto g(9) plus",
            nombreDeLaRed = "Oficina central",
            personas = Persona.ordenar(listOf(taiel, laura, meli).map { it.copy(vos = it.id == miId) }),
            perfil = perfil,
            miId = miId,
            calculo = calculo,
            cambiosDeSeveridad = cambios,
        )

    private fun dibujar(estado: DetalleUiState, acciones: AccionesDetalle = AccionesDetalle()) {
        compose.setContent { ZondaTheme { DetalleContenido(estado = estado, acciones = acciones, zona = utc, ahora = Fixtures.AHORA) } }
    }

    @Test
    fun `el administrador ve Sin asignar con Asignar`() {
        dibujar(contenido(Fixtures.alerta("a-1"), Perfil.ADMINISTRADOR, "d-1"))

        compose.onNodeWithText("Responsable").assertIsDisplayed()
        compose.onNodeWithText("Sin asignar").assertIsDisplayed()
        compose.onNodeWithText("Asignar").assertIsDisplayed()
    }

    @Test
    fun `asignada por vos a Laura, el administrador la puede cambiar`() {
        val alerta = Fixtures.alerta("a-1").copy(asignacion = Asignacion("d-2", "Laura", "d-1", "Taiel", hace10))
        dibujar(contenido(alerta, Perfil.ADMINISTRADOR, "d-1"))

        compose.onNodeWithText("Laura").assertIsDisplayed()
        compose.onNodeWithText("Asignada por vos · hoy 11:50").assertIsDisplayed()
        compose.onNodeWithText("Cambiar").assertIsDisplayed()
    }

    @Test
    fun `la analista se asigna una libre y suelta la suya`() {
        var asignada: Persona? = taiel
        dibujar(contenido(Fixtures.alerta("a-1"), Perfil.ANALISTA, "d-2"), AccionesDetalle(alAsignar = { asignada = it }))

        compose.onNodeWithText("Me la asigno").performClick()
        assertEquals("d-2", asignada?.id)
    }

    @Test
    fun `la analista con la suya ve Soltarla y que se la asigno ella`() {
        var soltada = false
        val alerta = Fixtures.alerta("a-1").copy(asignacion = Asignacion("d-2", "Laura", "d-2", "Laura", hace10))
        dibujar(contenido(alerta, Perfil.ANALISTA, "d-2"), AccionesDetalle(alAsignar = { soltada = it == null }))

        compose.onNodeWithText("Laura (vos)").assertIsDisplayed()
        compose.onNodeWithText("Te la asignaste · hoy 11:50").assertIsDisplayed()
        compose.onNodeWithText("Soltarla").performClick()
        assertTrue(soltada)
    }

    @Test
    fun `en la de otra persona la analista no tiene boton`() {
        val alerta = Fixtures.alerta("a-1").copy(asignacion = Asignacion("d-3", "Meli", "d-1", "Taiel", hace10))
        dibujar(contenido(alerta, Perfil.ANALISTA, "d-2"))

        compose.onNodeWithText("Asignada por Taiel · hoy 11:50").assertIsDisplayed()
        compose.onNodeWithText("Cambiar").assertDoesNotExist()
        compose.onNodeWithText("Soltarla").assertDoesNotExist()
        compose.onNodeWithText("Me la asigno").assertDoesNotExist()
    }

    @Test
    fun `una alerta revisada sin responsable no muestra la tarjeta`() {
        val alerta = Fixtures.alerta("a-1", revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR))
        dibujar(contenido(alerta, Perfil.ADMINISTRADOR, "d-1"))

        compose.onNodeWithText("Responsable").assertDoesNotExist()
    }

    @Test
    fun `la hoja Asignar a lista a la gente con su perfil y Sacar la asignacion`() {
        var elegida: Persona? = taiel
        compose.setContent {
            ZondaTheme {
                Column {
                    ContenidoHojaAsignar(
                        personas = Persona.ordenar(listOf(taiel.copy(vos = true), laura, meli)),
                        responsableId = "d-2",
                        alElegir = { elegida = it },
                    )
                }
            }
        }

        compose.onNodeWithText("Le llega un aviso por Telegram y la ve en Estado.").assertIsDisplayed()
        compose.onNodeWithText("Taiel (vos)").assertIsDisplayed()
        compose.onNodeWithText("Meli").performClick()
        assertEquals("d-3", elegida?.id)
        compose.onNodeWithText("Sacar la asignación").performClick()
        assertNull(elegida)
    }

    @Test
    fun `como se calculo la severidad con un equipo critico`() {
        val alerta = Fixtures.alerta("a-1", severidad = Severidad.ALTA, tipoAtaque = TipoAtaque.ESCANEO)
        val calculo = CalculoSeveridad(Severidad.MEDIA, "srv-sueldos", Importancia.CRITICO)
        dibujar(contenido(alerta, Perfil.ADMINISTRADOR, "d-1", calculo = calculo))

        compose.onNodeWithText("Cómo se calculó la severidad").assertIsDisplayed()
        compose.onNodeWithText("Tipo de ataque: escaneo de puertos").assertIsDisplayed()
        compose.onNodeWithText("Equipo destino: srv-sueldos (sube un nivel)").assertIsDisplayed()
        compose.onNodeWithText("Crítico").assertIsDisplayed()
        compose.onNodeWithText("Resultado").assertIsDisplayed()
    }

    @Test
    fun `en el tope la tarjeta dice que ya era alta y con un equipo normal no aparece`() {
        val alerta = Fixtures.alerta("a-1")
        dibujar(contenido(alerta, Perfil.ADMINISTRADOR, "d-1", calculo = CalculoSeveridad(Severidad.ALTA, "srv-web", Importancia.CRITICO)))
        compose.onNodeWithText("Equipo destino: srv-web (ya era alta)").assertIsDisplayed()
    }

    @Test
    fun `con un equipo normal no hay tarjeta del calculo`() {
        dibujar(contenido(Fixtures.alerta("a-1"), Perfil.ADMINISTRADOR, "d-1", calculo = CalculoSeveridad.de(Fixtures.alerta("a-1"), SeveridadPorTipo.RECOMENDADA, emptyMap())))
        compose.onNodeWithText("Cómo se calculó la severidad").assertDoesNotExist()
    }

    @Test
    fun `el historial suma los cambios de severidad y las asignaciones`() {
        val registro = CambioRegistro(
            7, hace10, TipoCambio.SEVERIDAD, "d-1", "Taiel", "fuerza_bruta", null, "alta", "media",
            "El backup de cada noche usa SSH y dispara alertas de fuerza bruta.",
        )
        val alerta = Fixtures.alerta("a-1", revision = Fixtures.revision(fecha = Fixtures.AHORA.minus(Duration.ofMinutes(30)), origen = OrigenRevision.SERVIDOR).copy(autor = "Teléfono de Laura", persona = "Laura"))
            .copy(
                asignaciones = listOf(
                    Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA.minus(Duration.ofMinutes(50))),
                    Asignacion(null, null, "d-2", "Laura", Fixtures.AHORA.minus(Duration.ofMinutes(45))),
                    Asignacion("d-3", "Meli", "d-3", "Meli", Fixtures.AHORA.minus(Duration.ofMinutes(40))),
                    Asignacion(null, null, "d-1", "Taiel", Fixtures.AHORA.minus(Duration.ofMinutes(35))),
                ),
            )
        dibujar(contenido(alerta, Perfil.ADMINISTRADOR, "d-1", cambios = listOf(CambioDeSeveridad(hace10, Severidad.ALTA, Severidad.MEDIA, registro))))

        compose.onNodeWithText("Pasó de alta a media").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Taiel cambió fuerza bruta: “El backup de cada noche usa SSH y dispara alertas de fuerza bruta.”").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Asignada a Laura").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Laura la soltó").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Meli se la asignó").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Taiel sacó la asignación").performScrollTo().assertIsDisplayed()
        // La revisión dice la persona y su teléfono, en la tarjeta y en el historial.
        compose.onAllNodesWithText("Laura · Teléfono de Laura").assertCountEquals(2)
    }
}
