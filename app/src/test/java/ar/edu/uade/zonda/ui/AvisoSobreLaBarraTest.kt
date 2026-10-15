package ar.edu.uade.zonda.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ar.edu.uade.zonda.domain.Fixtures
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ContadoresSinRevisar
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.ui.ajustes.AjustesUiState
import ar.edu.uade.zonda.ui.ajustes.AvisoAjustes
import ar.edu.uade.zonda.ui.ajustes.RedConEstado
import ar.edu.uade.zonda.ui.ajustes.RedesContenido
import ar.edu.uade.zonda.ui.detalle.AccionesDetalle
import ar.edu.uade.zonda.ui.detalle.AvisoDetalle
import ar.edu.uade.zonda.ui.detalle.DetalleContenido
import ar.edu.uade.zonda.ui.detalle.DetalleUiState
import ar.edu.uade.zonda.ui.resumen.AvisoResumen
import ar.edu.uade.zonda.ui.resumen.ContenidoResumen
import ar.edu.uade.zonda.ui.resumen.ResumenContenido
import ar.edu.uade.zonda.ui.resumen.ResumenUiState
import ar.edu.uade.zonda.ui.telegram.AccionesTelegram
import ar.edu.uade.zonda.ui.telegram.AvisoTelegram
import ar.edu.uade.zonda.ui.telegram.TelegramContenido
import ar.edu.uade.zonda.ui.telegram.TelegramUiState
import ar.edu.uade.zonda.ui.theme.ZondaTheme
import java.time.ZoneId
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Las pantallas que muestran el aviso inferior (Figma 66-3808) sin la barra de pestañas, en un teléfono
 * con la navegación de tres botones de Android. El aviso y su acción tienen que quedar enteros arriba de
 * esa barra: si no, el toque de "Deshacer" le llega a la barra y no a Zonda (cambio 22, hallazgo 12 del
 * ensayo en el moto). Estado, Alertas y Ajustes no entran: su aviso va arriba de la barra de pestañas,
 * que ya deja el lugar de la de Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvisoSobreLaBarraTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc = ZoneId.of("UTC")

    /** Lo que ocupa la barra de tres botones: 48 dp, los últimos 120 px de la pantalla del moto. */
    private val barraDeTresBotones = 48.dp

    /**
     * Dibuja la pantalla y le pasa los márgenes del sistema con la barra de tres botones abajo, como hace
     * Android con la app de borde a borde. Robolectric no dibuja las barras del sistema.
     */
    private fun dibujarConLaBarra(pantalla: @Composable () -> Unit) {
        lateinit var vista: View
        compose.setContent {
            vista = LocalView.current
            ZondaTheme { pantalla() }
        }
        compose.runOnIdle {
            val alto = (barraDeTresBotones.value * vista.resources.displayMetrics.density).roundToInt()
            val margenes = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, alto))
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(vista, margenes)
        }
    }

    /** El texto (y, si es una acción, todo lo que se puede tocar) termina arriba de la barra. */
    private fun quedaArribaDeLaBarra(texto: String) {
        val pantalla = compose.onRoot().getBoundsInRoot()
        val nodo = compose.onNodeWithText(texto).getBoundsInRoot()
        val barra = pantalla.bottom - barraDeTresBotones
        assertTrue("\"$texto\" termina en ${nodo.bottom} y la barra empieza en $barra", nodo.bottom <= barra)
    }

    private fun detalle(alerta: Alerta, recienRevisada: Boolean = false) = DetalleUiState.Contenido(
        alerta,
        accion = null,
        sinConexion = false,
        datosDe = Fixtures.AHORA,
        recienRevisada = recienRevisada,
        nombreDelTelefono = "Celular Martín",
        nombreDeLaRed = "Oficina central",
    )

    @Test
    fun `el aviso con Deshacer del detalle queda arriba de la barra`() {
        dibujarConLaBarra {
            DetalleContenido(
                estado = detalle(Fixtures.alerta("a-1", revision = Fixtures.revision()), recienRevisada = true),
                acciones = AccionesDetalle(),
                aviso = AvisoDetalle.RevisionGuardada(conConexion = true),
                zona = utc,
                ahora = Fixtures.AHORA,
            )
        }

        quedaArribaDeLaBarra("Revisión guardada y enviada al servidor.")
        quedaArribaDeLaBarra("Deshacer")
    }

    @Test
    fun `el final del detalle se puede subir arriba de la barra`() {
        dibujarConLaBarra {
            DetalleContenido(estado = detalle(Fixtures.alerta("a-1")), acciones = AccionesDetalle(), zona = utc, ahora = Fixtures.AHORA)
        }

        compose.onNodeWithText("Revisar").performScrollTo()
        quedaArribaDeLaBarra("Revisar")
    }

    @Test
    fun `el aviso de Telegram queda arriba de la barra`() {
        val red = Fixtures.red("red-1").copy(nombre = "Oficina central", telegramBot = "zonda_alertas_bot", telegramVinculado = true)
        dibujarConLaBarra {
            TelegramContenido(
                estado = TelegramUiState(red = red, nombreDelTelefono = "Celular Martín", aviso = AvisoTelegram.PruebaEnviada),
                acciones = AccionesTelegram(),
                desdeAjustes = true,
            )
        }

        quedaArribaDeLaBarra("Aviso de prueba enviado. Fijate en el chat de @zonda_alertas_bot.")
        quedaArribaDeLaBarra("OK")
    }

    @Test
    fun `el aviso de Redes queda arriba de la barra`() {
        val red = Fixtures.red("red-1").copy(nombre = "Oficina central")
        val estado = AjustesUiState(
            redes = listOf(RedConEstado(red, ContadoresSinRevisar(altas = 3, medias = 0, bajas = 0), alertasGuardadas = 213)),
            redActiva = red,
            nombreDelTelefono = "Celular Martín",
            aviso = AvisoAjustes.RedActivada("Oficina central"),
        )
        dibujarConLaBarra {
            RedesContenido(
                estado = estado,
                alActivar = {},
                alQuitar = {},
                alConectarDeNuevo = {},
                alAgregarRed = {},
                alQuedarSinRedes = {},
                alAvisoMostrado = {},
                alVolver = {},
                zona = utc,
            )
        }

        quedaArribaDeLaBarra("Red activa: Oficina central. Estado y Alertas ya muestran sus datos.")
        quedaArribaDeLaBarra("OK")
    }

    @Test
    fun `el aviso del Resumen queda arriba de la barra`() {
        // Solo hay avisos después de compartir, y para compartir hace falta el resumen: con el botón fijo abajo.
        val resumen = ResumenActividad.calcular(listOf(Fixtures.alerta("a-1")), PeriodoResumen.SIETE_DIAS, Fixtures.AHORA, utc, null)
        dibujarConLaBarra {
            ResumenContenido(
                estado = ResumenUiState(contenido = ContenidoResumen.Datos(resumen), red = "Oficina central", datosDe = Fixtures.AHORA, aviso = AvisoResumen.Copiado),
                alVolver = {},
                alElegirPeriodo = {},
                alTocarCifra = {},
                alQuitarFiltro = {},
                alCompartir = {},
                zona = utc,
            )
        }

        quedaArribaDeLaBarra("Imagen copiada. Pegala donde quieras.")
    }
}
