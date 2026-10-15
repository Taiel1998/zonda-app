package ar.edu.uade.zonda.ui.detalle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.AvisoRecibido
import ar.edu.uade.zonda.ui.navigation.EnlacesInternos
import java.net.URLEncoder
import java.time.temporal.ChronoUnit

/**
 * Ruta del detalle de una alerta. Se abre desde Estado o Alertas (con [crear]) y desde el enlace
 * del aviso de Telegram (App Link con el dominio público del equipo, decisión 9). El enlace lleva el
 * id, la red y lo mismo que dice el aviso (severidad, servicio y hora): si la alerta todavía no está
 * en el teléfono, el detalle lo muestra (RF03, criterio 3). Los datos de la alerta se piden a la API
 * o se leen de la base local, nunca del enlace (informe, sección 13.4).
 */
object RutaDetalle {
    const val ARG_ID = "alertaId"
    const val ARG_SEVERIDAD = "severidad"
    const val ARG_SERVICIO = "servicio"
    const val ARG_DETECCION = "deteccion"
    const val ARG_AVISO = "aviso"
    const val PATRON = "detalle/{$ARG_ID}"
    const val DOMINIO_AVISO = "https://taiel1998.github.io/a/"
    const val ENLACE_AVISO = DOMINIO_AVISO + "{$ARG_ID}?" +
        "${EnlacesInternos.ARG_RED}={${EnlacesInternos.ARG_RED}}&$ARG_SEVERIDAD={$ARG_SEVERIDAD}&" +
        "$ARG_SERVICIO={$ARG_SERVICIO}&$ARG_DETECCION={$ARG_DETECCION}&$ARG_AVISO={$ARG_AVISO}"

    fun crear(alertaId: String): String = "detalle/$alertaId"

    /**
     * El enlace del aviso de una alerta, para compartirla desde el detalle: el mismo que manda el bot,
     * sin la hora del aviso. Nunca lleva direcciones IP ni nombres de equipos (RNF02).
     */
    fun enlaceDelAviso(alerta: Alerta): String {
        val parametros = listOf(
            EnlacesInternos.ARG_RED to alerta.redId,
            ARG_SEVERIDAD to alerta.severidad.name.lowercase(),
            ARG_SERVICIO to alerta.servicio.lowercase(),
            ARG_DETECCION to alerta.inicio.truncatedTo(ChronoUnit.SECONDS).toString(),
        )
        val consulta = parametros.joinToString("&") { (clave, valor) -> "$clave=${URLEncoder.encode(valor, "UTF-8")}" }
        return "$DOMINIO_AVISO${alerta.id}?$consulta"
    }
}

fun NavGraphBuilder.detalleAlerta(
    alVolver: () -> Unit,
    alPedirRed: (redId: String) -> Unit = {},
    /** La pantalla. Las pruebas de navegación ponen una simple, que no necesita la base de datos. */
    pantalla: @Composable (alertaId: String, avisoRecibido: AvisoRecibido?, alVolver: () -> Unit) -> Unit = { alertaId, avisoRecibido, volver ->
        DetalleScreen(alertaId = alertaId, avisoRecibido = avisoRecibido, alVolver = volver)
    },
) {
    composable(
        route = RutaDetalle.PATRON,
        // El id siempre; lo demás viene solo en los enlaces y, si falta, queda en null.
        arguments = listOf(navArgument(RutaDetalle.ARG_ID) { type = NavType.StringType }) +
            ARGUMENTOS_DE_LOS_ENLACES.map { nombre ->
                navArgument(nombre) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            },
        deepLinks = listOf(
            navDeepLink { uriPattern = RutaDetalle.ENLACE_AVISO },
            navDeepLink { uriPattern = EnlacesInternos.PATRON_DETALLE },
        ),
    ) { entrada ->
        val argumentos = entrada.arguments
        val alertaId = argumentos?.getString(RutaDetalle.ARG_ID).orEmpty()
        // Lo que trajo el aviso; cada valor se valida y uno que no cierra se descarta (AvisoRecibido).
        val avisoRecibido = AvisoRecibido.desdeEnlace(
            severidad = argumentos?.getString(RutaDetalle.ARG_SEVERIDAD),
            servicio = argumentos?.getString(RutaDetalle.ARG_SERVICIO),
            deteccion = argumentos?.getString(RutaDetalle.ARG_DETECCION),
            aviso = argumentos?.getString(RutaDetalle.ARG_AVISO),
        )
        // La notificación o el aviso de otra red trae su id: esa red pasa a ser la activa (una sola vez por entrada).
        LaunchedEffect(entrada) {
            val red = entrada.arguments?.getString(EnlacesInternos.ARG_RED)
            if (red != null && entrada.savedStateHandle.get<Boolean>(RED_PEDIDA) != true) {
                entrada.savedStateHandle[RED_PEDIDA] = true
                alPedirRed(red)
            }
        }
        pantalla(alertaId, avisoRecibido, alVolver)
    }
}

private const val RED_PEDIDA = "red_pedida"

private val ARGUMENTOS_DE_LOS_ENLACES = listOf(
    EnlacesInternos.ARG_RED,
    RutaDetalle.ARG_SEVERIDAD,
    RutaDetalle.ARG_SERVICIO,
    RutaDetalle.ARG_DETECCION,
    RutaDetalle.ARG_AVISO,
)
