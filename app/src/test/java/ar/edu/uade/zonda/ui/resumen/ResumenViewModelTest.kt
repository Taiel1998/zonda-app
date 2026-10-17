package ar.edu.uade.zonda.ui.resumen

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.EscalaBarra
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.Franja
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.RangoResumen
import ar.edu.uade.zonda.domain.model.RevisionResumen
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.ToqueResumen
import ar.edu.uade.zonda.domain.usecase.ObservarResumen
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            assertTrue(estado.filtro.vacio)
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
    fun `tocar cifras suma filtros y tocarlas de nuevo los saca`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.datos != null }

            viewModel.tocar(ToqueResumen.Tipo(TipoAtaque.ESCANEO))
            val filtrado = esperar { it.datos?.resumen?.filtro?.tipos == setOf(TipoAtaque.ESCANEO) }
            assertEquals(1, filtrado.datos!!.resumen.total)

            // Otro tipo se suma: cuentan las de cualquiera de los dos.
            viewModel.tocar(ToqueResumen.Tipo(TipoAtaque.FUERZA_BRUTA))
            val dos = esperar { it.datos?.resumen?.filtro?.tipos?.size == 2 }
            assertEquals(2, dos.datos!!.resumen.total)
            assertEquals(2, dos.filtro.cantidad)

            // Un filtro de otra clase se suma con los anteriores: la de escaneo no está revisada.
            viewModel.tocar(ToqueResumen.Revision(RevisionResumen.REVISADAS))
            val revisadas = esperar { it.datos?.resumen?.filtro?.revision == RevisionResumen.REVISADAS }
            assertEquals(1, revisadas.datos!!.resumen.total)

            viewModel.tocar(ToqueResumen.Tipo(TipoAtaque.ESCANEO))
            viewModel.tocar(ToqueResumen.Tipo(TipoAtaque.FUERZA_BRUTA))
            val soloRevisadas = esperar { it.datos?.resumen?.filtro == FiltroResumen(revision = RevisionResumen.REVISADAS) }
            assertEquals(1, soloRevisadas.datos!!.resumen.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tocar el total de alertas saca todos los filtros`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.tocar(ToqueResumen.Revision(RevisionResumen.SIN_REVISAR))
            viewModel.tocar(ToqueResumen.Metodo(MetodoDeteccion.IA))
            esperar { it.filtro.cantidad == 2 }
            viewModel.tocar(ToqueResumen.Total)
            val todas = esperar { it.filtro.vacio && it.datos?.resumen?.filtro?.vacio == true }
            assertEquals(2, todas.datos!!.resumen.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `las altas son los tipos que la red tiene en alta`() = runTest {
        conRedYAlertas()
        redes.guardarSeveridadesLocal("red-1", SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA))
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.datos != null && it.severidades.severidadDe(TipoAtaque.ESCANEO) == Severidad.ALTA }
            viewModel.tocar(ToqueResumen.Altas)
            val altas = esperar { it.filtro.tipos.isNotEmpty() }
            assertEquals(setOf(TipoAtaque.FUERZA_BRUTA, TipoAtaque.SIN_TIPIFICAR, TipoAtaque.ESCANEO), altas.filtro.tipos)
            assertTrue(altas.filtro.tiposSonLasAltas(altas.altas))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la hoja de filtros reemplaza todos los filtros de una vez`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()
        val hoy = Franja.dia(LocalDate.of(2026, 10, 12))

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.tocar(ToqueResumen.Revision(RevisionResumen.REVISADAS))
            esperar { it.filtro.revision != null }

            val deLaHoja = FiltroResumen(barras = setOf(hoy), tipos = setOf(TipoAtaque.ESCANEO))
            viewModel.aplicarFiltro(deLaHoja)
            val aplicado = esperar { it.datos?.resumen?.filtro == deLaHoja }
            assertEquals(deLaHoja, aplicado.filtro)
            assertEquals(listOf("hoy-2"), aplicado.datos!!.resumen.alertas.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `al cambiar de periodo se sacan los dias elegidos pero no los demas filtros`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()
        val hoy = Franja.dia(LocalDate.of(2026, 10, 12))

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.tocar(ToqueResumen.Barra(hoy))
            viewModel.tocar(ToqueResumen.Revision(RevisionResumen.REVISADAS))
            esperar { it.filtro.cantidad == 2 }
            viewModel.elegirPeriodo(PeriodoResumen.TREINTA_DIAS)
            val sinDia = esperar { it.periodo == PeriodoResumen.TREINTA_DIAS }
            assertEquals(FiltroResumen(revision = RevisionResumen.REVISADAS), sinDia.filtro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `elegir fechas cambia lo que abarca y saca los dias elegidos, y si son 7 dias marca ese atajo`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()
        val hoy = LocalDate.of(2026, 10, 12)

        viewModel.estado.test {
            esperar { it.datos != null }
            viewModel.tocar(ToqueResumen.Barra(Franja.dia(hoy)))
            viewModel.tocar(ToqueResumen.Tipo(TipoAtaque.ESCANEO))
            esperar { it.filtro.cantidad == 2 }

            // Hoy de 00:00 a 11:59: va por hora.
            val manana = RangoResumen(hoy.atStartOfDay(), hoy.atTime(11, 59))
            viewModel.elegirFechas(manana)
            val conFechas = esperar { it.datos?.resumen?.rango == manana }
            assertEquals(manana, conFechas.fechas)
            assertEquals(FiltroResumen(tipos = setOf(TipoAtaque.ESCANEO)), conFechas.filtro)
            assertEquals(EscalaBarra.HORA, conFechas.datos!!.resumen.escala)

            viewModel.elegirFechas(PeriodoResumen.SIETE_DIAS.rango(hoy))
            val siete = esperar { it.fechas == null && it.datos?.resumen?.periodo == PeriodoResumen.SIETE_DIAS }
            assertEquals(PeriodoResumen.SIETE_DIAS, siete.periodo)

            viewModel.elegirFechas(manana)
            esperar { it.fechas == manana }
            viewModel.elegirPeriodo(PeriodoResumen.SIETE_DIAS)
            val deNuevo = esperar { it.fechas == null && it.datos?.resumen?.periodo == PeriodoResumen.SIETE_DIAS }
            assertEquals(null, deNuevo.fechas)
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
