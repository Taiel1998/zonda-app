package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.AvisoRecibido
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.Duration
import java.time.Instant
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

/** El detalle, la revisión y sus salidas dibujados con Compose sobre Robolectric (Figma 3-3 a 3-139). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetalleContenidoTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    /** La revisión del servidor, hecha sin conexión a las 11:10 y llegada a las 11:16. */
    private val revisionDelServidor = Fixtures.revision(fecha = Fixtures.AHORA.minus(Duration.ofMinutes(44)), origen = OrigenRevision.SERVIDOR)
        .copy(autor = "Celular Martín", revisadaEn = Fixtures.AHORA.minus(Duration.ofMinutes(50)), comentario = "Avisé al proveedor.")

    private fun dibujar(
        estado: DetalleUiState,
        acciones: AccionesDetalle = AccionesDetalle(),
        aviso: AvisoDetalle? = null,
    ) {
        compose.setContent {
            ZondaTheme { DetalleContenido(estado = estado, acciones = acciones, aviso = aviso, zona = utc, ahora = Fixtures.AHORA) }
        }
    }

    private fun contenido(
        alerta: ar.edu.uade.zonda.domain.model.Alerta,
        accion: ar.edu.uade.zonda.domain.model.AccionPendiente? = null,
        sinConexion: Boolean = false,
        recienRevisada: Boolean = false,
    ) = DetalleUiState.Contenido(
        alerta,
        accion,
        sinConexion,
        datosDe = Fixtures.AHORA,
        recienRevisada = recienRevisada,
        nombreDelTelefono = "Celular Martín",
        nombreDeLaRed = "Oficina central",
    )

    @Test
    fun `sin revisar muestra el encabezado, los datos y Revisar, y comparte sin IP ni nombres`() {
        var abierta = false
        var compartido = ""
        dibujar(
            contenido(Fixtures.alerta("a-1").copy(protocolo = "tcp")),
            AccionesDetalle(alRevisar = { abierta = true }, alCompartir = { compartido = it }),
        )

        compose.onNodeWithText("pc-09 → srv-web").assertIsDisplayed()
        compose.onNodeWithText("SSH · TCP/22 · 10:50:00 · detectada por IA").assertIsDisplayed()
        compose.onNodeWithText("Datos del flujo").assertIsDisplayed()
        compose.onNodeWithText("Veredicto por método").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Revisar").performScrollTo().performClick()
        assertTrue(abierta)

        compose.onNodeWithContentDescription("Compartir").performClick()
        // El mismo texto que el aviso de Telegram, con el enlace que abre la alerta (RNF02).
        assertTrue(compartido, compartido.startsWith("Alerta ALTA · SSH · 10:50\nTocá para ver el detalle en Zonda.\nRed: Oficina central\n"))
        assertTrue(
            compartido,
            compartido.endsWith("https://taiel1998.github.io/a/a-1?red=red-1&severidad=alta&servicio=ssh&deteccion=2026-10-12T10%3A50%3A00Z"),
        )
        assertFalse(compartido, "192.168" in compartido || "srv-web" in compartido || "pc-09" in compartido)
    }

    @Test
    fun `cada metodo dice con palabras cuanto se paso, sin el umbral ni los numeros con ceros`() {
        // IA: 0,0412 / 0,0031 = 13 veces; umbrales: 65 conexiones contra 2 = 33 veces; reglas: servicio autorizado.
        dibujar(contenido(Fixtures.alerta("a-1", metodos = setOf(MetodoDeteccion.IA, MetodoDeteccion.UMBRALES))))

        compose.onNodeWithText("Conexiones en total").assertIsDisplayed()
        compose.onNodeWithText("13 veces más de lo normal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("33 veces más de lo normal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Servicio autorizado").performScrollTo().assertIsDisplayed()
        // Antes: "error 0,0412 · umbral 0,0031" y "contexto 65 · umbral 2".
        compose.onAllNodesWithText("· umbral", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("contexto", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("0,0", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Flujos del episodio").assertCountEquals(0)
    }

    @Test
    fun `seis terminos tienen su signo de pregunta, que abre un globito por vez`() {
        dibujar(contenido(Fixtures.alerta("a-1")))
        val ayudaIa = "Aprendió cómo es el tráfico normal de tu red y marca lo que se parece poco a eso."
        val ayudaUmbrales = "Cuentan las conexiones a un mismo servicio en un minuto y marcan si son más que en un día normal."

        listOf("Conexiones en 60 s", "Conexiones en total", "Veredicto por método", "Modelo de IA", "Umbrales estadísticos", "Reglas estáticas")
            .forEach { termino -> compose.onNodeWithContentDescription("Qué es $termino").assertHasClickAction() }
        compose.onAllNodesWithText(ayudaIa).assertCountEquals(0)

        compose.onNodeWithContentDescription("Qué es Modelo de IA").performScrollTo().performClick()
        compose.onNodeWithText(ayudaIa).assertIsDisplayed()

        // Abrir otro cierra el primero: uno por vez.
        compose.onNodeWithContentDescription("Qué es Umbrales estadísticos").performScrollTo().performClick()
        compose.onNodeWithText(ayudaUmbrales).assertIsDisplayed()
        compose.onNodeWithText(ayudaIa).assertDoesNotExist()
    }

    @Test
    fun `revisada muestra arriba la revision y el historial con las dos horas`() {
        var cambiar = false
        dibujar(contenido(Fixtures.alerta("a-1", revision = revisionDelServidor)), AccionesDetalle(alRevisar = { cambiar = true }))

        compose.onNodeWithText("Ataque real · sincronizada 11:16").assertIsDisplayed()
        compose.onNodeWithText("Avisé al proveedor.").assertIsDisplayed()
        compose.onNodeWithText("Historial").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enviada al servidor").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Revisada: ataque real · Escalado al proveedor").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Celular Martín · sin conexión, quedó pendiente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Detectada por el modelo de IA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cambiar revisión").performScrollTo().performClick()
        assertTrue(cambiar)
    }

    @Test
    fun `recien revisada muestra la revision abajo, como enviada, y el aviso con Deshacer`() {
        var deshecha = false
        dibujar(
            contenido(Fixtures.alerta("a-1", revision = revisionDelServidor), recienRevisada = true),
            AccionesDetalle(alDeshacer = { deshecha = true }),
            aviso = AvisoDetalle.RevisionGuardada(conConexion = true),
        )

        compose.onNodeWithText("“Avisé al proveedor.”").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Celular Martín · hoy 11:10 · enviada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Revisión guardada y enviada al servidor.").assertIsDisplayed()
        compose.onNodeWithText("Deshacer").performClick()
        assertTrue(deshecha)
    }

    @Test
    fun `pendiente sin conexion lo dice en el banner, la etiqueta y la tarjeta`() {
        val local = Fixtures.revision(fecha = Fixtures.AHORA.minus(Duration.ofMinutes(5)))
        dibujar(
            contenido(
                Fixtures.alerta("a-1", revision = local, envio = EstadoEnvio.PENDIENTE),
                accion = Fixtures.accion(alertaId = "a-1", revision = local),
                sinConexion = true,
            ),
        )

        compose.onNodeWithText("Sin conexión · tu revisión se enviará al reconectar").assertIsDisplayed()
        compose.onNodeWithText("PENDIENTE DE SINCRONIZAR").assertIsDisplayed()
        compose.onNodeWithText("11:55 · Celular Martín · pendiente de envío").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cambiar revisión").assertDoesNotExist()
    }

    @Test
    fun `revisada desde otro telefono muestra la tarjeta azul y deja elegir`() {
        var aplicada = false
        var mantenida = false
        val ajena = Fixtures.revision(resultado = ResultadoRevision.FALSO_POSITIVO, motivo = Motivo.TAREA_PROGRAMADA, origen = OrigenRevision.SERVIDOR)
            .copy(autor = "Celular Lucía", revisadaEn = Fixtures.AHORA.minus(Duration.ofMinutes(2)))
        val alerta = Fixtures.alerta("a-1", revision = Fixtures.revision(), envio = EstadoEnvio.CONFLICTO)
        val accion = Fixtures.accion(alertaId = "a-1").copy(estado = EstadoEnvio.CONFLICTO, revisionAjena = ajena)
        dibujar(contenido(alerta, accion), AccionesDetalle(alAplicarLaMia = { aplicada = true }, alQuedarmeConLaOtra = { mantenida = true }))

        compose.onNodeWithText("Revisada desde otro teléfono").assertIsDisplayed()
        // La etiqueta muestra lo que quedó en el servidor: la revisión de Lucía.
        compose.onNodeWithText("FALSO POSITIVO").assertIsDisplayed()
        compose.onNodeWithText(
            "Celular Lucía la revisó como falso positivo a las 11:58 con el motivo “Tarea programada”, después de tu última sincronización.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Tu revisión (Escalado al proveedor, 12:00) todavía no se aplicó. ¿Querés aplicarla igual?").assertIsDisplayed()
        compose.onNodeWithText("Aplicar la mía").performScrollTo().performClick()
        // "Celular Lucía" no entra en medio botón: queda "Mantener la otra".
        compose.onNodeWithText("Mantener la otra").performScrollTo().performClick()
        assertTrue(aplicada && mantenida)
    }

    @Test
    fun `una revision que no se pudo enviar ofrece Reintentar`() {
        var reintentada = false
        val alerta = Fixtures.alerta("a-1", revision = Fixtures.revision(), envio = EstadoEnvio.ERROR)
        val accion = Fixtures.accion(alertaId = "a-1").copy(estado = EstadoEnvio.ERROR, intentos = 5, ultimoError = "Error del servidor")
        dibujar(contenido(alerta, accion), AccionesDetalle(alReintentarEnvio = { reintentada = true }))

        compose.onNodeWithText("No se pudo enviar la revisión").assertExists()
        compose.onNodeWithText("Intentos: 5. Último error: Error del servidor.").assertExists()
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
        assertTrue(reintentada)
    }

    @Test
    fun `sin la alerta en el telefono explica cuando se descarga y deja reintentar o volver`() {
        var reintentos = 0
        var vuelta = false
        dibujar(
            DetalleUiState.NoDisponible(sinConexion = true, error = ErrorZonda.SinConexion()),
            AccionesDetalle(alReintentarDescarga = { reintentos++ }, alVolver = { vuelta = true }),
        )

        compose.onNodeWithText("Sin conexión con el servidor · el detalle todavía no está en el teléfono").assertIsDisplayed()
        compose.onNodeWithText("PENDIENTE DE DESCARGA").assertIsDisplayed()
        compose.onNodeWithText("El detalle se descarga cuando vuelvas a la red de la empresa").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compartir").assertDoesNotExist()
        compose.onNodeWithText("Reintentar ahora").performClick()
        compose.onNodeWithText("Volver a la lista").performClick()
        assertEquals(1, reintentos)
        assertTrue(vuelta)
    }

    @Test
    fun `sin la alerta en el telefono muestra lo que trajo el aviso de Telegram`() {
        val aviso = AvisoRecibido(
            severidad = Severidad.ALTA,
            servicio = "ssh",
            deteccion = Instant.parse("2026-10-12T05:09:12Z"),
            enviadoEn = Instant.parse("2026-10-12T05:10:03Z"),
        )
        dibujar(DetalleUiState.NoDisponible(sinConexion = true, error = ErrorZonda.SinConexion(), aviso = aviso))

        compose.onNodeWithText("ALTA").assertIsDisplayed()
        compose.onNodeWithText("PENDIENTE DE DESCARGA").assertIsDisplayed()
        compose.onAllNodesWithText("SSH").assertCountEquals(2)
        compose.onNodeWithText("Aviso de Telegram de las 05:10").assertIsDisplayed()
        compose.onNodeWithText("Lo que trajo el aviso").assertIsDisplayed()
        compose.onNodeWithText("Alta").assertIsDisplayed()
        compose.onNodeWithText("Hora de detección").assertIsDisplayed()
        compose.onNodeWithText("05:09").assertIsDisplayed()
        compose.onNodeWithText("Reintentar ahora").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el historial muestra cuando salio el aviso de Telegram`() {
        val alerta = Fixtures.alerta("a-1", revision = revisionDelServidor)
        dibujar(contenido(alerta.copy(avisoTelegram = alerta.inicio.plus(Duration.ofMinutes(1)))))

        compose.onNodeWithText("Aviso enviado por Telegram").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("10:51").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el paso 1 pregunta si fue un ataque real o un falso positivo`() {
        var elegido: ResultadoRevision? = null
        compose.setContent {
            ZondaTheme {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContenidoHojaRevision(FormularioRevision(), Fixtures.alerta(), AccionesDetalle(alElegirResultado = { elegido = it }))
                }
            }
        }

        compose.onNodeWithText("¿Qué resultó ser esta alerta?").assertIsDisplayed()
        compose.onNodeWithText("Paso 1 de 2").assertIsDisplayed()
        compose.onNodeWithText("Era tráfico legítimo: un backup, una tarea programada, un equipo nuevo.").assertIsDisplayed()
        compose.onNodeWithText("Falso positivo").performClick()
        assertEquals(ResultadoRevision.FALSO_POSITIVO, elegido)
    }

    @Test
    fun `el paso 2 tiene los motivos del Figma, propone su comentario y solo deja confirmar con todo`() {
        var motivoElegido: Pair<Motivo, String>? = null
        var confirmada = false
        var formulario by mutableStateOf(FormularioRevision(paso = PasoRevision.MOTIVO, resultado = ResultadoRevision.FALSO_POSITIVO))
        compose.setContent {
            ZondaTheme {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContenidoHojaRevision(
                        formulario,
                        Fixtures.alerta(),
                        AccionesDetalle(alElegirMotivo = { motivo, comentario -> motivoElegido = motivo to comentario }, alConfirmarRevision = { confirmada = true }),
                    )
                }
            }
        }

        compose.onNodeWithText("Falso positivo: ¿por qué?").assertIsDisplayed()
        listOf("Tarea programada", "Backup", "Equipo nuevo", "Usuario autorizado", "Otro").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Contá qué hiciste o por qué. Queda en el historial de la alerta.").assertIsDisplayed()
        compose.onNodeWithText("Confirmar revisión").assertIsNotEnabled()
        compose.onNodeWithText("Backup").performClick()
        // El comentario del Figma, con los equipos de esta alerta.
        assertEquals(Motivo.BACKUP to "Es el backup semanal de pc-09 hacia srv-web.", motivoElegido)

        formulario = formulario.copy(motivo = Motivo.BACKUP, comentario = "Backup semanal")
        compose.onNodeWithText("Confirmar revisión").assertIsEnabled().performClick()
        assertTrue(confirmada)
    }
}
