package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccionesPendientesRepositoryImplTest : PruebaConBase() {

    private lateinit var acciones: AccionesPendientesRepositoryImpl
    private lateinit var alertas: AlertasRepositoryImpl

    @Before
    fun preparar() {
        acciones = AccionesPendientesRepositoryImpl(base.accionesPendientesDao())
        alertas = AlertasRepositoryImpl(base.alertasDao())
    }

    @Test
    fun `una accion de revision se guarda y se lee igual`() = runTest {
        conRed()
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1")))
        val accion = Fixtures.accion(id = "accion-1", alertaId = "a-1")

        acciones.encolar(accion)

        assertEquals(listOf(accion), acciones.observar("red-1").first())
        assertEquals(accion, acciones.deAlerta("red-1", "a-1"))
    }

    @Test
    fun `una accion de severidades guarda la tabla completa`() = runTest {
        conRed()
        val severidades = SeveridadPorTipo.RECOMENDADA.con(TipoAtaque.ESCANEO, Severidad.ALTA)
        val accion = Fixtures.accion(id = "accion-sev", alertaId = null, revision = null).copy(tipo = TipoAccion.CAMBIAR_SEVERIDADES, severidades = severidades, versionVista = null)

        acciones.encolar(accion)

        val leida: AccionPendiente = acciones.observar("red-1").first().single()
        assertEquals(accion, leida)
        assertEquals(Severidad.ALTA, leida.severidades?.severidadDe(TipoAtaque.ESCANEO))
    }

    @Test
    fun `la segunda accion sobre la misma alerta reemplaza a la primera`() = runTest {
        conRed()
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1")))
        acciones.encolar(Fixtures.accion(id = "accion-1", alertaId = "a-1"))

        acciones.encolar(Fixtures.accion(id = "accion-2", alertaId = "a-1"))

        assertEquals(listOf("accion-2"), acciones.observar("red-1").first().map { it.id })
    }

    @Test
    fun `quitar y actualizar el envio`() = runTest {
        conRed()
        alertas.guardarDelServidor(listOf(Fixtures.alerta("a-1"), Fixtures.alerta("a-2")))
        acciones.encolar(Fixtures.accion(id = "accion-1", alertaId = "a-1"))
        acciones.encolar(Fixtures.accion(id = "accion-2", alertaId = "a-2"))

        acciones.actualizarEnvio("accion-2", EstadoEnvio.ERROR, intentos = 5, ultimoError = "tiempo de espera agotado")
        acciones.quitar("accion-1")

        val restantes = acciones.listar("red-1")
        assertEquals(listOf("accion-2"), restantes.map { it.id })
        assertEquals(EstadoEnvio.ERROR, restantes.single().estado)
        assertEquals(5, restantes.single().intentos)
        assertNull(acciones.deAlerta("red-1", "a-1"))
        assertTrue(acciones.observar("otra-red").first().isEmpty())
    }
}
