package ar.edu.uade.zonda.ui.estado

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.ObtenerEstadoRed
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La pantalla Estado (RF01) desde su ViewModel, con los repositorios en memoria. */
class EstadoViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val redes = FakeRedesRepository()
    private val alertas = FakeAlertasRepository()
    private val conectividad = FakeConectividad()
    private val acciones = FakeAccionesPendientesRepository()

    private fun viewModel() = EstadoViewModel(
        obtenerEstadoRed = ObtenerEstadoRed(redes, alertas),
        observarAlertas = ObservarAlertas(redes, alertas),
        sincronizarAlertas = SincronizarAlertas(redes, alertas),
        redes = redes,
        alertas = alertas,
        conectividad = conectividad,
        acciones = acciones,
    )

    private suspend fun ReceiveTurbine<EstadoUiState>.esperar(condicion: (EstadoUiState) -> Boolean): EstadoUiState {
        var estado = awaitItem()
        while (!condicion(estado)) estado = awaitItem()
        return estado
    }

    private val EstadoUiState.datos: ContenidoEstado.Datos
        get() = contenido as ContenidoEstado.Datos

    private val hace5Minutos = Fixtures.AHORA.minus(Duration.ofMinutes(5))

    /** Dos altas, una media y una baja sin revisar (la baja es la más nueva) y una alta ya revisada. */
    private fun conRedYAlertas() {
        redes.agregar(Fixtures.red("red-1"))
        alertas.agregar(
            Fixtures.alerta("alta-nueva", severidad = Severidad.ALTA, creadaEn = Fixtures.AHORA, inicio = hace5Minutos),
            Fixtures.alerta("alta-vieja", severidad = Severidad.ALTA, creadaEn = Fixtures.AHORA.minus(Duration.ofHours(2))),
            Fixtures.alerta("media", severidad = Severidad.MEDIA, creadaEn = Fixtures.AHORA.minus(Duration.ofHours(1))),
            Fixtures.alerta("baja", severidad = Severidad.BAJA, creadaEn = Fixtures.AHORA, inicio = Fixtures.AHORA.minus(Duration.ofMinutes(2))),
            Fixtures.alerta("alta-revisada", severidad = Severidad.ALTA, revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR)),
        )
    }

    private fun resultado(nuevas: Int = 0, hora: Instant = Fixtures.AHORA) =
        ResultadoSincronizacion(nuevas = nuevas, modificadas = 0, altasNuevas = 0, accionesEnviadas = 0, accionesConError = 0, hora = hora)

    @Test
    fun `con datos muestra el semaforo, los contadores y las sin revisar de la mas reciente a la mas vieja`() = runTest {
        conRedYAlertas()

        viewModel().estado.test {
            val estado = esperar { it.contenido is ContenidoEstado.Datos }

            assertEquals(Semaforo.ROJO, estado.datos.semaforo)
            assertEquals(ContadoresSinRevisar(altas = 2, medias = 1, bajas = 1), estado.datos.sinRevisar)
            assertEquals(listOf("baja", "alta-nueva", "media", "alta-vieja"), estado.datos.alertas.map { it.id })
            assertEquals(Fixtures.AHORA, estado.datosDe)
            assertFalse(estado.sinConexion)
            assertNull(estado.severidadElegida)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la ultima deteccion es la de la severidad que pone el color, no la de una baja mas nueva`() = runTest {
        conRedYAlertas()

        viewModel().estado.test {
            assertEquals(hace5Minutos, esperar { it.contenido is ContenidoEstado.Datos }.datos.ultimaDeteccion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la regla de la ultima deteccion se puede probar sola`() {
        val alta = Fixtures.alerta("alta", severidad = Severidad.ALTA, inicio = Fixtures.AHORA.minus(Duration.ofHours(3)))
        val media = Fixtures.alerta("media", severidad = Severidad.MEDIA, inicio = Fixtures.AHORA.minus(Duration.ofHours(1)))
        val baja = Fixtures.alerta("baja", severidad = Severidad.BAJA, inicio = Fixtures.AHORA)

        assertEquals(alta.inicio, EstadoViewModel.ultimaDeteccion(Semaforo.ROJO, listOf(alta, media, baja)))
        assertEquals(media.inicio, EstadoViewModel.ultimaDeteccion(Semaforo.AMARILLO, listOf(media, baja)))
        assertEquals(baja.inicio, EstadoViewModel.ultimaDeteccion(Semaforo.VERDE, listOf(baja)))
        assertNull(EstadoViewModel.ultimaDeteccion(Semaforo.VERDE, emptyList()))
        assertNull(EstadoViewModel.ultimaDeteccion(Semaforo.SIN_DATOS, listOf(alta)))
    }

    @Test
    fun `tocar un contador deja solo las sin revisar de esa severidad y otro toque muestra todas`() = runTest {
        conRedYAlertas()
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.contenido is ContenidoEstado.Datos }

            viewModel.tocarContador(Severidad.ALTA)
            val altas = esperar { it.severidadElegida == Severidad.ALTA }
            assertEquals(listOf("alta-nueva", "alta-vieja"), altas.datos.alertas.map { it.id })
            // Los contadores y el semáforo no cambian: siguen contando todas las sin revisar.
            assertEquals(ContadoresSinRevisar(altas = 2, medias = 1, bajas = 1), altas.datos.sinRevisar)
            assertEquals(Semaforo.ROJO, altas.datos.semaforo)

            viewModel.tocarContador(Severidad.MEDIA)
            assertEquals(listOf("media"), esperar { it.severidadElegida == Severidad.MEDIA }.datos.alertas.map { it.id })

            viewModel.tocarContador(Severidad.MEDIA)
            assertEquals(4, esperar { it.severidadElegida == null }.datos.alertas.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `al revisar las altas desde el detalle el semaforo pasa solo a amarillo`() = runTest {
        conRedYAlertas()

        viewModel().estado.test {
            esperar { (it.contenido as? ContenidoEstado.Datos)?.semaforo == Semaforo.ROJO }

            alertas.aplicarRevisionLocal("red-1", "alta-nueva", Fixtures.revision())
            alertas.aplicarRevisionLocal("red-1", "alta-vieja", Fixtures.revision())

            // Los contadores y la lista son dos consultas: se espera a que lleguen las dos.
            val estado = esperar { (it.contenido as? ContenidoEstado.Datos)?.let { d -> d.semaforo == Semaforo.AMARILLO && d.alertas.size == 2 } == true }
            assertEquals(listOf("baja", "media"), estado.datos.alertas.map { it.id })
            assertEquals(Fixtures.AHORA.minus(Duration.ofHours(1)).minus(Duration.ofMinutes(10)), estado.datos.ultimaDeteccion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `solo con bajas sin revisar el semaforo es verde y cuenta la ultima baja`() = runTest {
        redes.agregar(Fixtures.red("red-1"))
        val baja = Fixtures.alerta("baja", severidad = Severidad.BAJA)
        alertas.agregar(baja)

        viewModel().estado.test {
            val datos = esperar { it.contenido is ContenidoEstado.Datos }.datos
            assertEquals(Semaforo.VERDE, datos.semaforo)
            assertEquals(baja.inicio, datos.ultimaDeteccion)
            assertEquals(listOf("baja"), datos.alertas.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una red que nunca se sincronizo muestra sin datos y nunca un semaforo verde`() = runTest {
        redes.agregar(Fixtures.red("red-1", ultimaSincronizacion = null))

        viewModel().estado.test {
            val estado = esperar { it.contenido !is ContenidoEstado.Cargando }
            assertEquals(ContenidoEstado.SinDatos, estado.contenido)
            assertNull(estado.datosDe)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mientras baja la primera descarga muestra cuantas van y al terminar el semaforo`() = runTest {
        redes.agregar(Fixtures.red("red-1", ultimaSincronizacion = null))
        alertas.progresoDescarga.value = ProgresoDescarga("red-1", descargadas = 120, total = 213)

        viewModel().estado.test {
            assertEquals(ContenidoEstado.Descargando(120, 213), esperar { it.contenido is ContenidoEstado.Descargando }.contenido)

            alertas.agregar(Fixtures.alerta("media", severidad = Severidad.MEDIA))
            redes.actualizar(Fixtures.red("red-1"))
            alertas.progresoDescarga.value = null

            assertEquals(Semaforo.AMARILLO, esperar { it.contenido is ContenidoEstado.Datos }.datos.semaforo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin red pareada muestra SinRed y no intenta sincronizar`() = runTest {
        viewModel().estado.test {
            assertEquals(ContenidoEstado.SinRed, esperar { it.contenido !is ContenidoEstado.Cargando }.contenido)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `al abrir con conexion sincroniza sola, sin hacer girar el icono ni avisar`() = runTest {
        conRedYAlertas()
        alertas.resultadoSincronizacion = resultado(nuevas = 3)

        viewModel().estado.test {
            val estado = esperar { it.contenido is ContenidoEstado.Datos }
            assertNull(estado.aviso)
            assertFalse(estado.sincronizando)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `sincronizar a pedido avisa con la fecha y la hora`() = runTest {
        conRedYAlertas()
        val hora = Fixtures.AHORA.plusSeconds(60)
        alertas.resultadoSincronizacion = resultado(nuevas = 2, hora = hora)
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.contenido is ContenidoEstado.Datos }

            viewModel.sincronizar()
            assertEquals(AvisoEstado.Sincronizado(hora, nuevas = 2), esperar { it.aviso != null }.aviso)

            viewModel.avisoMostrado()
            esperar { it.aviso == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("red-1", "red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `pedir sincronizar mientras corre la automatica la hace propia y avisa al terminar`() = runTest {
        conRedYAlertas()
        val espera = CompletableDeferred<Unit>()
        alertas.esperaAlSincronizar = espera
        val viewModel = viewModel()

        viewModel.estado.test {
            assertFalse("la automática no hace girar el ícono", esperar { it.contenido is ContenidoEstado.Datos }.sincronizando)

            viewModel.sincronizar()
            esperar { it.sincronizando }

            espera.complete(Unit)
            val terminado = esperar { it.aviso != null }
            assertTrue(terminado.aviso is AvisoEstado.Sincronizado)
            assertFalse(terminado.sincronizando)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("no lanza una segunda sincronización", listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `sin conexion la automatica no avisa, pero la pedida avisa el error para reintentar`() = runTest {
        conRedYAlertas()
        alertas.errorAlSincronizar = ErrorZonda.SinConexion()
        val viewModel = viewModel()

        viewModel.estado.test {
            assertNull(esperar { it.contenido is ContenidoEstado.Datos }.aviso)

            viewModel.sincronizar()
            val estado = esperar { it.aviso != null }
            assertTrue((estado.aviso as AvisoEstado.Error).error is ErrorZonda.SinConexion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un error de la automatica que no es falta de conexion se avisa igual`() = runTest {
        conRedYAlertas()
        alertas.errorAlSincronizar = ErrorZonda.ServidorNoVerificado()

        viewModel().estado.test {
            val estado = esperar { it.aviso != null }
            assertTrue((estado.aviso as AvisoEstado.Error).error is ErrorZonda.ServidorNoVerificado)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sin conexion muestra el ultimo estado guardado con su hora y no intenta sincronizar`() = runTest {
        conRedYAlertas()
        conectividad.estado.value = false

        viewModel().estado.test {
            val estado = esperar { it.contenido is ContenidoEstado.Datos }
            assertTrue(estado.sinConexion)
            assertEquals(Fixtures.AHORA, estado.datosDe)
            assertEquals(Semaforo.ROJO, estado.datos.semaforo)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(alertas.sincronizaciones.isEmpty())
    }

    @Test
    fun `al volver la conexion sincroniza sola`() = runTest {
        conRedYAlertas()
        conectividad.estado.value = false
        val viewModel = viewModel()

        viewModel.estado.test {
            esperar { it.sinConexion && it.contenido is ContenidoEstado.Datos }
            conectividad.estado.value = true
            esperar { !it.sinConexion }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("red-1"), alertas.sincronizaciones)
    }

    @Test
    fun `con el acceso vencido lo marca para conectar de nuevo y sigue mostrando lo guardado`() = runTest {
        redes.agregar(Fixtures.red("red-1", accesoVencido = true))
        alertas.agregar(Fixtures.alerta("alta", severidad = Severidad.ALTA))

        viewModel().estado.test {
            val estado = esperar { it.contenido is ContenidoEstado.Datos }
            assertTrue(estado.accesoVencido)
            assertEquals(Semaforo.ROJO, estado.datos.semaforo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una revision de este telefono en conflicto aparece en Estado hasta que se elige cual queda`() = runTest {
        conRedYAlertas()
        acciones.encolar(Fixtures.accion(id = "accion-1", alertaId = "alta-vieja", estado = EstadoEnvio.CONFLICTO))
        acciones.encolar(Fixtures.accion(id = "accion-2", alertaId = "media", estado = EstadoEnvio.PENDIENTE))

        viewModel().estado.test {
            assertEquals(listOf("alta-vieja"), esperar { it.revisionesNoAplicadas.isNotEmpty() }.revisionesNoAplicadas)

            // "Aplicar la mía" la vuelve a encolar como pendiente: el aviso se va.
            acciones.encolar(Fixtures.accion(id = "accion-1", alertaId = "alta-vieja", estado = EstadoEnvio.PENDIENTE))
            assertEquals(emptyList<String>(), esperar { it.revisionesNoAplicadas.isEmpty() }.revisionesNoAplicadas)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `las sin revisar asignadas a este telefono arman el aviso azul`() = runTest {
        redes.agregar(Fixtures.red("red-1").copy(dispositivoId = "d-2"))
        alertas.agregar(
            Fixtures.alerta("mia").copy(asignacion = Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA)),
            Fixtures.alerta("de-meli").copy(asignacion = Asignacion("d-3", "Meli", "d-1", "Taiel", Fixtures.AHORA)),
            Fixtures.alerta("libre"),
            Fixtures.alerta("mia-revisada", revision = Fixtures.revision(origen = OrigenRevision.SERVIDOR))
                .copy(asignacion = Asignacion("d-2", "Laura", "d-1", "Taiel", Fixtures.AHORA)),
        )

        viewModel().estado.test {
            val estado = esperar { it.asignadasAMi.isNotEmpty() }
            assertEquals(listOf("mia"), estado.asignadasAMi.map { it.id })
            assertEquals("d-2", estado.contexto.miId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
