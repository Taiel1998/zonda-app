package ar.edu.uade.zonda.ui.detalle

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.fakes.FakeAccionesPendientesRepository
import ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository
import ar.edu.uade.zonda.domain.fakes.FakeBloqueosRepository
import ar.edu.uade.zonda.domain.fakes.FakeBloqueosRepository.Companion.bloqueo
import ar.edu.uade.zonda.domain.fakes.FakeConectividad
import ar.edu.uade.zonda.domain.fakes.FakeRedesRepository
import ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.usecase.AsignarAlerta
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.GestionarBloqueos
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import ar.edu.uade.zonda.domain.usecase.ResolverEnvioRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ReglaDispatcherPrincipal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La tarjeta y la hoja del bloqueo del origen en el detalle (decisión 51), con el servidor en memoria. */
@OptIn(ExperimentalCoroutinesApi::class)
class BloqueoDetalleViewModelTest {

    @get:Rule
    val dispatcherPrincipal = ReglaDispatcherPrincipal()

    private val alertas = FakeAlertasRepository()
    private val acciones = FakeAccionesPendientesRepository()
    private val conectividad = FakeConectividad()
    private val bloqueos = FakeBloqueosRepository()

    private fun viewModel(alcance: CoroutineScope, perfil: Perfil = Perfil.ADMINISTRADOR): DetalleViewModel {
        val redes = FakeRedesRepository(listOf(Fixtures.red("red-1").copy(perfil = perfil)))
        return DetalleViewModel(
            obtenerDetalle = ObtenerDetalleAlerta(alertas, conectividad),
            revisarAlerta = RevisarAlerta(alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
            asignarAlerta = AsignarAlerta(redes, alertas, acciones, GeneradorDeIdsFijo(), reloj = { Fixtures.AHORA }),
            ventana = VentanaDeshacer(DeshacerRevision(alertas, acciones), SincronizarAlertas(redes, alertas), conectividad, alcance),
            resolverEnvio = ResolverEnvioRevision(alertas, acciones),
            sincronizarAlertas = SincronizarAlertas(redes, alertas),
            redes = redes,
            acciones = acciones,
            conectividad = conectividad,
            alcanceDeEnvio = alcance,
            alertaId = "a-1",
            gestionarBloqueos = GestionarBloqueos(bloqueos, GeneradorDeIdsFijo()),
        )
    }

    private suspend fun ReceiveTurbine<DetalleUiState>.esperar(condicion: (DetalleUiState.Contenido) -> Boolean): DetalleUiState.Contenido {
        var estado = awaitItem()
        while (estado !is DetalleUiState.Contenido || !condicion(estado)) estado = awaitItem()
        return estado
    }

    @Test
    fun `con un firewall conectado el detalle trae la tarjeta del origen sin bloquear`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))

        viewModel(backgroundScope).estado.test {
            val estado = esperar { it.bloqueo != null }

            assertNull(estado.bloqueo?.actual)
            assertFalse(estado.bloqueo!!.nuncaSeBloquea)
            assertTrue(estado.bloqueo!!.firewall.conectado)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `en modo de observacion no hay tarjeta del bloqueo`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        bloqueos.firewall = bloqueos.firewall.copy(habilitado = false)

        viewModel(backgroundScope).estado.test {
            val estado = esperar { true }
            runCurrent()

            assertEquals(1, bloqueos.consultas)
            assertNull(estado.bloqueo)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un origen en la lista de nunca bloquear no ofrece el boton`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        bloqueos.firewall = bloqueos.firewall.copy(nuncaBloquear = listOf("192.168.10.0/24"))

        viewModel(backgroundScope).estado.test {
            assertTrue(esperar { it.bloqueo != null }.bloqueo!!.nuncaSeBloquea)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un administrador bloquea con el motivo propuesto y la tarjeta pasa a bloqueado`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val vm = viewModel(backgroundScope)

        vm.estado.test {
            esperar { it.bloqueo != null }
            vm.abrirBloqueo("Fuerza bruta de pc-09 a srv-web.")
            assertEquals(DuracionBloqueo.UNA_HORA, vm.hojaBloqueo.value?.duracion)
            vm.elegirDuracion(DuracionBloqueo.QUINCE_MINUTOS)
            vm.confirmarBloqueo()

            val bloqueado = esperar { it.bloqueo?.actual != null }
            assertEquals(listOf("bloquear a-1 QUINCE_MINUTOS Fuerza bruta de pc-09 a srv-web."), bloqueos.hecho)
            assertEquals(EstadoBloqueo.APROBADO, bloqueado.bloqueo?.actual?.estado)
            assertNull(vm.hojaBloqueo.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `despues de bloquear se vuelve a mirar mientras el firewall lo aplica`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val vm = viewModel(backgroundScope)
        runCurrent()
        val antes = bloqueos.consultas

        vm.abrirBloqueo("Fuerza bruta.")
        vm.confirmarBloqueo()
        runCurrent()
        advanceTimeBy(25_000)
        runCurrent()

        // Una en el momento y otras tres a los 4, 12 y 24 segundos; después, nada más.
        assertEquals(antes + 4, bloqueos.consultas)
        advanceTimeBy(60_000)
        assertEquals(antes + 4, bloqueos.consultas)
    }

    @Test
    fun `sin motivo no sale y la hoja marca el error`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val vm = viewModel(backgroundScope)

        vm.abrirBloqueo("")
        vm.cambiarMotivoBloqueo("   ")
        vm.confirmarBloqueo()

        assertTrue(vm.hojaBloqueo.value!!.conError)
        assertTrue(bloqueos.hecho.isEmpty())
        vm.cambiarMotivoBloqueo("Fuerza bruta.")
        assertFalse(vm.hojaBloqueo.value!!.conError)
    }

    @Test
    fun `sin conexion no sale, no queda en la cola y lo avisa`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val vm = viewModel(backgroundScope)
        conectividad.estado.value = false

        vm.abrirBloqueo("Fuerza bruta.")
        vm.confirmarBloqueo()
        runCurrent()

        assertEquals(AvisoDetalle.BloqueoSinConexion, vm.aviso.value)
        assertTrue(bloqueos.hecho.isEmpty())
        assertTrue(acciones.todas.isEmpty())
        assertNull(vm.hojaBloqueo.value)
    }

    @Test
    fun `si el servidor no deja bloquear el aviso trae el porque`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        val vm = viewModel(backgroundScope)
        runCurrent()
        val porque = ErrorZonda.BloqueoNoPosible("Ese equipo ya tiene un bloqueo o un pedido de bloqueo")
        bloqueos.error = porque

        vm.abrirBloqueo("Fuerza bruta.")
        vm.confirmarBloqueo()
        runCurrent()

        assertEquals(AvisoDetalle.Error(porque), vm.aviso.value)
        assertNull(vm.hojaBloqueo.value)
    }

    @Test
    fun `un analista pide el bloqueo y queda pedido`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        bloqueos.administrador = false
        val vm = viewModel(backgroundScope, perfil = Perfil.ANALISTA)

        vm.estado.test {
            esperar { it.bloqueo != null }
            vm.abrirBloqueo("Fuerza bruta.")
            vm.confirmarBloqueo()

            assertEquals(EstadoBloqueo.PEDIDO, esperar { it.bloqueo?.actual != null }.bloqueo?.actual?.estado)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un administrador aprueba un pedido sin hoja y rechaza otro con su motivo`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        bloqueos.bloqueos += bloqueo(id = "b-1", estado = EstadoBloqueo.PEDIDO, pedidoPor = "Laura")
        val vm = viewModel(backgroundScope)
        runCurrent()

        vm.aprobarBloqueo()
        runCurrent()
        assertEquals(listOf("aprobar b-1"), bloqueos.hecho)

        bloqueos.bloqueos[0] = bloqueo(id = "b-1", estado = EstadoBloqueo.PEDIDO, pedidoPor = "Laura")
        vm.abrirRechazo()
        vm.cambiarMotivoBloqueo("Es el backup nocturno.")
        vm.confirmarBloqueo()
        runCurrent()
        assertEquals("rechazar Es el backup nocturno. b-1", bloqueos.hecho.last())
    }

    @Test
    fun `cambiar la duracion abre con la que tiene y desbloquear usa el bloqueo del origen`() = runTest {
        alertas.agregar(Fixtures.alerta("a-1"))
        bloqueos.bloqueos += bloqueo(id = "b-7", estado = EstadoBloqueo.APLICADO, segundos = 3600)
        val vm = viewModel(backgroundScope)
        runCurrent()

        vm.abrirCambioDeDuracion()
        runCurrent()
        assertEquals(AccionBloqueo.CAMBIAR_DURACION, vm.hojaBloqueo.value?.accion)
        assertEquals(DuracionBloqueo.UNA_HORA, vm.hojaBloqueo.value?.duracion)
        vm.elegirDuracion(DuracionBloqueo.OCHO_HORAS)
        vm.cambiarMotivoBloqueo("Sigue el ataque.")
        vm.confirmarBloqueo()
        runCurrent()
        assertEquals("duracion OCHO_HORAS Sigue el ataque. b-7", bloqueos.hecho.last())

        vm.abrirDesbloqueo()
        vm.cambiarMotivoBloqueo("Se cambió la contraseña.")
        vm.confirmarBloqueo()
        runCurrent()
        assertEquals("desbloquear Se cambió la contraseña. b-7", bloqueos.hecho.last())
    }
}
