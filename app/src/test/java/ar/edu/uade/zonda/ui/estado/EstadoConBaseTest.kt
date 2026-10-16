package ar.edu.uade.zonda.ui.estado

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ApiFalsa
import ar.edu.uade.zonda.data.remote.ProveedorDeApiFalso
import ar.edu.uade.zonda.data.repository.AccionesPendientesRepositoryImpl
import ar.edu.uade.zonda.data.repository.AlertasRepositoryImpl
import ar.edu.uade.zonda.data.repository.RedesRepositoryImpl
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.ObtenerEstadoRed
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RF01, criterio 1: con datos ya sincronizados, el estado aparece en menos de 1 segundo. Se prueba con la
 * base de verdad (Room en memoria) y más alertas de las que junta una red en 30 días, sin conexión para
 * que no haya ninguna sincronización en el medio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EstadoConBaseTest : PruebaConBase() {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    @Test
    fun `con 500 alertas guardadas el estado aparece en menos de un segundo`() = runTest {
        conRed()
        val alertas = AlertasRepositoryImpl(base.alertasDao(), reloj = { Fixtures.AHORA })
        alertas.guardarDelServidor(
            (1..500).map { i ->
                Fixtures.alerta(
                    id = "a-$i",
                    severidad = Severidad.entries[i % 3],
                    creadaEn = Fixtures.AHORA.minus(Duration.ofMinutes(i * 80L)),
                    // Una de cada cuatro ya está revisada.
                    revision = if (i % 4 == 0) Fixtures.revision(origen = OrigenRevision.SERVIDOR) else null,
                )
            },
        )
        val redes = RedesRepositoryImpl(base.redesDao(), FakeAlmacenSeguro(), ProveedorDeApiFalso(ApiFalsa()), reloj = { Fixtures.AHORA })
        val viewModel = EstadoViewModel(
            obtenerEstadoRed = ObtenerEstadoRed(redes, alertas),
            observarAlertas = ObservarAlertas(redes, alertas),
            sincronizarAlertas = SincronizarAlertas(redes, alertas),
            redes = redes,
            alertas = alertas,
            conectividad = FakeConectividad(conectadoAlInicio = false),
            acciones = AccionesPendientesRepositoryImpl(base.accionesPendientesDao()),
        )

        val inicio = System.nanoTime()
        val estado = viewModel.estado.first { it.contenido is ContenidoEstado.Datos }
        val milisegundos = (System.nanoTime() - inicio) / 1_000_000
        println("Estado con 500 alertas guardadas: $milisegundos ms")

        assertTrue("tardó $milisegundos ms", milisegundos < 1_000)
        val datos = estado.contenido as ContenidoEstado.Datos
        assertEquals(Semaforo.ROJO, datos.semaforo)
        assertEquals(375, datos.sinRevisar.total)
        assertEquals(375, datos.alertas.size)
    }
}
