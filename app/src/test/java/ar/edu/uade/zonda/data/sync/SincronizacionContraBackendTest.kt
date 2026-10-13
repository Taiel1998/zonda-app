package ar.edu.uade.zonda.data.sync

import ar.edu.uade.zonda.data.FakeAlmacenSeguro
import ar.edu.uade.zonda.data.PruebaConBase
import ar.edu.uade.zonda.data.remote.ProveedorDeApiRetrofit
import ar.edu.uade.zonda.data.repository.AccionesPendientesRepositoryImpl
import ar.edu.uade.zonda.data.repository.AlertasRepositoryImpl
import ar.edu.uade.zonda.data.repository.RedesRepositoryImpl
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueba manual contra la API real, con el cliente HTTP de verdad (Retrofit + Gson + OkHttp).
 * No corre en CI: se salta si no están las variables de entorno. Para correrla, con el backend
 * levantado y un código nuevo de `zonda.cmd pair`:
 *
 *     $env:ZONDA_API_URL = "http://127.0.0.1:8000"; $env:ZONDA_CODIGO_PAREO = "K7M2-QX9P"
 *     .\gradlew.bat testDebugUnitTest --tests "*ContraBackend*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SincronizacionContraBackendTest : PruebaConBase() {

    @Test
    fun `pareo, sincronizacion completa, revision y segunda sincronizacion contra la API local`() = runTest {
        val url = System.getenv("ZONDA_API_URL")
        val codigo = System.getenv("ZONDA_CODIGO_PAREO")
        assumeTrue("Definí ZONDA_API_URL y ZONDA_CODIGO_PAREO para correr esta prueba", url != null && codigo != null)

        val almacen = FakeAlmacenSeguro()
        val apis = ProveedorDeApiRetrofit(tokens = { almacen.leer(RedesRepositoryImpl.claveToken(it)) })
        val redes = RedesRepositoryImpl(base.redesDao(), almacen, apis)
        val alertas = AlertasRepositoryImpl(base.alertasDao(), SincronizadorHttp(base, apis))
        val acciones = AccionesPendientesRepositoryImpl(base.accionesPendientesDao())

        val red = redes.parear(DatosPareo(url!!, codigo!!), "Prueba desde la JVM").red
        println("Pareada la red ${red.nombre} (${red.id}) en ${red.urlBase}")

        val primera = alertas.sincronizar(red.id)
        val lista = alertas.observarAlertas(red.id, FiltroAlertas.TODAS).first()
        println("Primera sincronización: ${primera.nuevas} nuevas, ${primera.altasNuevas} altas; cursor ${redes.observarRedActiva().first()?.cursor}")
        assertTrue(primera.nuevas > 0)
        assertEquals(primera.nuevas, lista.size)
        assertTrue(redes.observarCatalogo(red.id).first().servicios.isNotEmpty())

        val sinRevisar = lista.first { it.sinRevisar }
        RevisarAlerta(alertas, acciones, GeneradorDeIds { UUID.randomUUID().toString() })(
            red.id, sinRevisar.id, ResultadoRevision.FALSO_POSITIVO, Motivo.TAREA_PROGRAMADA, "Respaldo nocturno (prueba desde la JVM)",
        )

        val segunda = alertas.sincronizar(red.id)
        println("Segunda sincronización: ${segunda.accionesEnviadas} enviadas, ${segunda.modificadas} modificadas")
        assertEquals(1, segunda.accionesEnviadas)
        assertEquals(0, segunda.accionesConError)
        assertTrue(acciones.observar(red.id).first().isEmpty())
        val revisada = alertas.observarAlerta(red.id, sinRevisar.id).first()!!
        assertEquals(listOf(OrigenRevision.SERVIDOR), revisada.historial.map { it.origen })
        assertEquals("Prueba desde la JVM", revisada.revisionActual?.autor)
        assertEquals(2, revisada.version)
    }
}
