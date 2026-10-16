package ar.edu.uade.zonda.ui.resumen

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.Tramo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.usecase.ObservarResumen
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResumenViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository()
    private val alertas = FakeAlertasRepository()
    private val conectividad = FakeConectividad()

    private fun viewModel() = ResumenViewModel(
        ObservarResumen(redes, alertas, reloj = { Fixtures.AHORA }, zona = { ZoneOffset.UTC }),
        redes,
        conectividad,
    )

    private suspend fun ReceiveTurbine<ResumenUiState>.esperar(condicion: (ResumenUiState) -> Boolean): ResumenUiState {
        var estado = awaitItem()
        while (!condicion(estado)) estado = awaitItem()
        return estado
    }

    private val ResumenUiState.datos: ContenidoResumen.Datos?
        get() = contenido as? ContenidoResumen.Datos

    private fun conRedYAlertas() {
        redes.agregar(Fixtures.red("red-1"))
        alertas.agregar(
            Fixtures.alerta("hoy-1", tipoAtaque = TipoAtaque.FUERZA_BRUTA, revision = Fixtures.revision()),
            Fixtures.alerta("hoy-2", tipoAtaque = TipoAtaque.ESCANEO),
            Fixtures.alerta("hace-10-dias", creadaEn = Fixtures.AHORA.minus(Duration.ofDays(10))),
        )
    }

    @Test
    fun `arranca en 7 dias con las alertas de la red y su nombre`() = runTest {
        conRedYAlertas()

        viewModel().estado.test {
            val estado = esperar { it.datos != null }
            assertEquals(PeriodoResumen.SIETE_DIAS, estado.periodo)
            assertEquals(2, estado.datos!!.resumen.total)
            assertEquals("Oficina", estado.red)
            assertNull(estado.grupo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trae la severidad que eligio la red para cada tipo`() = runTest {
        conRedYAlertas()
        redes.guardarSeveridadesLocal("red-1", SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA))

        viewModel().estado.test {
            val estado = esperar { it.datos != null }
            // El punto de color de "Por tipo de ataque" (cambio 14).
            assertEquals(Severidad.ALTA, estado.severidades.severidadDe(TipoAtaque.ESCANEO))
            assertEquals(Severidad.ALTA, estado.severidades.severidadDe(TipoAtaque.FUERZA_BRUTA))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `30 dias suma las alertas de hace mas de una semana`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.elegirPeriodo(PeriodoResumen.TREINTA_DIAS)
            val estado = esperar { it.datos?.resumen?.periodo == PeriodoResumen.TREINTA_DIAS }
            assertEquals(3, estado.datos!!.resumen.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tocar una cifra recalcula todo sobre ese grupo y otro toque vuelve a todas`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.datos != null }

            viewModel.tocarCifra(GrupoResumen.Tipo(TipoAtaque.ESCANEO))
            val filtrado = esperar { it.datos?.resumen?.grupo == GrupoResumen.Tipo(TipoAtaque.ESCANEO) }
            assertEquals(GrupoResumen.Tipo(TipoAtaque.ESCANEO), filtrado.grupo)
            assertEquals(1, filtrado.datos!!.resumen.total)

            viewModel.tocarCifra(GrupoResumen.Tipo(TipoAtaque.ESCANEO))
            val todas = esperar { it.grupo == null && it.datos?.resumen?.grupo == null }
            assertEquals(2, todas.datos!!.resumen.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tocar el total de alertas o la cruz del chip quita el filtro`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.tocarCifra(GrupoResumen.SinRevisar)
            esperar { it.grupo == GrupoResumen.SinRevisar }
            viewModel.tocarCifra(null)
            esperar { it.grupo == null }

            viewModel.tocarCifra(GrupoResumen.Revisadas)
            esperar { it.grupo == GrupoResumen.Revisadas }
            viewModel.quitarFiltro()
            esperar { it.grupo == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `al cambiar de periodo se quita un dia elegido pero no los demas filtros`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()
        val hoy = LocalDate.of(2026, 10, 12)

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.tocarCifra(GrupoResumen.Barra(Tramo(hoy, hoy)))
            esperar { it.grupo is GrupoResumen.Barra }
            viewModel.elegirPeriodo(PeriodoResumen.TREINTA_DIAS)
            val sinDia = esperar { it.periodo == PeriodoResumen.TREINTA_DIAS }
            assertNull(sinDia.grupo)

            viewModel.tocarCifra(GrupoResumen.Revisadas)
            esperar { it.grupo == GrupoResumen.Revisadas }
            viewModel.elegirPeriodo(PeriodoResumen.SIETE_DIAS)
            val conRevisadas = esperar { it.periodo == PeriodoResumen.SIETE_DIAS }
            assertEquals(GrupoResumen.Revisadas, conRevisadas.grupo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin alertas en el periodo muestra el vacio y sin red, que no hay red`() = runTest {
        redes.agregar(Fixtures.red("red-1"))

        viewModel().estado.test {
            assertTrue(esperar { it.contenido !is ContenidoResumen.Cargando }.contenido is ContenidoResumen.Vacio)
            cancelAndIgnoreRemainingEvents()
        }
        ResumenViewModel(ObservarResumen(FakeRedesRepository(), alertas), FakeRedesRepository(), conectividad).estado.test {
            assertEquals(ContenidoResumen.SinRed, esperar { it.contenido !is ContenidoResumen.Cargando }.contenido)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin conexion el resumen sigue y la pantalla lo avisa`() = runTest {
        conRedYAlertas()
        conectividad.estado.value = false

        viewModel().estado.test {
            val estado = esperar { it.datos != null }
            assertTrue(estado.sinConexion)
            assertEquals(Fixtures.AHORA, estado.datosDe)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `al compartir avisa por donde salio y el aviso se usa una sola vez`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.compartido(DestinoCompartir.TELEGRAM)
            assertEquals(AvisoResumen.Enviado(DestinoCompartir.TELEGRAM), esperar { it.aviso != null }.aviso)
            viewModel.avisoMostrado()
            esperar { it.aviso == null }
            viewModel.compartido(DestinoCompartir.COPIAR)
            assertEquals(AvisoResumen.Copiado, esperar { it.aviso != null }.aviso)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
